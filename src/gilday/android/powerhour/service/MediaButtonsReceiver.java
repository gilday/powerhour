package gilday.android.powerhour.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class MediaButtonsReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		if(Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			KeyEvent keyEvent = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			// Intent is fired for KeyEvent.ACTION_DOWN and KeyEvent.ACTION_UP so only 
			// respond to one 
			if(keyEvent.getAction() == KeyEvent.ACTION_UP) {
				switch(keyEvent.getKeyCode()) {
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
					context.startService(new Intent(PowerHourService.ACTION_PLAY_PAUSE));
					break;
				case KeyEvent.KEYCODE_MEDIA_NEXT:
					context.startService(new Intent(PowerHourService.ACTION_SKIP));
				}
			}
		}
	}

}
