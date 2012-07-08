package gilday.android.powerhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * 
 * @author Johnathan Gilday
 *
 */
public class RemoteControlReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context content, Intent intent) {
		// TODO Auto-generated method stub
		if(Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			switch(event.getKeyCode()){
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				break;
			}
		}
	}

}
