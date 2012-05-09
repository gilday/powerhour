/**
 * 
 */
package gilday.android.powerhour;

import gilday.android.powerhour.data.PlaylistRepository;
import gilday.android.powerhour.data.PowerHour;
import gilday.android.powerhour.data.PreferenceRepository;
import gilday.android.powerhour.model.PlaylistItem;
import gilday.android.powerhour.view.NowPlaying;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * @author John Gilday
 *
 */
public class PowerHourService extends Service {
	public static final int NOT_STARTED = 0;
	public static final int PLAYING = 1;
	public static final int PAUSED = 2;
	public static final int INTERVAL = 1000;
	// public static final int INTERVAL = 200;
	public static final int COMPLETE_SENTINEL = -9999;
	public static final int PAUSE_ACTION_ID = 2;
	private static final String TAG = "PH_Service";
	
	private MediaPlayer mplayer;
	private MediaPlayer soundClipPlayer;
	private IPowerHourClient myClient;
	private Timer myTimer;
	private int seconds = -1;
	private Random rand;
	private long lastMinuteStart = -1;
	private int playingState = NOT_STARTED;
	Object mplayerLock = new Object();
	Object splayerLock = new Object();
	Handler mHandler = new Handler();
	private boolean affectingClientVar = false;
	private Object affectingClientVarLock = new Object();
	private PreferenceRepository powerHourPrefs;
	
	// Notification objects
	Notification notification;
	NotificationManager mNotificationManager;
	PendingIntent notificationIntent;
	static final int NOTIFICATION_ID = 1;
	
