package gilday.android.powerhour.data;

import gilday.android.powerhour.data.PowerHour.NowPlaying;

import java.util.Collections;
import java.util.Random;
import java.util.Stack;

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
	private static final int QUICKLOAD_THRESHOLD = 180;
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
		if(songsToImportCount <= 0) {
			throw new IllegalStateException("Cannot initialize a power hour with no songs to import");
		}
		PreferenceRepository prefsRepo = new PreferenceRepository(context);
		if(songsToImportCount > (QUICKLOAD_THRESHOLD * 2) && prefsRepo.getQuickLoad()) 
		{
			songsToImportCount = QUICKLOAD_THRESHOLD;
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
		final int sourcePlaylistSize = importCursor.getCount();
		if(importCursor == null || sourcePlaylistSize <= 0) {
			throw new IllegalArgumentException("There are no songs in this playlist");
		}
        
        PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
        playlistRepo.clearPlaylist();
        SQLiteDatabase writablePlaylistDB = playlistRepo.writablePlaylistDB;
        
        InsertHelper ih = new InsertHelper(writablePlaylistDB, NowPlaying.TABLE);
        final int idColumn = ih.getColumnIndex(NowPlaying._ID);
        final int artistColumn = ih.getColumnIndex(NowPlaying.ARTIST);
        final int albumColumn = ih.getColumnIndex(NowPlaying.ALBUM);
        final int titleColumn = ih.getColumnIndex(NowPlaying.TITLE);
        final int omitColumn = ih.getColumnIndex(NowPlaying.OMIT);
        final int playedColumn = ih.getColumnIndex(NowPlaying.PLAYED);
        final int positionColumn = ih.getColumnIndex(NowPlaying.POSITION);
        final int shufflePositionColumn = ih.getColumnIndex(NowPlaying.SHUFFLE_POSITION);
        
        int i = 0;
        
        final int sourceIdColumn = 0;
        final int sourceArtistColumn = importCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        final int sourceAlbumColumn = importCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        final int sourceTitleColumn = importCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        
        boolean quickLoad = new PreferenceRepository(context).getQuickLoad() && importCursor.getCount() > (QUICKLOAD_THRESHOLD * 2);
        
		// Build the playlist positions in shuffled order
		// Will decide what shuffled order means up front instead of shuffling each item on the fly
		// this allows users to see what the jumbled playlist looks like
        Stack<Integer> shuffledPositions = getShuffledOrder();
    	
        long now = System.currentTimeMillis();
        
        importCursor.moveToFirst();
        writablePlaylistDB.setLockingEnabled(false);
        
        try 
        {
        	if(quickLoad) {
        		// Use fancy quick load algorithm to ensure we load enough songs for the power hour 
        		// but don't waste all the user's time loading in the entire song collection since 
        		// apparently it's huge
        		int importSize = importCursor.getCount();
        		int bucketSize = (int) (importSize / QUICKLOAD_THRESHOLD);
        		Random rand = new Random();
        		while(i < songsToImportCount) {
        			// Move the cursor to the next source
        			int bucketIndex = rand.nextInt(bucketSize);
        			int sourcePosition = (i * bucketSize) + bucketIndex;
        			importCursor.moveToPosition(sourcePosition);
        			// Import
        			ih.prepareForInsert();
        			
    	        	int songId = importCursor.getInt(sourceIdColumn);
    	        	String artist = importCursor.getString(sourceArtistColumn);
    	        	String album = importCursor.getString(sourceAlbumColumn);
    	        	String title = importCursor.getString(sourceTitleColumn);
    	        	int omit = 0;
    	        	int played = 0;
    	        	int shufflePosition = shuffledPositions.pop();
    	        	
    	        	ih.bind(idColumn, songId);
    	        	// Power Hour will not 0-index playlist positions so that we don't 
    	        	// have to bump this number up one in the user interface. Optimization 
    	        	// since this string to int and back conversion will happen a lot in a 
    	        	// list view
    	        	ih.bind(positionColumn, i + 1);
    	        	ih.bind(shufflePositionColumn, shufflePosition);
    	        	ih.bind(artistColumn, artist);
    	        	ih.bind(albumColumn, album);
    	        	ih.bind(titleColumn, title);
    	        	ih.bind(omitColumn, omit);
    	        	ih.bind(playedColumn, played);
    	        	
    	        	ih.execute();
        			
        			if(i % reportInterval == 0) 
        			{
        				publishProgress();
        			}
        			// iterate 
        			i++;
        		}
        	} else {
        		// Load all the songs without that fancy quick load algorithm
        		// move to first
        		importCursor.moveToFirst();
        		// Import all
        		while(i < songsToImportCount) {
        			// Import
        			ih.prepareForInsert();
        			
    	        	int songId = importCursor.getInt(sourceIdColumn);
    	        	String artist = importCursor.getString(sourceArtistColumn);
    	        	String album = importCursor.getString(sourceAlbumColumn);
    	        	String title = importCursor.getString(sourceTitleColumn);
    	        	int omit = 0;
    	        	int played = 0;
    	        	int shufflePosition = shuffledPositions.pop();
    	        	
    	        	ih.bind(idColumn, songId);
    	        	// Power Hour will not 0-index playlist positions so that we don't 
    	        	// have to bump this number up one in the user interface. Optimization 
    	        	// since this string to int and back conversion will happen a lot in a 
    	        	// list view
    	        	ih.bind(positionColumn, i + 1);
    	        	ih.bind(shufflePositionColumn, shufflePosition);
    	        	ih.bind(artistColumn, artist);
    	        	ih.bind(albumColumn, album);
    	        	ih.bind(titleColumn, title);
    	        	ih.bind(omitColumn, omit);
    	        	ih.bind(playedColumn, played);
    	        	
    	        	ih.execute();
        			
        			if(i % reportInterval == 0) 
        			{
        				publishProgress();
        			}
        			// iterate
        			i++;
        			importCursor.moveToNext();
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
	
	/**
	 * This needs to return a list of random integers in [0,songsToImportCount] with 
	 * no duplicates as fast as possible. These will be pop'd off with each imported song 
	 * to form the playlist's shuffled order
	 * @return
	 */
	private Stack<Integer> getShuffledOrder() {
		// Build a list of sequential integers [0, songsToImportCount]
		Stack<Integer> listToShuffle = new Stack<Integer>();
		for(int i = 0; i < songsToImportCount; i++) {
			listToShuffle.push(i);
		}
		// Use Java's built in shuffle algorithm to shuffle list in linear time
		Collections.shuffle(listToShuffle);
		return listToShuffle;
	}
}
