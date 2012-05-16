package gilday.android.powerhour.data;

import gilday.android.powerhour.data.PowerHour.NowPlaying;

import java.util.Collections;
import java.util.Random;
import java.util.Stack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
		
		// Clear the now playing list since we are initializing a new one
		context.getContentResolver().delete(NowPlaying.CONTENT_URI, null, null);
        
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
        // Will build a list of ContentValues to give to the ContentProvider's bulkInsert
        ContentValues[] values = new ContentValues[songsToImportCount];
        // Decide which implementation of ImportCursorIterator to use
        ImportCursorIterator importCursorIterator = quickLoad
    		? new QuickLoadImportCursorIterator()
        	: new LoadAllImportCursorIterator();
    	// Set importCursor to first position
    	importCursorIterator.moveToInitialSongImport();
    	
        long now = System.currentTimeMillis();
        while(i < songsToImportCount) {
			// Import
        	int songId = importCursor.getInt(sourceIdColumn);
        	String artist = importCursor.getString(sourceArtistColumn);
        	String album = importCursor.getString(sourceAlbumColumn);
        	String title = importCursor.getString(sourceTitleColumn);
        	int omit = 0;
        	int played = 0;
        	int shufflePosition = shuffledPositions.pop();
        	
        	ContentValues set = new ContentValues();
        	set.put(NowPlaying._ID, songId);
        	// Power Hour will not 0-index playlist positions so that we don't 
        	// have to bump this number up one in the user interface. Optimization 
        	// since this string to int and back conversion will happen a lot in a 
        	// list view
        	set.put(NowPlaying.POSITION, i + 1);
        	set.put(NowPlaying.SHUFFLE_POSITION, shufflePosition + 1);
        	set.put(NowPlaying.ARTIST, artist);
        	set.put(NowPlaying.ALBUM, album);
        	set.put(NowPlaying.TITLE, title);
        	set.put(NowPlaying.OMIT, omit);
        	set.put(NowPlaying.PLAYED, played);
        	
        	values[i] = set;
			
			if(i % reportInterval == 0) 
			{
				publishProgress();
			}
			// iterate 
			i++;
			importCursorIterator.moveToNextSongImport(i);
		}
        long elapsed = System.currentTimeMillis() - now;
        Log.d("DB IMPORT", "" + elapsed);
        importCursor.close();
        
        // Now write everything to the content provider with a bulk insert
        context.getContentResolver().bulkInsert(NowPlaying.CONTENT_URI, values);
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
	
	private interface ImportCursorIterator
	{
		void moveToInitialSongImport();
		
		void moveToNextSongImport(int iteration);
	}
	
	private class QuickLoadImportCursorIterator implements ImportCursorIterator
	{
		Random rand;
		int bucketSize;
		
		public QuickLoadImportCursorIterator() {
    		int importSize = importCursor.getCount();
    		bucketSize = (int) (importSize / QUICKLOAD_THRESHOLD);
    		rand = new Random();
		}

		public void moveToInitialSongImport() {
			moveToRandom(0);
		}

		public void moveToNextSongImport(int iteration) {
			moveToRandom(iteration);
		}
		
		private void moveToRandom(int iteration) {
			// Move the cursor to the next source
			int bucketIndex = rand.nextInt(bucketSize);
			int sourcePosition = (iteration * bucketSize) + bucketIndex;
			importCursor.moveToPosition(sourcePosition);
		}
	}
	
	private class LoadAllImportCursorIterator implements ImportCursorIterator
	{

		public void moveToInitialSongImport() {
			importCursor.moveToFirst();
		}

		public void moveToNextSongImport(int iteration) {
			importCursor.moveToNext();
		}
	}
}