	@Override
	public void onCreate(){
		super.onCreate();
		mplayer = new MediaPlayer();
		soundClipPlayer = new MediaPlayer();
		powerHourPrefs = new PreferenceRepository(this);
		
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(new PhoneStateListener(){
			public void onCallStateChanged(int state, String incomingNumber){
				switch(state){
				case TelephonyManager.CALL_STATE_RINGING:
					if(playingState == PLAYING)
						doPause();
					break;
				case TelephonyManager.CALL_STATE_IDLE:
					if(playingState == PAUSED)
						doPause();
					break;
				}
			}
		}, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	@Override
	public void onStart(Intent intent, int startId){
		//Log.(TAG, "onStart here");
		// If this service hasn't been started yet, and the playlist repository is set, start the service
		// why? if it is started, don't do anything. If the playlist isn't set, can't do anything
		PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
		if(playingState == NOT_STARTED && playlistRepo.getPlaylistSize() > 0) {
			if(playlistRepo.getPlaylistSize() <= 0){
				throw new ArrayIndexOutOfBoundsException("The song list given to PowerHourService contains no songs!");
			}
			int duration = powerHourPrefs.getDuration();
			if(playlistRepo.getPlaylistSize() < (duration / 60)){
				toastError("You have added only " + playlistRepo.getPlaylistSize() + " songs. Your Power Hour is going to be a little short");
			}
			doStart();
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		//Log.(TAG, "onBind here");
		return mBinder;
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		destroyStuff();
	}
	
	void destroyStuff(){
		myTimer.cancel();
		if(playingState != PowerHourService.NOT_STARTED){
			playingState = PowerHourService.NOT_STARTED;
			synchronized(mplayerLock){
		    	mplayer.stop();
		    	mplayer.release();
		    	mplayer = null;
			}
			synchronized(splayerLock){
				soundClipPlayer.stop();
				soundClipPlayer.release();
				soundClipPlayer = null;
			}
		}
		// Clear notification
		if(mNotificationManager != null){
			mNotificationManager.cancel(R.layout.custom_notification_layout);
		}
		PlaylistRepository.getInstance().clearPlaylist();
	}

	/**
	 * Loads and plays the next song in the list. Takes care of shuffling here.
	 * @return the id (in terms of the Android MusicProvider) of the song that was just loaded
	 */
	int loadNextSong() {
		PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
		boolean shuffle = powerHourPrefs.isShuffle();
		
		// Set the current song as 'played'
		int currentSong = playlistRepo.getCurrentSong();
		if(currentSong >= 0) {
			ContentValues values = new ContentValues();
			values.put(PowerHour.NowPlaying.PLAYED, true);
			Uri updateUri = ContentUris.withAppendedId(PowerHour.NowPlaying.CONTENT_URI, currentSong);
			getContentResolver().update(updateUri, values, null, null);
		}
		
		// Do expensive work before we reset the media player so we don't "skip a beat" bahahaha
		// Advance to next song
		int songId = playlistRepo.getNextSong(shuffle);
		if(songId == -1) {
			return -1;
		}
		
		// Set the offset
		int msOffset = 0;
		double offset = powerHourPrefs.getOffset();
		if(offset != 0){
			// Get the duration of this song in ms
			long duration = MusicUtils.getDuration(getBaseContext(), songId);
			// Calculate offset
			// Is offset random
			if(offset == PowerHourPreferences.RANDOM){
				// Ensure the offset will leave at least a minute of playback
				// MusicUtils returns duration in ms so subtract 60 seconds * 1000
				double maxOffset = (double)(duration - 60000) / duration;
				offset = (rand.nextDouble() * maxOffset);
			}
			Log.v("OFFSET", "OFFSET: " + offset);
			msOffset = (int) (offset * duration);
			//msOffset = (int) (((double)(percent / 100)) * duration);
			// Is there enough song left to finish the minute?
			if(duration - msOffset < INTERVAL){
				// Nope, play the whole song. Don't account for songs 
				// that are < 60 seconds: If the user put a song < 60
				// seconds on a Power Hour they're retarded and deserve to 
				// sit through silence.
				// TODO: Meh. Maybe do something about this. I'm sure voice memos don't want to be heard
				// then again, just skip it?
				msOffset = 0;
			}
		}
		
		synchronized(mplayerLock){
			mplayer.reset();
			// Get next song's path from MusicUtils
			String path = MusicUtils.getPathForSong(songId);
			try {
				// try set and prepare next song
				mplayer.setDataSource(path);
				mplayer.prepare();
			} catch (IllegalStateException e) {
				toastError("Nasty error has occured with the Android VM. Please wait for the next song");
				e.printStackTrace();
				return -1;
			} catch (IOException e) {
				toastError("Song corrupted! Error retrieving song at: " + path);
				e.printStackTrace();
				return -1;
			}
			// Set the offset
			mplayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
				public void onSeekComplete(MediaPlayer mp) {
					double offset = powerHourPrefs.getOffset();
					Log.d(TAG, "Offset: " + offset + ".  Current pos: " + mplayer.getCurrentPosition());
					if(playingState == PowerHourService.PLAYING) {
						mplayer.start();
					}
					mplayer.setOnSeekCompleteListener(null);
				}
			});
			Log.d(TAG, "Offset: " + offset + ".  Current pos: " + mplayer.getCurrentPosition());
			mplayer.seekTo(msOffset);			
			
			// Post to status bar
			postToStatusBar(songId);
		}
		return songId;
	}
	
	void doStart() {
    	myTimer = new Timer();
    	// reassign rand to reset the random seed
    	rand = new Random();
    	lastMinuteStart = System.currentTimeMillis();

    	// Create notification in status bar if not already instantiated
    	// Instantiate the notification and notification manager if they need to be
    	if(notification == null && mNotificationManager == null){
    		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);			
			notification = new Notification();
			notification.icon = R.drawable.beerstatusbar;
			notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
			Intent nowPlayingIntent = new Intent(PowerHourService.this, NowPlaying.class);
			notificationIntent = PendingIntent.getActivity(PowerHourService.this, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			notification.contentIntent = notificationIntent;
    	}
    	
    	myTimer.schedule(new SecondTimer(), 0, INTERVAL);
	}
	
	void postToStatusBar(int songID){
		PlaylistItem songInfo = MusicUtils.getInfoPack(getApplicationContext(), songID);
		RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.custom_notification_layout);
		contentView.setTextViewText(R.id.notificationSongTitle, songInfo.song);
		contentView.setTextViewText(R.id.notificationArtist, songInfo.artist);
		contentView.setTextViewText(R.id.notificationDrink, "Drink number: " + ((seconds / 60) + 1));
		notification.contentView = contentView;
		// notification.setLatestEventInfo(getApplicationContext(), "PowerHour Pro", "Drink number: " + ((seconds / 60) + 1), notificationIntent);
		mNotificationManager.notify(R.layout.custom_notification_layout, notification);
	}
	
	private void toastError(String message) {
		// pass parameter to a final String so inner class can access it
		final String _message = message;
		mHandler.post(new Runnable() {
			public void run() {
				Toast.makeText(PowerHourService.this, _message, Toast.LENGTH_LONG).show();
			}
		});
	}
	
	void doPause(){
    	if(playingState == PowerHourService.PLAYING) {
    		myTimer.cancel();
    		mplayer.pause();
    		playingState = PowerHourService.PAUSED;
    	} else {
    		myTimer = new Timer();
    		mplayer.start();
    		playingState = PowerHourService.PLAYING;
    		myTimer.schedule(new SecondTimer(), 0, INTERVAL);
    	}
	}
	
	void doStop(){
		// Clear notification
		// Clear notification
		if(mNotificationManager != null){
			mNotificationManager.cancel(R.layout.custom_notification_layout);
		}
		stopSelf();
	}
	
