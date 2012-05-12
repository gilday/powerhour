package gilday.android.powerhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MusicUpdateBroadcastReceiver extends BroadcastReceiver {
	
	private IMusicUpdateListener listener;
	
	public void registerUpdateListener(IMusicUpdateListener listener) {
		this.listener = listener;
	}
	
	public void unRegisterUpdateListener() {
		this.listener = null;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if(listener != null) {
			int songId = intent.getExtras().getInt(PowerHourService.SONGID);
			int seconds = intent.getExtras().getInt(PowerHourService.SECONDS);
			listener.onSongUpdate(songId, seconds);
		}
	}

}
