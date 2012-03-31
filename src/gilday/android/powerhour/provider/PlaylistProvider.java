/**
 * 
 */
package gilday.android.powerhour.provider;

import gilday.android.powerhour.data.CurrentPlaylistDatabaseHelper;
import gilday.android.powerhour.data.PowerHour;
import gilday.android.powerhour.data.PowerHour.NowPlaying;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;

/**
 * ContentProvider for Power Hour data. Currently provides access to the power hour playlist stored in SQLite
 * @author jgilday
 *
 */
public class PlaylistProvider extends ContentProvider {

	private static final UriMatcher URI_MATCHER;
	private static final int NOW_PLAYING = 1;
	private static final int NOW_PLAYING_ID = 2;
	
	private CurrentPlaylistDatabaseHelper dbHelper;
	
	static {
		// Establish the UriMatcher in the static block since it is a static variable.
		// Just copying android developer documentation here. Not sure what the rationale is for making the 
		// UriMatcher a static variable
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI("com.johnathangilday.powerhour.provider", NowPlaying.TABLE, NOW_PLAYING);
		URI_MATCHER.addURI("com.johnathangilday.powerhour.provider", NowPlaying.TABLE + "/#", NOW_PLAYING_ID);
	}
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		dbHelper = new CurrentPlaylistDatabaseHelper(getContext());
		return true;
	}
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(
			Uri uri, 
			String[] projection, 
			String selection, 
			String[] selectionArgs,
			String sortOrder) {
		
		switch(URI_MATCHER.match(uri)) {
		case NOW_PLAYING:
			// Going to list playlist items. Initialize sortOrder if not set
			// Android didn't implement String.isEmpty() until Gingerbread...
			if(TextUtils.isEmpty(sortOrder)) 
				sortOrder = PowerHour.NowPlaying.DEFAULT_SORT_ORDER;
			break;
		case NOW_PLAYING_ID:
			// This uri is for a single playlist item so set selection arg
			selection += "_ID = " + uri.getLastPathSegment();
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		SQLiteDatabase readableDb = dbHelper.getReadableDatabase();
		Cursor cursor = readableDb.query(PowerHour.NowPlaying.TABLE, projection, selection, selectionArgs, null, null, sortOrder);
		// Following example on this next line
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		return cursor;
	}
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		// Validate uri
		if(URI_MATCHER.match(uri) != NOW_PLAYING) {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		if(initialValues == null) {
			throw new IllegalArgumentException("Must include comprehensive set of initial values");
		}
		// Hold the values to be inserted in the new row
		ContentValues values = new ContentValues(initialValues);
		// Not yet sure how insert will be used. Perhaps do more input checking 
		// or automatically retrieve values from android music provider here
		
		SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
		long rowId = writableDb.insert(PowerHour.NowPlaying.TABLE, null, values);
		if(rowId <= 0) {
			throw new SQLException("Failed to insert row into " + uri);
		}
		Uri playlistItemUri = ContentUris.withAppendedId(PowerHour.NowPlaying.CONTENT_URI, rowId);
		getContext().getContentResolver().notifyChange(playlistItemUri, null);
		return playlistItemUri;
	}
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		int count;
		switch(URI_MATCHER.match(uri)) {
		case NOW_PLAYING:
			count = db.update(NowPlaying.TABLE, values, where, whereArgs);
			break;
		case NOW_PLAYING_ID:
			String playlistItemId = uri.getLastPathSegment();
			count = db.update(NowPlaying.TABLE, values, NowPlaying._ID + "=" + playlistItemId, whereArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
	
	/* (non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
		int count;
		
		switch(URI_MATCHER.match(uri)) {
		case NOW_PLAYING:
			count = writableDb.delete(NowPlaying.TABLE, where, whereArgs);
			break;
		case NOW_PLAYING_ID:
			String playlistItemId = uri.getLastPathSegment();
			count = writableDb.delete(
					NowPlaying.TABLE, 
					NowPlaying._ID + "=" + playlistItemId + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
					whereArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
			
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	@Override
	public String getType(Uri uri) {
		switch(URI_MATCHER.match(uri)){
		case NOW_PLAYING:
			return NowPlaying.CONTENT_TYPE;
		case NOW_PLAYING_ID:
			return NowPlaying.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

}
