package gilday.android.powerhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ProgressUpdateBroadcastReceiver extends BroadcastReceiver {

	private IProgressUpdateListener listener;
	
	public void registerUpdateListener(IProgressUpdateListener listener)
	{
		this.listener = listener;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if(listener != null) {
			int minute = intent.getExtras().getInt(PowerHourService.PROGRESS);
			listener.onProgressUpdate(minute);
		}
	}

}
