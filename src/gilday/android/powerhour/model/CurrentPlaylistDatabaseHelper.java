package gilday.android.powerhour.model;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CurrentPlaylistDatabaseHelper extends SQLiteOpenHelper {
	
	private static String DATABASE_NAME = "powerhour";
	private static int DATABASE_VERSION = 1;
	private static String PLAYLIST_TABLE_NAME = "current_playlist";
    private static final String PLAYLIST_TABLE_CREATE =
        "CREATE TABLE " + PLAYLIST_TABLE_NAME + " (" +
        "_id INTEGER, " +
        "position INTEGER, " +
        "title VARCHAR, " + 
        "album VARCHAR, " +
        "album_id VARCHAR, " +
        "artist VARCHAR, " +
        "omit INTEGER);";


	public CurrentPlaylistDatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PLAYLIST_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}

}
