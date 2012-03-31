/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

/**
 * Simply sets a layout and lets the PlaylistListFragment do all the work
 * @author Johnathan Gilday
 *
 */
public class PlaylistEditor extends FragmentActivity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setTitle(getString(R.string.PlaylistEditorTitle));

		setContentView(R.layout.playlist_editor);
	}
}
