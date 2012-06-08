/**
 * 
 */
package gilday.android.powerhour;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import gilday.android.powerhour.data.PreferenceRepository;

import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

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
	public static final String MUSIC_UPDATE_BROADCAST = "com.johnathangilday.powerhour.musicupdatebroadcast";
	public static final String PROGRESS_UPDATE_BROADCAST = "com.johnathangilday.powerhour.progressupdatebroadcast";
	public static final String SONGID = "songid";
	public static final String PROGRESS = "progress";
	
	private static final String TAG = "PH_Service";
	
	private MediaPlayer mplayer;
	private Timer myTimer;
	private int seconds = -1;
	private Random rand;
	private int playingState = NOT_STARTED;
	final Object mplayerLock = new Object();
	Handler mHandler = new Handler();
	private PreferenceRepository powerHourPrefs;
	private NowPlayingPlaylistManager playlistManager;
    private MusicUpdateBroadcastReceiver musicUpdateBroadcastReceiver;
    private ProgressUpdateBroadcastReceiver progressUpdateBroadcastReceiver;
	
	@Override
	public void onCreate(){
		super.onCreate();
		mplayer = new MediaPlayer();
		powerHourPrefs = new PreferenceRepository(this);
		playlistManager = new NowPlayingPlaylistManager(this);
		
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

        // Register the NotificationSoundClipPlayer so it will play the
        // alert clip when the service broadcasts a progress advance
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getApplicationContext());
        NotificationSoundClipPlayer drinkNotificationPlayer = new NotificationSoundClipPlayer(getApplicationContext());
        OngoingNotificationUpdater drinkNotificationUpdater = new OngoingNotificationUpdater(getApplicationContext());
        progressUpdateBroadcastReceiver = new ProgressUpdateBroadcastReceiver(drinkNotificationPlayer, drinkNotificationUpdater);
        musicUpdateBroadcastReceiver = new MusicUpdateBroadcastReceiver(drinkNotificationUpdater);
        lbm.registerReceiver(progressUpdateBroadcastReceiver, new IntentFilter(PowerHourService.PROGRESS_UPDATE_BROADCAST));
		lbm.registerReceiver(musicUpdateBroadcastReceiver, new IntentFilter(PowerHourService.MUSIC_UPDATE_BROADCAST));
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if(playingState == NOT_STARTED && playlistManager.getPlaylistSize() > 0) {
			if(playlistManager.getPlaylistSize() <= 0){
				throw new ArrayIndexOutOfBoundsException("The song list given to PowerHourService contains no songs!");
			}
			// Start timer
	    	myTimer = new Timer();
	    	// reassign rand to reset the random seed
	    	rand = new Random();

	    	myTimer.schedule(new SecondTimer(), 0, INTERVAL);
		}
		
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		//Log.(TAG, "onBind here");
		return (IBinder) mBinder;
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		myTimer.cancel();
		if(playingState != PowerHourService.NOT_STARTED){
			playingState = PowerHourService.NOT_STARTED;
			synchronized(mplayerLock){
				mplayer.stop();
		    	mplayer.release();
		    	mplayer = null;
			}
		}
        // Clear progressUpdateReceiver
        progressUpdateBroadcastReceiver.unregisterUpdateListener();
        musicUpdateBroadcastReceiver.unRegisterUpdateListener();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getApplicationContext());
        lbm.unregisterReceiver(progressUpdateBroadcastReceiver);
        lbm.unregisterReceiver(musicUpdateBroadcastReceiver);

        // Clear playlist
		playlistManager.clearPlaylist();
	}

	/**
	 * Loads and plays the next song in the list. Takes care of shuffling here.
	 * @return the id (in terms of the Android MusicProvider) of the song that was just loaded
	 */
	int loadNextSong() {
		// Do expensive work before we reset the media player so we don't "skip a beat" bahahaha
		// Advance to next song
		int songId = playlistManager.advancePlaylist();
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
			
    		// Send update
    		Intent intent = new Intent();
    		intent.putExtra(PowerHourService.SONGID, songId);
    		intent.setAction(PowerHourService.MUSIC_UPDATE_BROADCAST);
    		LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
		}
		return songId;
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
    		mplayer.pause();
    		myTimer.cancel();
    		playingState = PowerHourService.PAUSED;
    	} else {
    		mplayer.start();
    		myTimer = new Timer();
    		myTimer.schedule(new SecondTimer(), 1000, INTERVAL);
    		playingState = PowerHourService.PLAYING;
    	}
	}
	
	private final IPowerHourService mBinder = new PowerHourServiceInterface();
    
    private class PowerHourServiceInterface extends Binder implements IPowerHourService
    {
	    public void pause() { 
	    	doPause();
	    }
	    
	    public int skip() {
	    	if(!playlistManager.isPlayingLastSong()){
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

		public int getCurrentSong() {
			return playlistManager.getCurrentSong();
		}
    }

    private class SecondTimer extends TimerTask{
    	@Override
		public void run() {
    		//Log.(TAG, "tick!");
    		seconds++;
    		playingState = PowerHourService.PLAYING;
    		if(seconds % 60 == 0) {
    			int minutes = seconds / 60;
				// If the power hour is over, or if the list is too small and there are no more songs to load,
				// end the power hour
				int duration = powerHourPrefs.getDuration();
				if(minutes >= duration || playlistManager.isPlayingLastSong()){
					// Tear down
					stopSelf();
					return;
				}
				
				loadNextSong();
				
				// Send progress update
				Intent intent = new Intent();
				intent.putExtra(PowerHourService.PROGRESS, minutes);
				intent.setAction(PowerHourService.PROGRESS_UPDATE_BROADCAST);
				LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    		}
    	}
    }
}
