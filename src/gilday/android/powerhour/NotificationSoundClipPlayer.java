package gilday.android.powerhour;

import java.io.FileDescriptor;
import java.io.IOException;

import gilday.android.powerhour.data.PreferenceRepository;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * Will catch advances in the Power Hour playlist and play the drink alert clip
 * @author Johnathan Gilday
 *
 */
public class NotificationSoundClipPlayer implements IProgressUpdateListener, IDisposable {

	private String TAG = "NotificationSoundClipPlayer";
	private Context applicationContext;
	private MediaPlayer soundClipPlayer;
	private final Object splayerLock = new Object();
	
	
	public NotificationSoundClipPlayer(Context context) {
		applicationContext = context;
		soundClipPlayer = new MediaPlayer();
	}
	
	public void onProgressUpdate(int currentMinute) {
		PreferenceRepository prefsRepo = new PreferenceRepository(applicationContext);
		boolean useAlert = prefsRepo.getUseAlert();
		// If preference has disabled useAlert, return here
		if(!useAlert)
			return;
		
		boolean useArnold = prefsRepo.getUseArnold();
		String alertPath = prefsRepo.getAlertPath();
		synchronized(splayerLock){
			soundClipPlayer.reset();
			try{
				if(useArnold || alertPath.equals("arnold")){
					FileDescriptor soundClip = applicationContext.getResources().openRawResourceFd(R.raw.doitnow).getFileDescriptor();
					soundClipPlayer.setDataSource(soundClip);
				} else {
					soundClipPlayer.setDataSource(alertPath);
				}
				soundClipPlayer.prepare();
				soundClipPlayer.start();
			} catch (IllegalStateException e) {
				Log.e(TAG, "Could not play sound clip");
				e.printStackTrace();
			} catch (IOException e) {
				String errorMessage;
				if(useArnold){
					errorMessage = "The default alert sound clip is missing? strange...";
				} else {
					errorMessage = "The alert sound clip is invalid. Change custom alert sound in preferences";
				}
				Log.e(TAG, errorMessage);
				e.printStackTrace();
			}
		}
	}
	
	public void dispose() {
		synchronized(splayerLock){
			soundClipPlayer.release();
			soundClipPlayer = null;
		}
	}

}
