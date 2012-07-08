package gilday.android.powerhour.service;

import java.io.IOException;
import java.util.Random;

import gilday.android.powerhour.IDisposable;
import gilday.android.powerhour.MusicUtils;
import gilday.android.powerhour.PowerHourPreferences;
import gilday.android.powerhour.data.PreferenceRepository;
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
class SongPlayer implements IDisposable, OnAudioFocusChangeListener {

	enum AudioFocus { NoFocusNoDuck, NoFocusCanDuck, Focus };
	
	private static final String TAG = "SongPlayer";
	final private Object mplayerLock = new Object();
	private MediaPlayer mplayer;
	private Context context;
	private PreferenceRepository powerHourPrefs;
	private AudioManager audioManagerSystem;
	private AudioFocus audioFocus = AudioFocus.NoFocusNoDuck;
	private IAudioFocusLostListener audioFocusLostListener;
	
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
		audioManagerSystem = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		this.audioFocusLostListener = audioFocusLostListener;
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
		
		tryAbandonAudioFocus();
	}
	
	public void play() {
		tryGetAudioFocus();
		if(audioFocus == AudioFocus.NoFocusNoDuck) {
			if(mplayer.isPlaying())
				mplayer.pause();
			return;
		}
		if(audioFocus == AudioFocus.NoFocusCanDuck) 
			// Lower volume
			mplayer.setVolume(0.1f, 0.1f);
		else 
			mplayer.setVolume(1.0f, 1.0f);
		
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
		tryAbandonAudioFocus();
	}

	@Override
	public void onAudioFocusChange(int focusCode) {
		String tag = "SongPlayer";
		switch(focusCode){
		
		case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
		case AudioManager.AUDIOFOCUS_GAIN:
			Log.v(tag, "AUDIOFOCUS_GAIN");
			audioFocus = AudioFocus.Focus;
			// Regained focus. Bump volume back up
			mplayer.setVolume(1.0f, 1.0f);
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			audioFocus = AudioFocus.NoFocusNoDuck;
			// On any loss, alert the IAudioFocusLostListener to pause the PowerHour
			audioFocusLostListener.onAudioFocusLost();
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			Log.v(tag, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
			// We can duck, just lower the volume for now
			audioFocus = AudioFocus.NoFocusCanDuck;
			mplayer.setVolume(0.1f, 0.1f);
			break;
		}
	}
	
	private void tryGetAudioFocus() {
		if(audioFocus == AudioFocus.Focus)
			return;
		if(AudioManager.AUDIOFOCUS_REQUEST_GRANTED == 
			audioManagerSystem.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN))
			audioFocus = AudioFocus.Focus;
	}
	
	private void tryAbandonAudioFocus() {
		if(audioFocus != AudioFocus.NoFocusNoDuck && AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManagerSystem.abandonAudioFocus(this))
				audioFocus = AudioFocus.NoFocusNoDuck;
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
}
