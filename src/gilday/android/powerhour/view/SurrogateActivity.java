/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.model.Keys;
import android.app.Activity;
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
	
	@Override
	public void onCreate(Bundle savedInstance){
		super.onCreate(savedInstance);
		Intent chooseFileIntent = new Intent();
		chooseFileIntent.setAction(Intent.ACTION_GET_CONTENT);
		// In my case I need an audio file path
		chooseFileIntent.setType("audio/*");
		startActivityForResult(chooseFileIntent, 0);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_OK){
			 Uri audioPath = data.getData();
			 // Use SharedPreferences.Editor to update preference value
			 SharedPreferences.Editor prefsEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			 prefsEditor.putString(Keys.PREF_ALERTPATH, audioPath.toString());
			 prefsEditor.commit();
		}
		// finish this "hidden" activity on any result
		finish();
	}
}