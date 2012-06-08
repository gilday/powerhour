/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

/**
 * @author Amir
 *         http://android-journey.blogspot.com/2010/01/for-almost-any-application
 *         -we-need-to.html
 * 
 */
public class RangePreference extends Preference implements
		OnSeekBarChangeListener {

	public static int maximum = 100;
	public static int interval = 1;

	private float oldValue = 50;
	private TextView monitorBox;

	public RangePreference(Context context) {
		super(context);
		init();
	}

	public RangePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public RangePreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	public void init(){
		this.setLayoutResource(R.layout.rangepreference);
	}
	
	@Override
	protected void onBindView(View v) {
		super.onBindView(v);
		
		monitorBox = (TextView) v.findViewById(R.id.RangePreferenceMonitor);
		TextView titleView = (TextView) v.findViewById(R.id.RangePreferenceTitle);
		titleView.setText(getTitle());
		TextView summaryView = (TextView) v.findViewById(R.id.RangePreferenceSummary);
		summaryView.setText(getSummary());
		SeekBar seekBar = (SeekBar)v.findViewById(R.id.RangePreferenceSeekBar);
		seekBar.setOnSeekBarChangeListener(this);
		seekBar.setMax(maximum);
		seekBar.setProgress((int) this.oldValue);
		monitorBox.setText(seekBar.getProgress() + "%");
	}

	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {

		progress = Math.round(((float) progress) / interval) * interval;

		if (!callChangeListener(progress)) {
			seekBar.setProgress((int) this.oldValue);
			return;
		}

		seekBar.setProgress(progress);
		this.oldValue = progress;
		this.monitorBox.setText(progress + "%");
		updatePreference(progress);
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	@Override
	protected Object onGetDefaultValue(TypedArray ta, int index) {

		int dValue = ta.getInt(index, 50);

		return validateValue(dValue);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {

		int temp = restoreValue ? getPersistedInt(50) : (Integer) defaultValue;

		if (!restoreValue)
			persistInt(temp);

		this.oldValue = temp;
	}

	private int validateValue(int value) {

		if (value > maximum)
			value = maximum;
		else if (value < 0)
			value = 0;
		else if (value % interval != 0)
			value = Math.round(((float) value) / interval) * interval;

		return value;
	}

	private void updatePreference(int newValue) {

		SharedPreferences.Editor editor = getEditor();
		editor.putInt(getKey(), newValue);
		editor.commit();
	}

}