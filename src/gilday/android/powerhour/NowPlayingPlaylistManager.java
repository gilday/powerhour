package gilday.android.powerhour;

import gilday.android.powerhour.data.PowerHour.NowPlaying;
import gilday.android.powerhour.data.PreferenceRepository;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

class NowPlayingPlaylistManager {
	
	private Context context;
	private int currentSong = -1;
	
	/**
	 * Construct a NowPlayingPlaylistManager to keep track of the Now Playing playlist's progress
	 * @param context
	 */
	public NowPlayingPlaylistManager(Context context) {
		this.context = context;
	}
	
	public int advancePlaylist() {
		// Set current song as played
		if(currentSong >= 0) {
			ContentValues set = new ContentValues();
			set.put(NowPlaying.PLAYED, true);
			Uri updateUri = ContentUris.withAppendedId(NowPlaying.CONTENT_URI, currentSong);
			context.getContentResolver().update(updateUri, set, null, null);
		}
		// Advance playlist
		currentSong = getNextSong();
		return currentSong;
	}
	
	public int getCurrentSong() {
		return this.currentSong;
	}
	
	public int getPlaylistSize() {
		return context
				.getContentResolver()
				.query(NowPlaying.CONTENT_URI, null, null, null, null)
				.getCount();
	}
	
	public boolean isPlayingLastSong() {
		return getNextSong() == -1;
	}
	
	private int getNextSong() {
		// if shuffle
		boolean shuffle = new PreferenceRepository(context).isShuffle();
		String positionColumn = shuffle ? NowPlaying.SHUFFLE_POSITION : NowPlaying.POSITION;
		Cursor cursor = context.getContentResolver().query(
				NowPlaying.CONTENT_URI, 
				new String[] { NowPlaying._ID, NowPlaying.POSITION, NowPlaying.SHUFFLE_POSITION }, 
				NowPlaying.PLAYED + " = ? AND " + NowPlaying.OMIT + " = ?", 
				new String[] { "0", "0" }, 
				positionColumn + " ASC");
		// if there are no more songs left
		if(!cursor.moveToFirst()) {
			// return -1 to indicate this
			return -1;
		}
		int songId = -1;

		songId = cursor.getInt(cursor.getColumnIndex(NowPlaying._ID));
		cursor.close();
		return songId;
	}
	
	public void clearPlaylist() {
		// Delete the now playing playlist
		context.getContentResolver().delete(NowPlaying.CONTENT_URI, null, null);
		// Reset the current song and position
		currentSong = -1;
	}

}
