/**
 * 
 */
package gilday.android.powerhour.model;

import gilday.android.powerhour.MusicUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.MediaStore;
import android.widget.BaseAdapter;

/**
 * @author Johnathan Gilday
 *
 */
public class PlaylistRepository {
	
	private static PlaylistRepository instance;
	
	private int position = -1;
	private Object mutateLock;
	
	private int playlistId = -1;
	private ArrayList<Integer> playlist;
	SQLiteDatabase readablePlaylistDB;
	SQLiteDatabase writablePlaylistDB;
	private boolean[] omissions;

	private PlaylistRepository(){ 
		mutateLock = new Object();
	}
	
	public static PlaylistRepository getInstance(){
		if(instance == null){
			instance = new PlaylistRepository();
		}
		return instance;
	}
	
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
	
	public void clearPlaylist() {
		position = -1;
		writablePlaylistDB.delete("current_playlist", null, null);
	}
	
	public int getPlaylistSize() {
		if(readablePlaylistDB != null) {
			Cursor c = readablePlaylistDB.query("current_playlist", null, null, null, null, null, null);
			int playlistSize = c.getCount();
			c.close();
			return playlistSize;
		}
		return 0;
	}
	
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
				new String[] {"_id"}, 
				"position >= ?", 
				new String[] { position + "" }, 
				null, null, 
				"position ASC");
		// if there are no more songs left
		if(!cursor.moveToFirst()) {
			// return -1 to indicate this
			return -1;
		}
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
				"position > ?", 
				new String[] { "" + position }, 
				null, null, null);
		boolean isOver = !cursor.moveToFirst();
		cursor.close();
		return isOver;
	}
	
	public void toggleSongOmission(int songId) {
		// TODO: implement
		int index = playlist.indexOf(songId);
		omissions[index] = !omissions[index];
	}
	
	public List<PlaylistItem> getCurrentListViewModel(Context applicationContext) {
		if(playlist == null) { return null; }
		ArrayList<PlaylistItem> playlistVM = new ArrayList<PlaylistItem>(playlist.size());
		Iterator<Integer> itr = playlist.iterator();
		while(itr.hasNext()) {
			int songId = itr.next();
			PlaylistItem pi = MusicUtils.getInfoPack(applicationContext, songId, false);
			playlistVM.add(pi);
		}
		return playlistVM;		
	}
	
	public Cursor getCursorForPlaylist() 
	{
		Cursor cursor = writablePlaylistDB.query(
				"current_playlist", 
				new String[] { "_id", "title", "artist" }, 
				"position > ?", 
				new String[] { "" + position }, 
				null, null, null);
		return cursor;
	}
	
	public BaseAdapter getAdapterForPlaylist(Context c, int layout, int[] layoutItems) {
		ArrayList<HashMap<String,String>> playlistMap = new ArrayList<HashMap<String,String>>();
		final String NAME = "NAME";
		final String ARTIST = "ARTIST";
		final String ID = "ID";
		final String OMIT = "OMIT";
		
		// Populate playlist
		if(playlist != null) {
			// instead of using playlist, get cursor for performance reasons
			Cursor cursor;
			String[] projection;
			if(playlistId > 0) {
				projection = new String[] { 
					MediaStore.Audio.Playlists.Members.AUDIO_ID,
					MediaStore.Audio.Playlists.Members.ARTIST,
					MediaStore.Audio.Playlists.Members.TITLE
				};
			    cursor = c.getContentResolver().query(
			    	MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
			    	projection, 
			    	null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
			} else {
				projection = new String[] {
					MediaStore.Audio.Media._ID,
		        	MediaStore.Audio.Media.ARTIST,
		        	MediaStore.Audio.Media.TITLE 
				};
				cursor = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
					projection, 
		            MediaStore.Audio.Media.IS_MUSIC + "=1",
		            null, null);
			}
			
			int size = playlist.size();
			for(int i = 0; i < size; i++) {
				HashMap<String, String> item = new HashMap<String,String>();
				item.put(ID, "" + cursor.getInt(cursor.getColumnIndex(projection[0])));
				item.put(ARTIST, cursor.getString(cursor.getColumnIndex(projection[1])));
				item.put(NAME, cursor.getString(cursor.getColumnIndex(NAME)));
				
				playlistMap.add(item);
			}
			
			Iterator<Integer> itr = playlist.iterator();
			while(itr.hasNext()) {
				int id = itr.next();
				PlaylistItem song = MusicUtils.getInfoPack(c, id);
				HashMap<String,String> songItem = new HashMap<String,String>();
				songItem.put(NAME, song.song);
				songItem.put(ID, ""+id);
				songItem.put(ARTIST, song.artist);
				String omitString;
				if(song.omit) {
					omitString = "true";
				} else {
					omitString = "false";
				}
				songItem.put(OMIT, omitString);
				playlistMap.add(songItem);
			}
		}
//		
//		SimpleAdapter myAdapter = new SimpleAdapter(c, playlistMap, layout, 
//				new String[] { ID, NAME, ARTIST, OMIT }, layoutItems);
//		
//		return myAdapter;
		return null;
	}
}