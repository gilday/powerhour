/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.IMusicUpdateListener;
import gilday.android.powerhour.IProgressUpdateListener;
import gilday.android.powerhour.MusicUpdateBroadcastReceiver;
import gilday.android.powerhour.MusicUtils;
import gilday.android.powerhour.PowerHourPreferences;
import gilday.android.powerhour.ProgressUpdateBroadcastReceiver;
import gilday.android.powerhour.R;
import gilday.android.powerhour.data.PreferenceRepository;
import gilday.android.powerhour.model.PlaylistItem;
import gilday.android.powerhour.service.IPowerHourService;
import gilday.android.powerhour.service.PowerHourService;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author John Gilday
 *
 */
public class NowPlayingActivity extends Activity implements IMusicUpdateListener, IProgressUpdateListener {
	// http://developer.android.com/guide/practices/design/performance.html#avoid_enums
	private static final String TAG = "NowPlaying";
	
	private IPowerHourService phService;	
	
	private TextView artistText, albumText, songText, minutesText;
	private ImageButton pauseButton;
	private ProgressBar pBar;
	//private RelativeLayout layout;
	private ImageView artView;
	private MusicUpdateBroadcastReceiver musicUpdateReceiver;
	private ProgressUpdateBroadcastReceiver progressUpdateReceiver;
	private Timer progressUpdateTimer;
	private final int progressTimerInterval = 500;
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		//Log.(TAG, "onCreate");
		setTheme(android.R.style.Theme_Black_NoTitleBar);
		
		setContentView(R.layout.nowplaying);
		
		// Volume buttons will control media stream
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		artistText = (TextView)findViewById(R.id.ArtistTitle);
		albumText = (TextView)findViewById(R.id.AlbumTitle);
		songText = (TextView)findViewById(R.id.SongTitle);
		artView = (ImageView)findViewById(R.id.ArtView);
		minutesText = (TextView)findViewById(R.id.MinutesText);
		pauseButton = (ImageButton)findViewById(R.id.PauseButton);
		minutesText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/COLLEGE.TTF"));
		pBar = (ProgressBar)findViewById(R.id.SongProgress);
		pBar.setMax(10000);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		// See if the service is running
		if(isPowerHourServiceRunning()) {
			// Bind to the started service.
			Intent bindServiceIntent = new Intent(this, PowerHourService.class);
			// Bind WITHOUT auto create set
		    if(!bindService(bindServiceIntent, phServiceConnection, 0)){
		    	Log.e(TAG, "Could not bind to service");
		    }
		} else {
    		// start activity to select the playlist
        	Intent getList = new Intent(getBaseContext(), TitleScreen.class);
        	getList.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        	startActivityForResult(getList, 0);
		}

