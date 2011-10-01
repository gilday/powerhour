/**
 * 
 */
package gilday.android.powerhour.model;

import gilday.android.powerhour.PowerHourPreferences;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * @author jgilday
 *
 */
public class PreferenceRepository {

	private final String defaultDuration = "60";
	private final boolean defaultShuffle = true;
	private final int defaultOffset = 0;
	private final boolean defaultUseAlert = true;
	private final boolean defaultUseArnold = true;
	private final String defaultAlertPath = "arnold";
	private final boolean defaultQuickLoad = true;
	
	private SharedPreferences prefs;
	
	public PreferenceRepository(Context appContext){
		prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
	}
	
	public int getDuration() {
		String durationString = prefs.getString(Keys.PREF_DURATION, defaultDuration);
		int duration;
		try { 
			duration = Integer.parseInt(durationString);
		} catch (NumberFormatException e){
			duration = 60;
		}
		// multiply by 60 to put songs in terms of seconds
		return duration * 60;
	}
	
	public boolean isShuffle() {
		return prefs.getBoolean(Keys.PREF_SHUFFLE, defaultShuffle);
	}
	
	public double getOffset() {
		int percent = prefs.getInt(Keys.PREF_OFFSET, defaultOffset);
		// Validation check
		if(percent > 99) {
			// default to 0
			percent = 0;
		}
		// Any negative number means use random
		if(percent < 0) {
			percent = PowerHourPreferences.RANDOM;
		}
		return (double)percent / 100;
	}
	
	public boolean getUseArnold() {
		return prefs.getBoolean(Keys.PREF_ARNOLD, defaultUseArnold);
	}
	
	public boolean getUseAlert() { 
		return prefs.getBoolean(Keys.PREF_USEALERT, defaultUseAlert);
	}
	
	public String getAlertPath() {
		return prefs.getString(Keys.PREF_ALERTPATH, defaultAlertPath);
	}

	public boolean getQuickLoad() {
		return prefs.getBoolean(Keys.PREF_QUICKLOAD, defaultQuickLoad);
	}
}
