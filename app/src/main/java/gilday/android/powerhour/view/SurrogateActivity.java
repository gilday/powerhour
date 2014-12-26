/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import gilday.android.powerhour.data.Keys;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * @author jgilday
 *
 */
public class SurrogateActivity extends Activity {
	
	private boolean isUnableToFindActivity;
	
	@Override
	public void onCreate(Bundle savedInstance){
		super.onCreate(savedInstance);
		Intent chooseFileIntent = new Intent();
		chooseFileIntent.setAction(Intent.ACTION_GET_CONTENT);
		// In my case I need an audio file path
		chooseFileIntent.setType("audio/*");
		try {
			startActivityForResult(chooseFileIntent, 0);
		} catch (ActivityNotFoundException e) {
			isUnableToFindActivity = true;
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_OK){
			 Uri audioPath = data.getData();
			 // Use SharedPreferences.Editor to update preference value
			 SharedPreferences.Editor prefsEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			 prefsEditor.putString(Keys.PREF_ALERTPATH, audioPath.toString());
			 prefsEditor.commit();
			finish();
		} else {
			// Check if there was an error finding an activity
			if(isUnableToFindActivity) {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.error_cannotPickAudio))
				.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				})
				.show();
			} else {
				// Perhaps user cancelled
				finish();
			}
		} 
	}
}