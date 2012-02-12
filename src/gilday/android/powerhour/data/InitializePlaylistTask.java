package gilday.android.powerhour.data;

import java.util.Random;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

/**
 * Implements all the background leg work for initializing the Power Hour playlist from the 
 * Android MediaStore. Can initialize the playlist based on all the songs in the MediaStore 
 * or from a playlist saved in the MediaStore. Leaves the onProgressUpdate and onPostExecute 
 * unimplemented so that a subclass of this abstract class may define how progress is conveyed 
 * to the user.
 * @author jgilday
 *
 */
public abstract class InitializePlaylistTask extends AsyncTask<Void, Void, Void> {
	private Cursor importCursor;
	protected Context context;
	protected int songsToImportCount = 0;
	protected int reportInterval = 5;

	/**
	 * Creates an InitializePlaylistTask which will create a new Power Hour playlist from 
	 * all the songs stored in the Android MediaStore
	 * @param context Need a context to get the MediaStore content resolver
	 */
	public InitializePlaylistTask(Context context) {
		this.context = context;
		if(context == null) {
			throw new IllegalArgumentException();
		}
		importCursor = context.getContentResolver().query(
        		MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[] {
        			MediaStore.Audio.Media._ID,
        			MediaStore.Audio.Media.ARTIST,
        			MediaStore.Audio.Media.ALBUM,
        			MediaStore.Audio.Media.ALBUM_ID,
        			MediaStore.Audio.Media.TITLE 
        			}, 
        		MediaStore.Audio.Media.IS_MUSIC + "=1",
        		null, null);
		setSongsToImportCount();
	}
	
	/**
	 * Creates an InitializePlaylistTask which will create a new Power Hour playlist from 
	 * a specific playlist in the Android MediaStore
	 * @param context Need a context to get the MediaStore content resolver
	 * @param playlistId The ID of the MediaStore.Audio.Playlists
	 */
	public InitializePlaylistTask(Context context, int playlistId) {
		this.context = context;
		if(context == null) {
			throw new IllegalArgumentException();
		}
		final String[] ccols = new String[] { 
				MediaStore.Audio.Playlists.Members.AUDIO_ID,
    			MediaStore.Audio.Media.ARTIST,
    			MediaStore.Audio.Media.ALBUM,
    			MediaStore.Audio.Media.ALBUM_ID,
    			MediaStore.Audio.Media.TITLE  };
		importCursor = context.getContentResolver().query(
	    		MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
	    		ccols, 
	    		MediaStore.Audio.Media.IS_MUSIC + "=1", 
	    		null, 
	    		MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
		setSongsToImportCount();
	}
	
	private void setSongsToImportCount() 
	{
		songsToImportCount = importCursor.getCount();
		PreferenceRepository prefsRepo = new PreferenceRepository(context);
		if(songsToImportCount > 240 && prefsRepo.getQuickLoad()) 
		{
			songsToImportCount = 240;
		}
	}

	/**
	 * Does all the leg work for loading in the Power Hour playlist from the Android 
	 * MediaStore. Clears out the current playlist repository. Copies the Android playlist  
	 * into the Power Hour playlist table. Copying the data seems redundant but the Power 
	 * Hour playlist table has a unique schema with columns such as "omit".
	 */
	@Override
	protected Void doInBackground(Void... params) {
		final int playlistSize = importCursor.getCount();
		if(importCursor == null || playlistSize <= 0) {
			throw new IllegalArgumentException("There are no songs in this playlist");
		}
        
        PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
        playlistRepo.clearPlaylist();
        SQLiteDatabase writablePlaylistDB = playlistRepo.writablePlaylistDB;
        
        InsertHelper ih = new InsertHelper(writablePlaylistDB, "current_playlist");
        final int positionColumn = ih.getColumnIndex("position");
        final int idColumn = ih.getColumnIndex("_id");
        final int artistColumn = ih.getColumnIndex("artist");
        final int albumColumn = ih.getColumnIndex("album");
        final int titleColumn = ih.getColumnIndex("title");
        final int omitColumn = ih.getColumnIndex("omit");
        
        int i = 0;
        
        final int sourceIdColumn = 0;
        final int sourceArtistColumn = importCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        final int sourceAlbumColumn = importCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        final int sourceTitleColumn = importCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        
        PreferenceRepository prefsRepo = new PreferenceRepository(context);
    	
        long now = System.currentTimeMillis();
        writablePlaylistDB.setLockingEnabled(false);
        try 
        {
        	if(playlistSize > 240 && prefsRepo.getQuickLoad()) {
        		Random rand = new Random();
        		while(i < 240) 
        		{
        			int position = rand.nextInt(playlistSize - i) + i;
        			importCursor.moveToPosition(position);
        			
        			ih.prepareForInsert();
        			
		        	int songId = importCursor.getInt(sourceIdColumn);
		        	String artist = importCursor.getString(sourceArtistColumn);
		        	String album = importCursor.getString(sourceAlbumColumn);
		        	String title = importCursor.getString(sourceTitleColumn);
		        	int omit = 0;
		        	
		        	ih.bind(idColumn, songId);
		        	ih.bind(positionColumn, i);
		        	ih.bind(artistColumn, artist);
		        	ih.bind(albumColumn, album);
		        	ih.bind(titleColumn, title);
		        	ih.bind(omitColumn, omit);
		        	
		        	ih.execute();
        			i++;
        			
        			if(i % reportInterval == 0) 
        			{
        				publishProgress();
        			}
        		}
        		
        	}
        	else {
		        while(importCursor.moveToNext()) {
		        	ih.prepareForInsert();
		        	
		        	int songId = importCursor.getInt(sourceIdColumn);
		        	String artist = importCursor.getString(sourceArtistColumn);
		        	String album = importCursor.getString(sourceAlbumColumn);
		        	String title = importCursor.getString(sourceTitleColumn);
		        	int omit = 0;
		        	
		        	ih.bind(idColumn, songId);
		        	ih.bind(positionColumn, i);
		        	ih.bind(artistColumn, artist);
		        	ih.bind(albumColumn, album);
		        	ih.bind(titleColumn, title);
		        	ih.bind(omitColumn, omit);
		        	
		        	ih.execute();
		        	i++;
		        	
		        	if(i % reportInterval == 0)
		        	{
		        		publishProgress();
		        	}
		        }
        	}
        }
        finally 
        {
        	writablePlaylistDB.setLockingEnabled(true);
        }
        long elapsed = System.currentTimeMillis() - now;
        Log.d("DB IMPORT", "" + elapsed);
        importCursor.close();
        ih.close();
		return null;
	}
}