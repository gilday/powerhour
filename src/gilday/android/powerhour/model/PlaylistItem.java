/**
 * 
 */
package gilday.android.powerhour.model;

import android.graphics.Bitmap;

/**
 * @author John Gilday
 *
 */
public class PlaylistItem {
	public String artist;
	public String album;
	public String song;
	public Bitmap art;
    public int id;
	public boolean omit = false;
	
	public PlaylistItem() {}

	public PlaylistItem(String artist, String album, String song){
		this.artist = artist;
		this.album = album;
		this.song = song;
	}
}
