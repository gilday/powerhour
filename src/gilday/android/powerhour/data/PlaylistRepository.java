/**
 * 
 */
package gilday.android.powerhour.data;

import java.util.Random;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Encapsulates operations for retrieving data from and affecting the Power Hour 
 * playlist. This PlaylistRepository is backed by a SQLite database but the 
 * repository pattern hides this from the rest of the application
 * @author Johnathan Gilday
 *
 */
public class PlaylistRepository {
	
	private static PlaylistRepository instance;
	
	private int position = -1;
	private Object mutateLock;
	
	SQLiteDatabase readablePlaylistDB;
	SQLiteDatabase writablePlaylistDB;

	private PlaylistRepository(){ 
		mutateLock = new Object();
	}
	
	/**
	 * Follows Singleton pattern
	 * TODO: See if we can't use dependency injection to remove consumers' implicit 
	 * dependency on this implementation
	 * @return
	 */
	public static PlaylistRepository getInstance(){
		if(instance == null){
			instance = new PlaylistRepository();
		}
		return instance;
	}
	
	/**
	 * Creates or opens the database and gets references writable and read only references to 
	 * database helpers
	 * @param context Need a context to pass on to the SQLite database helper
	 */
	public void init(Context context) {
		if(readablePlaylistDB != null) {
			readablePlaylistDB.close();
		}
		if(writablePlaylistDB != null) {
			writablePlaylistDB.close();
		}
		CurrentPlaylistDatabaseHelper helper = new CurrentPlaylistDatabaseHelper(context);
		readablePlaylistDB = helper.getReadableDatabase();
        writablePlaylistDB = helper.getWritableDatabase();
        clearPlaylist();        
	}
	
	/**
	 * Deletes all the items in the Power Hour playlist
	 */
	public void clearPlaylist() {
		position = -1;
		writablePlaylistDB.delete("current_playlist", null, null);
	}
	
	/**
	 * Measures the total size of the playlist INCLUDING songs flagged for omission. Returns 0 if the 
	 * playlist has yet to be initialized.
	 * @return
	 */
	public int getPlaylistSize() {
		if(readablePlaylistDB != null) {
			Cursor c = readablePlaylistDB.query("current_playlist", null, null, null, null, null, null);
			int playlistSize = c.getCount();
			c.close();
			return playlistSize;
		}
		return 0;
	}
	
	/**
	 * Returns the current position of the Power Hour playlist. Represents how many songs into the 
	 * playlist this power hour is- not how many minutes have passed.
	 * @return
	 */
	public int getPlaylistPosition()
	{
		return position;
	}
	
	/**
	 * Will advance the playlist one song and return its song id
	 * @param shuffle whether or not to shuffle the playlist
	 * @return the song id of the song which will play next. -1 means no more songs
	 */
	public int getNextSong(boolean shuffle) {
		// Advance playlist
		position++;
		Cursor cursor = writablePlaylistDB.query(
				"current_playlist", 
				new String[] {"_id", "position"}, 
				"position >= ? AND omit = ?", 
				new String[] { position + "", "0" }, 
				null, null, 
				"position ASC");
		// if there are no more songs left
		if(!cursor.moveToFirst()) {
			// return -1 to indicate this
			return -1;
		}
		// Since the SQL query only takes songs that have not been omitted,  
		// have the repository up its position to the first song returned; thus, 
		// skipping over the omitted songs. If no songs have been skipped over, 
		// then this shouldn't change the position at all
		Log.d("PlaylistRepository", "Position was: " + position);
		position = cursor.getInt(cursor.getColumnIndex("position"));
		Log.d("PlaylistRepository", "Position  is: " + position);
		int songId = -1;
		if(shuffle){
			// SHUFFLE ALGORITHM
			// get an index in [current position , end]. This gives a random index for 
			// a song that has not been played yet
			// int nextIndex = new Random().nextInt(playlist.size() - position) + position;
			int nextIndex = new Random().nextInt(cursor.getCount());
			// swap this song that has not been played yet with the current index position
			cursor.moveToPosition(nextIndex);
			songId = cursor.getInt(cursor.getColumnIndex("_id"));
			ContentValues updateOldPositionValues = new ContentValues();
			updateOldPositionValues.put("position", position + nextIndex);
			synchronized(mutateLock) {
				writablePlaylistDB.update(
						"current_playlist", 
						updateOldPositionValues, 
						"position = ?", 
						new String[] { "" + position });
				ContentValues updateNewPositionValues = new ContentValues();
				updateNewPositionValues.put("position", position);
				writablePlaylistDB.update(
						"current_playlist",
						updateNewPositionValues,
						"_id = ?",
						new String[] { "" + songId });
			}
			cursor.close();
			return songId;
		}
		songId = cursor.getInt(cursor.getColumnIndex("_id"));
		cursor.close();
		return songId;
	}
	
	/** Will advance the playlist one song and return its song id
	 * @param c need a context to get stored preferences to check if shuffle is enabled
	 * @return the song id of the song which will play next. -1 means no more songs
	 */
	public int getNextSong(Context c) {
		boolean shuffle = new PreferenceRepository(c).isShuffle();
		return getNextSong(shuffle);
	}
	
	public int getCurrentSong() {
		if(position < 0) {
			return position;
		}
		Cursor cursor;
		synchronized(mutateLock) {
			cursor = readablePlaylistDB.query(
					"current_playlist", 
					new String[] { "_id" }, 
					"position == ?", 
					new String[] { "" + position }, 
					null, null, null);
			cursor.moveToFirst();
		}
		int currentSong = cursor.getInt(cursor.getColumnIndex("_id"));
		cursor.close();
		return currentSong;
	}
	
	public boolean isPlayingLastSong() {
		Cursor cursor = readablePlaylistDB.query(
				"current_playlist", 
				new String[] {"_id"}, 
				"position > ? AND omit = ?", 
				new String[] { "" + position, "0" }, 
				null, null, null);
		boolean isOver = !cursor.moveToFirst();
		cursor.close();
		return isOver;
	}
}