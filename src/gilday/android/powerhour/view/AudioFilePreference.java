/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.preference.Preference;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * @author Johnathan Gilday
 *
 */
public class AudioFilePreference extends Preference {

	public AudioFilePreference(Context context) {
		super(context);
		init();
	}
	
	public AudioFilePreference(Context context, AttributeSet attrSet){
		super(context, attrSet);
		init();
	}
	
	public AudioFilePreference(Context context, AttributeSet attrSet, int defStyle){
		super(context, attrSet, defStyle);
		init();
	}
	
	private void init(){
		// Inflate layout
		this.setLayoutResource(R.layout.audiopreference);
	}
	
	/**
	 * Could NOT figure out a way to catch the event that occurs when this shared preference's 
	 * value is changed. The event I would like to catch only fires when the user changes the 
	 * preference. In this case, this class relies on another class (SurrogateActivity) to 
	 * get and set the preference value; therefore, the event I need never fires. To get around 
	 * this, the PowerHourPreferences class will call this updateUI method in its onStart. This 
	 * works because we know the onStart method is called after the picker dialog finishes and 
	 * returns to the PowerHourPreferences activity.  
	 */
	public void updateUI(){
		if(fileNameTextView != null){
			SharedPreferences sharedPreferences = this.getSharedPreferences();
			String storedPreference = sharedPreferences.getString(getKey(), null);
			if(storedPreference.equals("arnold")) {
				fileNameTextView.setText("default");
				return;
			}
			Uri contentUri = Uri.parse(storedPreference);
			try{
				fileNameTextView.setText(getRealPathFromURI(contentUri));
			} catch(Exception e) {
				fileNameTextView.setText("Could not resolve file path");
				String exceptionMessage = e.getMessage();
				if(exceptionMessage == null) {
					exceptionMessage = "Null pointer";
				}
				Log.e("PowerHour", exceptionMessage);
			}
		}
	}
	
	/**
	 * Helper to turn android's weird content uri into a file path
	 * @param contentUri
	 * @return file path string
	 */
	private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Audio.Media.DATA };
        ContentResolver resolver = getContext().getContentResolver();
        Cursor cursor = resolver.query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

	@Override
	protected void onClick(){
		super.onClick();
		Intent launchHiddenActivity = new Intent(getContext(), SurrogateActivity.class);
		getContext().startActivity(launchHiddenActivity);
	}
	
	private TextView fileNameTextView;
	@Override
	protected void onBindView(View v){
		super.onBindView(v);
		TextView title = (TextView) v.findViewById(R.id.AudioPrefTitle);
		title.setText(getTitle());
		fileNameTextView = (TextView) v.findViewById(R.id.CustomAudioFileName);
		// Update the fileName with the proper format by calling updateUI
		updateUI();
	}
	
	@Override
	protected Object onGetDefaultValue(TypedArray ta, int index) {

        return ta.getString(index);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {

		String temp = restoreValue ? getPersistedString("arnold") : (String) defaultValue;

		if (!restoreValue)
			persistString(temp);
	}
}
