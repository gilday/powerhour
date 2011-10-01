/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import gilday.android.powerhour.model.PlaylistItem;
import gilday.android.powerhour.model.PlaylistRepository;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/**
 * @author Johnathan Gilday
 *
 */
public class PlaylistEditor extends Activity {
	
	PlaylistRepository playlistRepo = PlaylistRepository.getInstance(); 
	
	private View playlistEditorView;
	private ListView playlistView;
	
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setTitle(getString(R.string.PlaylistEditorTitle));
		setContentView(R.layout.playlist_loading);
	
		playlistEditorView = View.inflate(getApplicationContext(), R.layout.playlist_editor, null);
		playlistView = (ListView)playlistEditorView.findViewById(R.id.playlistEditor_ListView);
		
//		setContentView(R.layout.playlist_editor);
//		playlistView = (ListView)findViewById(R.id.playlistEditor_ListView);
		
		// When a playlist is selected, get its ID from the hashmap
		playlistView.setOnItemClickListener(new ListView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
				long songId = (playlistView.getAdapter().getItemId(position));
				//playlistRepo.toggleSongOmission(songId);
			}
		});
		
		LoadPlaylistTask loadList = new LoadPlaylistTask();
		loadList.execute(playlistRepo);
//		playlistView.setAdapter(createTestCursorAdapter());
	}
	
	private class PlaylistItemAdapter extends BaseAdapter{
		
		private List<PlaylistItem> playlistViewModel;
		
		public PlaylistItemAdapter(List<PlaylistItem> playlistViewModel) {
			this.playlistViewModel = playlistViewModel;
		}

		public int getCount() {
			return playlistViewModel.size();
		}

		public Object getItem(int position) {
			return playlistViewModel.get(position);
		}

		public long getItemId(int position) {
			return (long)playlistViewModel.get(position).id;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View v;
            if (convertView == null) {
                v = View.inflate(getBaseContext(), R.layout.song_item, null);
                ViewHolder vh = new ViewHolder();
                vh.songTextView = (TextView) v.findViewById(R.id.songitem_name);
                vh.artistTextView = (TextView) v.findViewById(R.id.songitem_artist);
                v.setTag(vh);
            } else {
                v = convertView;
            }
            ViewHolder vh = (ViewHolder)v.getTag();
            vh.artistTextView.setText(playlistViewModel.get(position).artist);
            vh.songTextView.setText(playlistViewModel.get(position).song);
            return v;
		}
		
		
		private class ViewHolder {

			TextView songTextView, artistTextView;
			
		}
	}
	
	private class PlaylistCursorAdapter extends CursorAdapter
	{
		public PlaylistCursorAdapter(Context context, Cursor cursor) 
		{
			super(context, cursor);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			TextView artistView = (TextView)view.findViewById(R.id.songitem_artist);
			TextView titleView = (TextView)view.findViewById(R.id.songitem_name);
			artistView.setText(cursor.getString(cursor.getColumnIndex("artist")));
			titleView.setText(cursor.getString(cursor.getColumnIndex("title")));
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			final LayoutInflater inflater = LayoutInflater.from(context);
			final View view = inflater.inflate(R.layout.song_item, parent, false);
			TextView artistView = (TextView)view.findViewById(R.id.songitem_artist);
			TextView titleView = (TextView)view.findViewById(R.id.songitem_name);
			artistView.setText(cursor.getString(cursor.getColumnIndex("artist")));
			titleView.setText(cursor.getString(cursor.getColumnIndex("title")));
			return view;
		}
	}
	
	private class LoadPlaylistTask extends AsyncTask<PlaylistRepository, Void, PlaylistItemAdapter> {

		@Override
		protected PlaylistItemAdapter doInBackground(PlaylistRepository... params) {
			PlaylistRepository repo = null;
			if(params == null || params.length < 1) {
				throw new IllegalArgumentException("Needs exactly one playlist repository");
			}
			repo = params[0];
			List<PlaylistItem> playlistViewModel = repo.getCurrentListViewModel(getApplicationContext()); 
			return new PlaylistItemAdapter(playlistViewModel);
		}
		
		@Override
		protected void onPostExecute(PlaylistItemAdapter adapter) {
//			BaseAdapter cursorAdapter = PlaylistEditor.this.createTestCursorAdapter();
//			PlaylistEditor.this.setContentView(playlistEditorView);
//			playlistView.setAdapter(cursorAdapter);
			PlaylistEditor.this.setContentView(playlistEditorView);
			Cursor cursor = PlaylistRepository.getInstance().getCursorForPlaylist();
			SimpleCursorAdapter scAdapter = new SimpleCursorAdapter(
					PlaylistEditor.this, 
					R.layout.song_item,
					cursor,
					new String[] { "title", "artist" },
					new int[] { R.id.songitem_name, R.id.songitem_artist });
			playlistView.setAdapter(new PlaylistCursorAdapter(PlaylistEditor.this.getApplicationContext(), cursor));
		}
	}
}
