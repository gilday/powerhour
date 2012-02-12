/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * Cursor adapter which binds a Cursor for the Power Hour database to the playlist 
 * edit list. Exposes an event which signifies when the user wishes to toggle the 
 * omission property for a song item in the playlist
 * @author jgilday
 *
 */
public class PlaylistCursorAdapter extends CursorAdapter {
	
	private SongOmitHandler listener;
	/**
	 * Holds the single instance of the OmitButtonHandler. Re-using 
	 * the same OmitToggleHandler instance saves on performance.
	 */
	private OmitButtonHandler internalButtonHandler;
	
	public PlaylistCursorAdapter(Context context, Cursor cursor) 
	{
		super(context, cursor);
		internalButtonHandler = new OmitButtonHandler();
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		ViewHolder vh = (ViewHolder) view.getTag();
		bindViewHolder(vh, cursor);
	}

	@Override
	public View newView(Context context, final Cursor cursor, ViewGroup parent) {
		// Inflate the views
		final LayoutInflater inflater = LayoutInflater.from(context);
		final View view = inflater.inflate(R.layout.song_item, parent, false);
		// Create a viewholder to hold references to the views 
		ViewHolder vh = new ViewHolder();
		vh.artistView = (TextView)view.findViewById(R.id.songitem_artist);
		vh.titleView = (TextView)view.findViewById(R.id.songitem_name);
		vh.omitToggleButton = (ToggleButton)view.findViewById(R.id.omit_toggle);
		view.setTag(vh);
		// Use helper function to bind cursor data to the views
		bindViewHolder(vh, cursor);
		// Clear out listeners in case they're still hanging around
		vh.omitToggleButton.setOnCheckedChangeListener(null);
		// Register this listener
		vh.omitToggleButton.setOnCheckedChangeListener(internalButtonHandler);
		return view;
	}
	
	public void setOmitToggleListener(SongOmitHandler listener) {
		this.listener = listener;
	}
	
	private void bindViewHolder(ViewHolder vh, Cursor cursor) {
		vh.artistView.setText(cursor.getString(cursor.getColumnIndex("artist")));
		vh.titleView.setText(cursor.getString(cursor.getColumnIndex("title")));
		int omit = cursor.getInt(cursor.getColumnIndex("omit"));
		// Put the song ID in the toggle button's Tag reference
		// This will help the onCheckedChanged event snag the song ID that it is relevant to
		vh.omitToggleButton.setTag(cursor.getInt(cursor.getColumnIndex("_id")));
		vh.omitToggleButton.setChecked(omit > 0);
	}
	
	/**
	 * The PlaylistCursorAdapter fires an event when a song is omitted from or marked for 
	 * re-inclusion in the now playing playlist. 
	 * @author jgilday
	 *
	 */
	interface SongOmitHandler
	{
		void onSongOmissionChanged(int songId, boolean isOmitted);
	}
	
	/**
	 * Using this private class to catch the CompoundButton.OnCheckedChangeListener 
	 * to handle this View based event internally and without having to expose a 
	 * public CompoundButton.OnCheckedChangeListener on PlaylistCursorAdapter
	 * Better encapsulation of UI
	 * @author jgilday
	 *
	 */
	private class OmitButtonHandler implements CompoundButton.OnCheckedChangeListener
	{
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			// Forward this event on to a the OmitToggleListener in a way that 
			// keeps all the View stuff encapsulated here
			if(listener != null) {
				int songId = (Integer) buttonView.getTag();
				listener.onSongOmissionChanged(songId, isChecked);
			}			
		}
	}
	
	/**
	 * View Holder Android pattern. Caches pointers to Views that make up each list 
	 * item view
	 * @author jgilday
	 *
	 */
	private class ViewHolder
	{
		public TextView artistView;
		public TextView titleView;
		public ToggleButton omitToggleButton;
	}
}
