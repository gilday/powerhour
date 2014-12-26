package gilday.android.powerhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import gilday.android.powerhour.service.PowerHourService;

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
		if(intent.getAction().equals(PowerHourService.PROGRESS_UPDATE_BROADCAST)){
	        int minute = intent.getExtras().getInt(PowerHourService.PROGRESS);
	        for(IProgressUpdateListener listener : listeners) {
	            listener.onProgressUpdate(minute);
	        }
	        return;
		}
		if(intent.getAction().equals(PowerHourService.PROGRESS_PAUSE_RESUME_BROADCAST)){
			if(intent.getExtras().getBoolean(PowerHourService.IS_PAUSED))
				for(IProgressUpdateListener listener : listeners)
					listener.onProgressPaused();
			else 
				for(IProgressUpdateListener listener : listeners)
					listener.onProgressResumed();
		}
	}

}