	    // Register receivers
		musicUpdateReceiver = new MusicUpdateBroadcastReceiver(this);
		progressUpdateReceiver = new ProgressUpdateBroadcastReceiver(this);
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getApplicationContext());
		lbm.registerReceiver(musicUpdateReceiver, new IntentFilter(PowerHourService.MUSIC_UPDATE_BROADCAST));
		lbm.registerReceiver(progressUpdateReceiver, new IntentFilter(PowerHourService.PROGRESS_UPDATE_BROADCAST));
		lbm.registerReceiver(progressUpdateReceiver, new IntentFilter(PowerHourService.PROGRESS_PAUSE_RESUME_BROADCAST));
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		
		if(phService != null) {
			// Unbind from the service b/c this activity is not visible
			unbindService(phServiceConnection);
			phService = null;
		}
		// Unbind receivers
		musicUpdateReceiver.unRegisterUpdateListener();
		progressUpdateReceiver.unregisterUpdateListener();
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getApplicationContext());
		lbm.unregisterReceiver(musicUpdateReceiver);
		lbm.unregisterReceiver(progressUpdateReceiver);
		musicUpdateReceiver = null;
		progressUpdateReceiver = null;
		// Kill the local ProgressDisplayTimerTask
		if(progressUpdateTimer != null) {
			progressUpdateTimer.cancel();
			progressUpdateTimer.purge();
			progressUpdateTimer = null;
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_CANCELED){
			// Log.v(TAG, "Result is a cancel! Finish now");
			finish();
		}
		else{
			// Log.v(TAG, "Going to get the result and bind to service");
			Intent launchServiceIntent = new Intent(this, PowerHourService.class);
			// Send new intent to service
			startService(launchServiceIntent);
		}
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.nowplaying_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
    	super.onOptionsItemSelected(item);
    	switch(item.getItemId()){
    	case R.id.stopButton_menu:
			new AlertDialog.Builder(this)
            .setTitle(getString(R.string.quit_confirmation))
            .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                	// Unbind
            		if(phService != null) {
            			unbindService(phServiceConnection);
            			phService = null;
            		}
            		// Stop service
                	stopService(new Intent(getApplicationContext(), PowerHourService.class));
                	// Kill this activity
                	finish();
                }
            })
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show();
    		break;
    	case R.id.skipButton_menu:
    		skipClick(null);
    		break;
    	case R.id.settings:
        	Intent launchPreferencesIntent = new Intent().setClass(this, PowerHourPreferences.class);
            startActivity(launchPreferencesIntent);
            break;
    	}
        return true;
    }
    
    private boolean isPowerHourServiceRunning() {
    	ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    	for(RunningServiceInfo serviceInfo : activityManager.getRunningServices(Integer.MAX_VALUE)) {
    		if(serviceInfo.service.getClassName().equals(PowerHourService.class.getName())) {
    			return true;
    		}
    	}
    	return false;
    }
	
	private ServiceConnection phServiceConnection = new ServiceConnection() {
        
		public void onServiceConnected(ComponentName className, IBinder service) {
            phService = (IPowerHourService)service;
            int playing = phService.getPlayingState();
        	int secondsElapsed = phService.getProgress();
        	int milisecondsElapsed = secondsElapsed * 1000;
        	int nowplaying = phService.getCurrentSong();
        	// Q: Why do we have to check if these values are valid?
        	// A: Due to a race condition, the Service could be "playing"
        	//    but it could still be loading the first song. This will 
        	//    check if the player is attempting to play but not actually 
        	//    playing
        	if(secondsElapsed > 0 && nowplaying > 0){
        		//Log.d(TAG, "WARNING: minutesElapsed= " + minutesElapsed + " nowplaying= " + nowplaying);
        		// First call safeUpdateUI with both arguments to update progress bar
        		updateNowPlayingSongUI(nowplaying);
        		updateMinutesText(secondsElapsed / 60);
        		updateProgressBar(milisecondsElapsed);
        	}
        	// Update the pause button if the service is paused. 
        	// This happens when the phone receives a call and the service pauses itself
        	if(playing == PowerHourService.PAUSED) {
        		pauseButton.setImageResource(R.drawable.play);
        	} else {
        		pauseButton.setImageResource(R.drawable.pause);
        		progressUpdateTimer = new Timer();
        		progressUpdateTimer.schedule(new ProgressDisplayTimerTask(milisecondsElapsed), 0, progressTimerInterval);
        	}
        }

        public void onServiceDisconnected(ComponentName className) {
        	new AlertDialog.Builder(NowPlayingActivity.this)
	  	      .setMessage(getString(R.string.completed))
	  	      .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
	  	    	  public void onClick(DialogInterface inteface, int button){
	  	    		  finish();
	  	    	  }
	  	      })
  	      .show();
        }
    };
    
    public void pauseClick(View v) {
    	// Send pause. This Activity will receive a callback when the Power Hour's progression has 
    	// changed so will handle chaning the UI accordingly in those callbacks
		phService.pause();
    }
    
    public void skipClick(View v) {
		int nextID = phService.skip();
		if(nextID < 0){
			Toast.makeText(this, "Cannot skip last song", Toast.LENGTH_LONG).show();
		}
    }
    
    public void playlistClick(View v) {
    	Intent launchPlaylistEditor = new Intent().setClass(this, PlaylistEditor.class);
    	startActivity(launchPlaylistEditor);
    }
    
    void updateNowPlayingSongUI(int id){
    	PlaylistItem id3;
    	try 
    	{
    		id3 = MusicUtils.getInfoPack(this, id);
    	}
    	catch(Exception e) 
    	{
    		// Signify error condition by assigning null to id3 as if song info couldn't be found
    		id3 = null;
    	}
    	if(id3 == null)
    	{
    		Log.e("safeUpdateUI", "id3 is null!");
    		songText.setText("No track info");
    		albumText.setText("");
    		artistText.setText("");
    	}
    	else
    	{
			songText.setText(id3.song);
			albumText.setText(id3.album);
			artistText.setText(id3.artist);
			// Set the image art to the icon if there is no album art
			if(id3.art == null){
				artView.setImageResource(R.drawable.bigicon);
			}
			else{
				artView.setImageBitmap(id3.art);
			}
    	}
    }
    
    void updateMinutesText(int currentMinute) {
    	// Increase minute bc users don't like to count from 0
    	currentMinute++;
    	if(currentMinute >= 10){
    		minutesText.setText("" + currentMinute);
    	}
    	else{
    		minutesText.setText("0" + currentMinute);
    	}
    }
    
    void updateProgressBar(int miliseconds)
    {
    	// Update the progress bar 
    	// Update the main progress bar to refelct the total progress
    	int duration = new PreferenceRepository(this).getDuration() * 60000;
    	double ratio = (double) miliseconds / (double) duration;
    	pBar.setSecondaryProgress((int) (10000 * ratio));
    	// Update the secondary progress bar to reflect progress into the current minute
    	ratio = (double) (miliseconds % 60000) / (double) 60000;
    	pBar.setProgress((int) (ratio * 10000));
    }
    
	public void onSongUpdate(int songID) {
		updateNowPlayingSongUI(songID);
	}
	
	public void onProgressUpdate(int currentMinute) {
		if(progressUpdateTimer != null) {
			progressUpdateTimer.cancel();
			progressUpdateTimer.purge();
		}
		progressUpdateTimer = new Timer();
		progressUpdateTimer.schedule(new ProgressDisplayTimerTask(currentMinute * 60000), 0, progressTimerInterval);
		updateMinutesText(currentMinute);
	}
	
	@Override
	public void onProgressPaused() {
		// Cancel the intra-minute progress bar
		if(progressUpdateTimer != null) {
			progressUpdateTimer.cancel();
			progressUpdateTimer.purge();
		}
		// Set pause/play button icon to play
		pauseButton.setImageResource(R.drawable.play);
	}

	@Override
	public void onProgressResumed() {
		// Set pause/play button icon to pause
		pauseButton.setImageResource(R.drawable.pause);
		// Start a new intra-minute timer
		int milisecondsElapsed = phService.getProgress() * 1000;
		progressUpdateTimer = new Timer();
		progressUpdateTimer.schedule(new ProgressDisplayTimerTask(milisecondsElapsed), 0, progressTimerInterval);
	}
	
	private class ProgressDisplayTimerTask extends TimerTask
	{
		private int miliseconds;
		private Handler handler;
		
		public ProgressDisplayTimerTask(int miliseconds)
		{
			this.miliseconds = miliseconds;
			this.handler = new Handler();
		}
		
		@Override
		public void run()
		{
			miliseconds += progressTimerInterval;
			handler.post(new Runnable() {
				public void run() {
					NowPlayingActivity.this.updateProgressBar(miliseconds);
				}
			});
		}
	}
}
