package gilday.android.powerhour;

import gilday.android.powerhour.model.PlaylistItem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

public class MusicUtils {

	private static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
	private static final BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();

	static{
		sBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptions.inDither = false;
	}
	
	public static PlaylistItem getInfoPack(Context context, int id){
		return getInfoPack(context, id, true);
	}
	
	public static PlaylistItem getInfoPack(Context context, int id, boolean loadBitmap) {
		ContentResolver resolver = context.getContentResolver();
		Cursor c = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				new String[] {MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, 
							MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM_ID},
				MediaStore.Audio.Media._ID + "=" + id, null, null);
		c.moveToFirst();
		PlaylistItem info = null;
		try{
			info = new PlaylistItem();
			info.id = id;
			info.artist = c.getString(c.getColumnIndex(MediaStore.Audio.Media.ARTIST));
			info.album = c.getString(c.getColumnIndex(MediaStore.Audio.Media.ALBUM));
			info.song = c.getString(c.getColumnIndex(MediaStore.Audio.Media.TITLE));
            info.albumId = c.getInt(c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
			if(loadBitmap) {
				info.art = getArtwork(context, info.albumId);
			}
		}
		catch(Exception e){
			Log.e("getID3", e.toString());
		} finally {
			c.close();
		}
		return info;
	}
	
	
	public static String getPathForSong(int id){
		//Log.d("utils", "get path for id=" + id);
		return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id;
	}
	
	public static long getDuration(Context context, int id){
		Cursor c = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				new String[] {MediaStore.Audio.AudioColumns.DURATION}, 
				MediaStore.Audio.Media._ID + "=" + id, null, null);
		if(!c.moveToFirst()){
			return -1;
		}
		long duration = c.getLong(c.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION));
		c.close();
		return duration;
	}
	
	/**
	 * Modified from method by the same name in the MusicUtils class found in the Android project 
	 * @param context
	 * @param album_id
     * @param sampleSize corresponds to BitmapFactory.Options sample size. 1 = full size
	 * @return
	 */
	public static Bitmap getArtwork(Context context, int album_id, int sampleSize) {
        sBitmapOptions.inSampleSize = sampleSize;
        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        Bitmap bitmap = null;
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(in, null, sBitmapOptions);
            } catch (FileNotFoundException ex) {
                // The album art thumbnail does not actually exist. Maybe the user deleted it, or
                // maybe it never existed to begin with.
            	
                //Bitmap bm = getArtworkFromFile(context, null, album_id);
                return null;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
        }
        return bitmap;
    }

    public static Bitmap getArtwork(Context context, int album_id) {
        return getArtwork(context, album_id, 1);
    }
	
	public static int getPlaylistSize(Context context, long id) {
		final String[] cols = new String[] { MediaStore.Audio.Playlists.Members.AUDIO_ID };
		Cursor cursor = context.getContentResolver().query(
				MediaStore.Audio.Playlists.Members.getContentUri("external", id),
				cols, null, null, MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
		if(cursor == null){
			return -1;
		}
		return cursor.getCount();
	}

}

//SongInfo song = new SongInfo();
//song.artist = songsCursor.getString(songsCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
//song.album = songsCursor.getString(songsCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
//song.song = songsCursor.getString(songsCursor.getColumnIndex(MediaStore.Audio.Media.TRACK));
//String artUriString = songsCursor.getString(songsCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ART));
//if(artUriString != null && artUriString.length() > 0){
//	song.artUri = Uri.parse(artUriString);
//}