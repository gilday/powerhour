/**
 * 
 */
package gilday.android.powerhour.view.fragment;

import gilday.android.powerhour.data.PowerHour.NowPlaying;
import gilday.android.powerhour.view.PlaylistCursorAdapter;
import gilday.android.powerhour.view.PlaylistCursorAdapter.SongOmitHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

/**
 * @author jgilday
 *
 */
public class PlaylistListFragment extends ListFragment 
	implements LoaderManager.LoaderCallbacks<Cursor>, SongOmitHandler {
	
	private PlaylistCursorAdapter adapter;
	
	@Override
	public void onActivityCreated(Bundle savedState) {
		super.onActivityCreated(savedState);
		
		// Initialize Loader
		getLoaderManager().initLoader(0, null, this);
	}

	/**
	 * Called when this Fragment needs a new loader
	 * will load the remainder of the NowPlaying playlist (songs which have not been played yet)
	 */
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
		return new CursorLoader(
				getActivity(), 
				NowPlaying.CONTENT_URI, 
				new String[] { "_id", "title", "artist", "omit" },
				// Ask for songs which have not been played
				NowPlaying.PLAYED + " = 0",
				null, 
				NowPlaying.POSITION + " asc");
	}

	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		// if the list adapter has not been instantiated yet
		if(adapter == null) {
			Log.d("PlaylistFragment", "Loader retrieved " + cursor.getCount() + " songs");
			// Create and set the new adapter
			adapter = new PlaylistCursorAdapter(getActivity(), cursor, 0);
			adapter.setOmitToggleListener(this);
			setListAdapter(adapter);
		}
		else {
			// Swap in the new cursor
			adapter.swapCursor(cursor);
		}
	}

	public void onLoaderReset(Loader<Cursor> arg0) {
		// Cursor will be closed so flush it out of the adapter
		adapter.swapCursor(null);		
	}

	public void onSongOmissionChanged(int songId, boolean isOmitted) {
		int omit = isOmitted ? 1 : 0;
		ContentValues values = new ContentValues();
		values.put(NowPlaying.OMIT, omit);
		Uri updateUri = ContentUris.withAppendedId(NowPlaying.CONTENT_URI, songId);
		getActivity().getContentResolver().update(updateUri, values, null, null);
	}

}
