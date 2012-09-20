package gilday.android.powerhour.service;

import java.io.IOException;
import java.util.Random;

import gilday.android.powerhour.IDisposable;
import gilday.android.powerhour.MusicUtils;
import gilday.android.powerhour.PowerHourPreferences;
import gilday.android.powerhour.data.PreferenceRepository;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.util.Log;

/**
 * 
 * @author Johnathan Gilday
 *
 */
class SongPlayer implements IDisposable {

	private static final String TAG = "SongPlayer";
	final private Object mplayerLock = new Object();
	private MediaPlayer mplayer;
	private Context context;
	private PreferenceRepository powerHourPrefs;
	private AudioFocusStateManager audioFocusStateManager;
	
	/**
	 * Construct the AudioPlayerManager just before it needs to play songs since it will 
	 * request audio focus from the Android system
	 * @param powerHourService Needed to determine if the power hour is set to "play"
	 * @param context
	 */
	public SongPlayer(Context context, IAudioFocusLostListener audioFocusLostListener) {
		mplayer = new MediaPlayer();
		this.context = context;
		powerHourPrefs = new PreferenceRepository(context);
		audioFocusStateManager = new AudioFocusStateManager(context, audioFocusLostListener);
	}
	
	/**
	 * Will reload the MediaPlayer with a new song. Will seek the MediaPlayer to the correct position 
	 * based on settings. If PowerHourService is playing, will play the song after seek operation complete
	 * @param songId Song to prepare
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public void prepareNextSong(int songId, final ISongPreparedListener callback) throws IllegalArgumentException, IllegalStateException, IOException {
		
		int msOffset = calculateMillisecondOffset(songId);
		
		synchronized(mplayerLock){
			mplayer.reset();
			// Get next song's path from MusicUtils
			String path = MusicUtils.getPathForSong(songId);
			// try set and prepare next song
			mplayer.setDataSource(path);
			mplayer.prepare();
			// Set the offset
			mplayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
				public void onSeekComplete(MediaPlayer mp) {
					double offset = powerHourPrefs.getOffset();
					Log.d(TAG, "Offset: " + offset + ".  Current pos: " + mplayer.getCurrentPosition());
					callback.onSongPrepared(SongPlayer.this);
					mplayer.setOnSeekCompleteListener(null);
				}
			});
			mplayer.seekTo(msOffset);			
		}
	}
		
	public void pause() {
		if(mplayer.isPlaying())
			mplayer.pause();
	}
	
	public void play() {
		audioFocusStateManager.tryGetAudioFocus();
		if(!audioFocusStateManager.hasFocus()) {
			if(mplayer.isPlaying())
				mplayer.pause();
			return;
		}
		
		if(!mplayer.isPlaying()) mplayer.start();
	}

	/**
	 * Release MediaPlayer resources and give up audio focus
	 */
	@Override
	public void dispose() {
		synchronized(mplayerLock){
			mplayer.stop();
	    	mplayer.release();
	    	mplayer = null;
		}
		audioFocusStateManager.tryAbandonAudioFocus();
	}

	/**
	 * Based on power hour settings, will determine the number of milliseconds to skip 
	 * before starting the media playback.
	 * @param songId the integer ID of the song. Needed to figure out how long the song is
	 * @return Media playback starting position in ms
	 */
	private int calculateMillisecondOffset(int songId) {
		int msOffset = 0;
		double offset = powerHourPrefs.getOffset();
		if(offset != 0){
			// Get the duration of this song in ms
			long duration = MusicUtils.getDuration(context, songId);
			// Calculate offset
			// Is offset random
			if(offset == PowerHourPreferences.RANDOM){
				// Ensure the offset will leave at least a minute of playback
				// MusicUtils returns duration in ms so subtract 60 seconds * 1000
				double maxOffset = (double)(duration - 60000) / duration;
				offset = (new Random().nextDouble() * maxOffset);
			}
			Log.v("OFFSET", "OFFSET: " + offset);
			msOffset = (int) (offset * duration);
			//msOffset = (int) (((double)(percent / 100)) * duration);
			// Is there enough song left to finish the minute?
			if(duration - msOffset < 60000){
				// Nope, play the whole song. Don't account for songs 
				// that are < 60 seconds: If the user put a song < 60
				// seconds on a Power Hour they're retarded and deserve to 
				// sit through silence.
				// TODO: Meh. Maybe do something about this. I'm sure voice memos don't want to be heard
				// then again, just skip it?
				msOffset = 0;
			}
		}
		return msOffset;
	}
	

	
	class AudioFocusStateManager implements OnAudioFocusChangeListener
	{
		private AudioManager audioManager;
		private ComponentName mediaButtonReceiverClass;
		private AudioFocusState currentState;
		private IAudioFocusLostListener audioFocusLostListener;
		
