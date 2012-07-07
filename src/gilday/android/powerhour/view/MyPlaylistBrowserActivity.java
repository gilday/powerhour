/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.MusicUtils;
import gilday.android.powerhour.R;
import gilday.android.powerhour.data.Keys;
import gilday.android.powerhour.data.PreferenceRepository;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * @author John Gilday
 *
 */
public class MyPlaylistBrowserActivity extends Activity {
	
	private static String ID   = "playlist_id";
	private static String NAME = "playlist_name";
	private static String SIZE = "playlist_size";
	
	// List of playlists in the form of key,value property mappings. Used for SimpleAdapter
	private ArrayList<HashMap<String,String>> playlists = new ArrayList<HashMap<String,String>>();
	private SimpleAdapter myAdapter;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setTitle(getString(R.string.PlaylistBrowserTitle));
		setContentView(R.layout.playlistbrowser);
		
		int duration = new PreferenceRepository(this).getDuration();
		// Set up filter button to reload playlists
		Button filterButton = (Button)findViewById(R.id.filter_button);
		filterButton.setText("Show playlists with " + duration + " or more songs");
		filterButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				loadPlaylists(true);
			}
		});

        ListView playlist_listView = (ListView) findViewById(R.id.playlist_ListView);
		myAdapter = new SimpleAdapter(this, playlists, R.layout.playlist_item, 
				new String[] { NAME, SIZE }, new int[] { R.id.playlist_name, R.id.playlist_size });
		playlist_listView.setAdapter(myAdapter);
		
		// When a playlist is selected, get its ID from the hashmap
		playlist_listView.setOnItemClickListener(new ListView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
                Intent respondIntent = new Intent();
                int playlistId = Integer.parseInt(playlists.get(position).get(ID));
                respondIntent.putExtra(Keys.PLAYLIST_ID_KEY, playlistId);
                setResult(RESULT_OK, respondIntent);
                finish();
            }
        });
		// Load all playlists at first
		loadPlaylists(false);
	}
	
	/**
	 * Populates the ListView with playlists from the Android music system
	 * @param filter Should this method only show playlists that contain enough songs to meet the duration of the power hour
	 */
	private void loadPlaylists(boolean filter) {
		// Initially clear the playlists structure
		playlists.clear();
		// Get playlist information from the system's MediaStore
		// Include playlist ID and name
		Cursor cursor = getContentResolver().query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
				new String[] {MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME}, null, null, 
				MediaStore.Audio.Playlists.DEFAULT_SORT_ORDER);
        if(!cursor.moveToFirst()){
        	// Cursor is empty, return
        	return;
        }
        int colidx = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID);
        int colnamex = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME);
        // Get duration if filtering
        int duration = new PreferenceRepository(this).getDuration();
        // Generate key,value property mappings for each playlist
        do {
        	int playlistId = cursor.getInt(colidx);
        	// measure playlist size
        	int size = MusicUtils.getPlaylistSize(this, playlistId);
        	// If we're filtering playlists that are too short, check if playlist size is sufficient
        	if(!filter || size >= duration) {
        		HashMap<String,String> playlist = new HashMap<String,String>();
	        	playlist.put(ID, "" + playlistId);
	        	playlist.put(NAME, cursor.getString(colnamex));
	        	playlist.put(SIZE, String.format(getString(R.string.playlistSongsCount), size));
	        	playlists.add(playlist);
        	}
        } while(cursor.moveToNext());
        cursor.close();
        myAdapter.notifyDataSetChanged();
	}
}
