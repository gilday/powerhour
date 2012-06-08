package gilday.android.powerhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.LinkedList;

public class MusicUpdateBroadcastReceiver extends BroadcastReceiver {
	
	private LinkedList<IMusicUpdateListener> listeners;
	
	public MusicUpdateBroadcastReceiver(IMusicUpdateListener... listener) {
		listeners = new LinkedList<IMusicUpdateListener>();
        for(IMusicUpdateListener l : listener) {
            listeners.add(l);
        }
	}
	
	public void unRegisterUpdateListener() {
        for(IMusicUpdateListener listener : listeners){
            if(listener instanceof IDisposable) {
                ((IDisposable) listener).dispose();
            }
        }
		listeners = null;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
        int songId = intent.getExtras().getInt(PowerHourService.SONGID);
        for(IMusicUpdateListener listener : listeners) {
            listener.onSongUpdate(songId);
        }
    }
}
