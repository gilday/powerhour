package gilday.android.powerhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.LinkedList;

public class ProgressUpdateBroadcastReceiver extends BroadcastReceiver {

	private LinkedList<IProgressUpdateListener> listeners = new LinkedList<IProgressUpdateListener>();
	
	public ProgressUpdateBroadcastReceiver(IProgressUpdateListener... listener)
    {
        listeners = new LinkedList<IProgressUpdateListener>();
        for(IProgressUpdateListener l : listener) {
            listeners.add(l);
        }
    }
	
	public void unregisterUpdateListener() {
        for(IProgressUpdateListener listener : listeners){
            if(listener instanceof IDisposable) {
                ((IDisposable) listener).dispose();
            }
        }
		listeners = null;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
        int minute = intent.getExtras().getInt(PowerHourService.PROGRESS);
        for(IProgressUpdateListener listener : listeners) {
            listener.onProgressUpdate(minute);
        }
	}

}