	private final IPowerHourService.Stub mBinder = new IPowerHourService.Stub() {
		
		/**
		 * @return returns the elapsed time (in seconds) of the current minutes playing. 
		 *  if the Power Hour is just starting, this will be 0.
		 *  if the registration has failed, this will be a negative number
		 *  if the Power Hour has been playing and the GUI is re-binding, this is the elapsed 
		 *  second of the current minute. Use this to update any progress
		 */
		public int registerClient(IPowerHourClient client){
			if(client == null){
				// Enter a thread protected segment
				synchronized(affectingClientVarLock){
					try {
						while(affectingClientVar)
							affectingClientVarLock.wait();
						affectingClientVar = true;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					myClient = null;
					affectingClientVar = false;
					affectingClientVarLock.notify();
				}
				return 0;
			}
			if(myClient == null){
				//Log.("service", "client registered");
				myClient = client;
				if(playingState != PowerHourService.NOT_STARTED){
					return (int)((lastMinuteStart - System.currentTimeMillis()) / 1000);
				}
				return 0;
			}
			return -1;
		}
	    
	    public void stop() {
	    	doStop();
	    }
	    
	    public void pause() { 
	    	doPause();
	    }
	    
	    public void start() {
	    	doStart();
	    }

	    public int skip() {
	    	PlaylistRepository playlistRepo = PlaylistRepository.getInstance(); 
	    	if(!playlistRepo.isPlayingLastSong()){
	    		return loadNextSong();
	    	} else {
	    		return -1;
	    	}
	    }
	    
	    public int getPlayingState() {
	    	return playingState;
	    }
	    
	    public int getProgress(){
	    	return seconds;
	    }
    };

    /** Used to throw a toast from the Timer thread */
    final Handler toastHandler = new Handler();
    final Runnable runInUIThread = new Runnable() {
        public void run() {
        	Toast.makeText(PowerHourService.this, getString(R.string.completed), Toast.LENGTH_LONG).show();
        	PowerHourService.this.doStop();
        }
      };


    private class SecondTimer extends TimerTask{
    	@Override
		public void run() {
    		//Log.(TAG, "tick!");
    		seconds++;
    		playingState = PowerHourService.PLAYING;
    		
    		// If a solid minutes has gone by
    		if(seconds % 60 == 0) {
    			//int minutes = seconds / 60;
    			// If the power hour is over, or if the list is too small and there are no more songs to load,
    			// end the power hour
    			int duration = powerHourPrefs.getDuration();
    			PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
    			if(seconds >= duration || playlistRepo.isPlayingLastSong()){
    				synchronized(affectingClientVarLock){
	    				try {
	    					while(affectingClientVar)
	    						affectingClientVarLock.wait();
	    					affectingClientVar = true;
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
	    				if(myClient != null){
	    					mHandler.post(new Runnable(){
								public void run() {
									try { 
										myClient.secondCompleted(seconds, COMPLETE_SENTINEL);
									} catch (RemoteException e) {
										e.printStackTrace();
			    						Log.e(TAG, "Nasty error when calling minuteCompleted(COMPLETE_SENTINEL)  " + e.getMessage());
			    						PowerHourService.this.doStop();
									}
								}
	    					});
	    				}
	    				else{
	    					toastHandler.post(runInUIThread);
	    				}
	    				affectingClientVar = false;
	    				affectingClientVarLock.notify();
    				}
    				destroyStuff();
    				return;
    			}

    			loadNextSong();
    			// Play sound clip
    			boolean useAlert = powerHourPrefs.getUseAlert();
    			if(useAlert){
    				boolean useArnold = powerHourPrefs.getUseArnold();
    				String alertPath = powerHourPrefs.getAlertPath();
    				synchronized(splayerLock){
	    				soundClipPlayer.reset();
	    				try{
		    				if(useArnold || alertPath.equals("arnold")){
		    					FileDescriptor soundClip = getResources().openRawResourceFd(R.raw.doitnow).getFileDescriptor();
		    					soundClipPlayer.setDataSource(soundClip);
		    				} else {
		    					soundClipPlayer.setDataSource(alertPath);
		    				}
	    					soundClipPlayer.prepare();
	    					soundClipPlayer.start();
	    				} catch (IllegalStateException e) {
	    					toastError("Could not play sound clip!");
	    					Log.e(TAG, "Could not play sound clip!");
	    					e.printStackTrace();
	    				} catch (IOException e) {
	    					String errorMessage;
	    					if(useArnold){
	    						errorMessage = "The default alert sound clip is missing? strange...";
	    					} else {
	    						errorMessage = "The alert sound clip is invalid. Change custom alert sound in preferences";
	    					}
	    					toastError(errorMessage);
	    					Log.e(TAG, errorMessage);
	    					e.printStackTrace();
	    				}
    				}
    			}
    		}
    		
    		// if we have a client registered, tell it a second has gone by
    		synchronized(affectingClientVarLock){
	    		try {
	    			while(affectingClientVar)
	    				affectingClientVarLock.wait();
	    			affectingClientVar = true;
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
	    		if(myClient != null){
					final int songId = PlaylistRepository.getInstance().getCurrentSong();
					mHandler.post(new Runnable(){
	
						public void run() {
							if(myClient != null){
								try {
									myClient.secondCompleted(seconds, songId);
								} catch (RemoteException e) {
									// do nothing
								}
							}
						}
						
					});
	    		}
	    		affectingClientVar = false;
	    		affectingClientVarLock.notify();
    		}
    		//Log.(TAG, "end tick!");
		}
    }
}
