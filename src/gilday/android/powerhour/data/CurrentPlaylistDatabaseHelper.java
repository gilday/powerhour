package gilday.android.powerhour.data;

import gilday.android.powerhour.data.PowerHour.NowPlaying;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Creates the Power Hour playlist SQLite database
 * @author jgilday
 *
 */
public class CurrentPlaylistDatabaseHelper extends SQLiteOpenHelper {
	
	private static int DATABASE_VERSION = 1;
    private static final String PLAYLIST_TABLE_CREATE =
        "CREATE TABLE " + NowPlaying.TABLE + " (" +
        NowPlaying._ID + " INTEGER, " +
        NowPlaying.POSITION + " INTEGER, " +
        NowPlaying.SHUFFLE_POSITION + " INTEGER, " +
        NowPlaying.TITLE + " VARCHAR, " + 
        NowPlaying.ALBUM + " VARCHAR, " +
        NowPlaying.ALBUM_ID + " VARCHAR, " +
        NowPlaying.ARTIST + " VARCHAR, " +
        NowPlaying.PLAYED + " INTEGER, " +
        NowPlaying.OMIT + " INTEGER);";


	public CurrentPlaylistDatabaseHelper(Context context) {
		super(context, PowerHour.DATABASE, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PLAYLIST_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}

}
