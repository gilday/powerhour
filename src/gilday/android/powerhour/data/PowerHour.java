package gilday.android.powerhour.data;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Prevent magic strings. 
 * Define static final strings for PowerHour database tables and columns 
 * @author jgilday
 *
 */
public final class PowerHour {
	
	public static final String DATABASE = "powerhourpro";

	/**
	 * NowPlaying table
	 *
	 */
	public static final class NowPlaying implements BaseColumns {
		
		/**
		 * Can't instantiate
		 */
		private NowPlaying() { }
		
		/**
		 * The name of this table in SQLite
		 */
		public static final String TABLE = "current_playlist";
		
		/**
		 * Content URI for the now playing table
		 */
		public static final Uri CONTENT_URI = Uri.parse("content://com.johnathangilday.powerhour.provider/" + TABLE);
		
		/**
		 * MIME type of {@link #CONTENT_URI} providing a list of playlist items for the now playing playlist
		 */
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.powerhourpro.playlist";
		
		/**
		 * MIME type of a single {@link #CONTENT_URI} playlist item
		 */
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.powerhourpro.playlist";
		
		/**
		 * By default, sort Now Playing table by song position in increasing order
		 */
		public static final String DEFAULT_SORT_ORDER = "position asc";
		
		/**
		 * The song's position in the playlist
		 * <p>Type: INTEGER</p>
		 */
		public static final String POSITION = "position";
		
		/**
		 * The song's position in the playlist when shuffle is enabled
		 * <p>Type: INTEGER</p>
		 */
		public static final String SHUFFLE_POSITION = "shuffle_position";
		
		/**
		 * The song's title
		 * <p>Type: VARCHAR</p>
		 */
		public static final String TITLE = "title";
		
		/**
		 * The name of the album this song is from
		 * <p>Type: VARCHAR</p>
		 */
		public static final String ALBUM = "album";
		
		/**
		 * Android music system's ID for the album that this song is from. Useful for getting the album art
		 * <p>Type: VARCHAR</p> 
		 */
		public static final String ALBUM_ID = "album_id";
		
		/**
		 * The name of the artist that performs this song
		 * <p>Type: VARCHAR</p>
		 */
		public static final String ARTIST = "artist";
		
		/**
		 * If true, song will not play when its turn is up in the playlist but it is not removed from the list
		 * <p>Type: INTEGER</p>
		 */
		public static final String OMIT = "omit";
		
		/**
		 * If true, this song is playing or has been played 
		 * <p>TYPE: INTEGER</p>
		 */
		public static final String PLAYED = "played";
	}
}
