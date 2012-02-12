/**
 * 
 */
package gilday.android.powerhour;

import gilday.android.powerhour.data.Keys;
import gilday.android.powerhour.view.AudioFilePreference;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

/**
 * @author John Gilday
 *
 */
public class PowerHourPreferences extends PreferenceActivity {
	/**
	 * The value that specifies a preference for random offsets into the song
	 */
	public static int RANDOM = -1;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		// Register a listener that disables the offset slider when use random offset is checked
		CheckBoxPreference randomOffsetPref = (CheckBoxPreference) this.findPreference(Keys.PREF_RANDOMOFFSET);
		final Preference offsetPref = this.findPreference(Keys.PREF_OFFSET);
		randomOffsetPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				offsetPref.setEnabled(!((Boolean)newValue));
				return true;
			}});
		// initialize offset slider preference based on random offset's current setting
		offsetPref.setEnabled(!randomOffsetPref.isChecked());
	}
	
	@Override
	public void onStart(){
		super.onStart();
		AudioFilePreference afPref = (AudioFilePreference)this.findPreference(Keys.PREF_ALERTPATH);
		afPref.updateUI();
	}
}
