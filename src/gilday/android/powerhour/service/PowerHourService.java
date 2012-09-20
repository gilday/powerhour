/**
 * 
 */
package gilday.android.powerhour.service;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import gilday.android.powerhour.MusicUpdateBroadcastReceiver;
import gilday.android.powerhour.NotificationSoundClipPlayer;
import gilday.android.powerhour.OngoingNotificationUpdater;
import gilday.android.powerhour.ProgressUpdateBroadcastReceiver;
import gilday.android.powerhour.data.PreferenceRepository;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author John Gilday
 *
 */
public class PowerHourService extends Service implements ISongPreparedListener, IAudioFocusLostListener {
	public static final int NOT_STARTED = 0;
	public static final int PLAYING = 1;
	public static final int PAUSED = 2;
	static final int INTERVAL = 1000;
	// public static final int INTERVAL = 200;
	public static final String MUSIC_UPDATE_BROADCAST = "com.johnathangilday.powerhour.musicupdatebroadcast";
	public static final String PROGRESS_UPDATE_BROADCAST = "com.johnathangilday.powerhour.progressupdatebroadcast";
	public static final String PROGRESS_PAUSE_RESUME_BROADCAST = "com.johnathangilday.powerhour.progresspauseresumebroadcast";
	public static final String SONGID = "songid";
	public static final String PROGRESS = "progress";
	public static final String IS_PAUSED = "isPaused";
	
	public static final String ACTION_PLAY_PAUSE = "com.johnathangilday.powerhour.action.playpause";
	public static final String ACTION_SKIP = "com.johnathangilday.powerhour.action.skip";
	
	private Timer myTimer;
	private int seconds = -1;
	private int playingState = NOT_STARTED;
	Handler mHandler = new Handler();
	private PreferenceRepository powerHourPrefs;
	private NowPlayingPlaylistManager playlistManager;
	private SongPlayer songPlayer;
    private MusicUpdateBroadcastReceiver musicUpdateBroadcastReceiver;
    private ProgressUpdateBroadcastReceiver progressUpdateBroadcastReceiver;
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		powerHourPrefs = new PreferenceRepository(this);
		playlistManager = new NowPlayingPlaylistManager(this);
		
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(new PhoneStateListener(){
			public void onCallStateChanged(int state, String incomingNumber){
				switch(state){
				case TelephonyManager.CALL_STATE_RINGING:
					if(playingState == PLAYING)
						playPause();
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
        lbm.registerReceiver(progressUpdateBroadcastReceiver, new IntentFilter(PowerHourService.PROGRESS_PAUSE_RESUME_BROADCAST));
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

	    	// Initialize new SongPlayer because about to start playing music
	    	songPlayer = new SongPlayer(this, this);
	    	
	    	myTimer.schedule(new SecondTimer(), 0, INTERVAL);
		}
		
		// PowerHourService handles intents for ACTION_PLAY_PAUSE and ACTION_SKIP 
		// so external actors (like BroadcastReceivers) may send these actions without binding
		if(intent != null) {
			String action = intent.getAction();
			if(ACTION_PLAY_PAUSE.equals(action)) playPause();
			else if(ACTION_SKIP.equals(action)) skip();
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
			songPlayer.dispose();
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
		
		try{
			songPlayer.prepareNextSong(songId, this);
		} catch (IllegalStateException e) {
			toastError("Nasty error has occured with the Android VM. Please wait for the next song");
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			toastError("Song corrupted! Error retrieving song with id: " + songId);
			e.printStackTrace();
			return -1;
		}
		
		// Send update
		Intent intent = new Intent();
		intent.putExtra(PowerHourService.SONGID, songId);
		intent.setAction(PowerHourService.MUSIC_UPDATE_BROADCAST);
		LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
		
		return songId;
	}
	
	@Override
	public void onSongPrepared(SongPlayer audioPlayerManager) {
		// If the power hour is playing..
		if(playingState == PLAYING) {
			// .. press play on the audio manager
			audioPlayerManager.play();
		}
	}
	
	@Override
	public void onAudioFocusLost() {
		// The app has lost focus indefinitely to another music player
		// Pause the Power Hour until user manually plays
		if(playingState == PowerHourService.PLAYING)
			playPause();
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
	
	void playPause(){
    	if(playingState == PowerHourService.PLAYING) {
    		songPlayer.pause();
    		myTimer.cancel();
    		playingState = PowerHourService.PAUSED;
    	} else {
    		songPlayer.play();
    		myTimer = new Timer();
    		myTimer.schedule(new SecondTimer(), 1000, INTERVAL);
    		playingState = PowerHourService.PLAYING;
    	}
    	
    	// Send Progress Pause / Resume Broadcast
    	Intent broadcast = new Intent();
    	broadcast.putExtra(PowerHourService.IS_PAUSED, (playingState == PowerHourService.PAUSED));
    	broadcast.setAction(PROGRESS_PAUSE_RESUME_BROADCAST);
    	LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcast);
	}
	
	int skip() {
    	if(!playlistManager.isPlayingLastSong()){
    		return loadNextSong();
    	} else {
    		return -1;
    	}
	}
	
	private final IPowerHourService mBinder = new PowerHourServiceInterface();
    
    private class PowerHourServiceInterface extends Binder implements IPowerHourService
    {
	    public void playPause() { 
	    	PowerHourService.this.playPause();
	    }
	    
	    public int skip() {
	    	return PowerHourService.this.skip();
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