		private InitialState initialState;
		private FocusedState focusedState;
		private NoFocusNoDuckState noFocusNoDuckState;
		private NoFocusCanDuckState noFocusCanDuckState;
		
		AudioFocusStateManager(Context context, IAudioFocusLostListener audioFocusLostListener) {
			audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			mediaButtonReceiverClass = new ComponentName(context, MediaButtonsReceiver.class);
			this.audioFocusLostListener = audioFocusLostListener;
			
			initialState = new InitialState();
			focusedState = new FocusedState();
			noFocusNoDuckState = new NoFocusNoDuckState();
			noFocusCanDuckState = new NoFocusCanDuckState();
			
			currentState = initialState;
		}
		
		boolean hasFocus() {
			return currentState == focusedState;
		}
		
		void tryGetAudioFocus() {
			if(currentState != focusedState && AudioManager.AUDIOFOCUS_REQUEST_GRANTED == 
				audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)) {
				currentState.onGainingFocus();
				currentState = focusedState;
			}
		}
		
		void tryAbandonAudioFocus() {
			if(currentState != noFocusNoDuckState 
					&& AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this)) {
				currentState.onGivingUpFocus();
				currentState = noFocusNoDuckState;
			}
		}

		@Override
		public void onAudioFocusChange(int focusCode) {
			String tag = "SongPlayer";
			switch(focusCode){
			
			case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
			case AudioManager.AUDIOFOCUS_GAIN:
				Log.v(tag, "AUDIOFOCUS_GAIN");
				if(currentState != focusedState) {
					currentState.onGainingFocus();
					currentState = focusedState;
				}
				break;
			case AudioManager.AUDIOFOCUS_LOSS:
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				if(currentState != noFocusNoDuckState) {
					currentState.onRobbedOfFocusNoDuck();
					currentState = noFocusNoDuckState;
				}
				break;
			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				Log.v(tag, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
				if(currentState != noFocusCanDuckState) {
				// We can duck, just lower the volume for now
					currentState.onRobbedOfFocusCanDuck();
					currentState = noFocusCanDuckState;
				}
				break;
			}
		}
		
		abstract class AudioFocusState
		{
			private String TAG = "AudioFocus";
			
			void onGainingFocus() { 
				Log.v(TAG, "Gained Focus");
				// Register buttons
				audioManager.registerMediaButtonEventReceiver(mediaButtonReceiverClass);
			}
			
			/**
			 * Report indefinite loss of focus
			 */
			void onRobbedOfFocusNoDuck() {
				Log.v(TAG, "Lost Focus No Duck");
				audioFocusLostListener.onAudioFocusLost();
				audioManager.unregisterMediaButtonEventReceiver(mediaButtonReceiverClass);
			}
			
			/**
			 * Lower volume
			 */
			void onRobbedOfFocusCanDuck() {
				Log.v(TAG, "Lost Focus Can DucK");
				mplayer.setVolume(0.1f, 0.1f);
			}
			
			void onGivingUpFocus() {
				Log.v(TAG, "Giving up focus");
				// Unregister buttons
				audioManager.unregisterMediaButtonEventReceiver(mediaButtonReceiverClass);
			}
		}
		
		class InitialState extends AudioFocusState { }
		
		class FocusedState extends AudioFocusState { 
			@Override
			void onGainingFocus() { }
		}
		
		class NoFocusNoDuckState extends AudioFocusState { }
			
		class NoFocusCanDuckState extends AudioFocusState
		{
			@Override
			void onGainingFocus() { 
				Log.v(TAG, "Bump volume up");
				// Regained focus. Bump volume back up
				mplayer.setVolume(1.0f, 1.0f);
			}
		}
	}
}
