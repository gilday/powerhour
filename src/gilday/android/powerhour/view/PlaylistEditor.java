/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import gilday.android.powerhour.data.PlaylistRepository;
import gilday.android.powerhour.view.PlaylistCursorAdapter.SongOmitHandler;
import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

/**
 * @author Johnathan Gilday
 *
 */
public class PlaylistEditor extends Activity implements SongOmitHandler {
	
	PlaylistRepository playlistRepo = PlaylistRepository.getInstance(); 
	
	private View playlistEditorView;
	private ListView playlistView;
	
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setTitle(getString(R.string.PlaylistEditorTitle));
		// Set temporary loading view
		// this probably isn't relevant any longer since the adapter is 
		// no longer loaded in a background task
		setContentView(R.layout.playlist_loading);
	
		playlistEditorView = View.inflate(getApplicationContext(), R.layout.playlist_editor, null);
		playlistView = (ListView)playlistEditorView.findViewById(R.id.playlistEditor_ListView);

		// Get cursor from repository
		Cursor cursor = playlistRepo.getCursorForPlaylist();
		// Create adapter
		PlaylistCursorAdapter adapter = new PlaylistCursorAdapter(this.getApplicationContext(), cursor);
		adapter.setOmitToggleListener(this);
		// Set view and adapter
		setContentView(playlistEditorView);
		playlistView.setAdapter(adapter);
	}

	/**
	 * Handles the event when the user toggles a song in the playlist for 
	 * omission or inclusion in the power hour
	 */
	public void onSongOmissionChanged(int songId, boolean isChecked) {
		playlistRepo.setSongOmission(songId, isChecked);
	}
}
