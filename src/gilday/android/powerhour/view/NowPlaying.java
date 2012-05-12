/**
 * 
 */
package gilday.android.powerhour.view;

import gilday.android.powerhour.IMusicUpdateListener;
import gilday.android.powerhour.IPowerHourService;
import gilday.android.powerhour.MusicUpdateBroadcastReceiver;
import gilday.android.powerhour.MusicUtils;
import gilday.android.powerhour.PowerHourPreferences;
import gilday.android.powerhour.PowerHourService;
import gilday.android.powerhour.R;
import gilday.android.powerhour.data.PlaylistRepository;
import gilday.android.powerhour.data.PreferenceRepository;
import gilday.android.powerhour.model.PlaylistItem;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
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
public class NowPlaying extends Activity implements IMusicUpdateListener{
	// http://developer.android.com/guide/practices/design/performance.html#avoid_enums
	private static final String TAG = "NowPlaying";
	
	private IPowerHourService phService;	
	
	private TextView artistText, albumText, songText, minutesText;
	private ImageButton pauseButton;
	private ProgressBar pBar;
	//private RelativeLayout layout;
	private ImageView artView;
	private MusicUpdateBroadcastReceiver updateReceiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		//Log.(TAG, "onCreate");
		setTheme(android.R.style.Theme_Black_NoTitleBar);
		
		setContentView(R.layout.nowplaying);
		
		artistText = (TextView)findViewById(R.id.ArtistTitle);
		albumText = (TextView)findViewById(R.id.AlbumTitle);
		songText = (TextView)findViewById(R.id.SongTitle);
		artView = (ImageView)findViewById(R.id.ArtView);
		minutesText = (TextView)findViewById(R.id.MinutesText);
		pauseButton = (ImageButton)findViewById(R.id.PauseButton);
		minutesText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/COLLEGE.TTF"));
		pBar = (ProgressBar)findViewById(R.id.SongProgress);
		pBar.setMax(10000);
		
		// Start the service explicitly. Doesn't matter if it's already running but this ensures
		// this it is and that it is not bound to the lifecycle of this activity
		Intent launchServiceIntent = new Intent(this, PowerHourService.class);
		startService(launchServiceIntent);
	}
	
	@Override
	protected void onStart(){
		super.onStart();
		// Bind to the started service.
		Intent bindServiceIntent = new Intent(this, PowerHourService.class);
		// Bind WITHOUT auto create set
	    if(!bindService(bindServiceIntent, rpcConnection, 0)){
	    	Log.e(TAG, "Could not bind to service");
	    }
	    else{
	    	//Log.d(TAG, "onStart binded to service");
	    }
	    // Register receiver
		updateReceiver = new MusicUpdateBroadcastReceiver();
		updateReceiver.registerUpdateListener(this);
		LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(updateReceiver, new IntentFilter(PowerHourService.UPDATE_BROADCAST));
	}
	
	@Override
	protected void onStop(){
		super.onStop();
		//Log.d(TAG, "onStop");
		// Unbind from the service b/c this activity is not visible
		// Hopefully unbinding will save memory and this activity can 
		// just rebind onStart()
		phService = null;
		// unbind service
		//Log.d(TAG, "onSTOP unbind phService");
		unbindService(rpcConnection);
		// Unbind receiver
		updateReceiver.unRegisterUpdateListener();
		LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(updateReceiver);
		updateReceiver = null;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_CANCELED){
			//Log.v(TAG, "Result is a cancel! Finish now");
			finish();
		}
		else{
			//Log.d(TAG, "Going to get the result and bind to service");
			PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
			if(playlistRepo.getPlaylistSize() > 0){
				Intent launchServiceIntent = new Intent(this, PowerHourService.class);
				// Send new intent
				//Log.d(TAG, "Send list to service");
				startService(launchServiceIntent);
			}
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
            .setTitle("Are you sure you want to end power hour?")
            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
            		try {
            			phService.stop();
        			} catch (RemoteException e) {
        				Log.e(TAG, "Could not stop the service, it must be dead already");
        				e.printStackTrace();
        			}
                	restartApp();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    		break;
    	case R.id.skipButton_menu:
    		skipClick(null);
//    		try{
//    			int nextID = phService.skip();
//    			if(nextID < 0){
//    				Toast.makeText(this, "Cannot skip song. PowerHour will not have enough songs to finish", Toast.LENGTH_LONG).show();
//    			}
//    			else{
//    				updateUI(nextID);
//    			}
//    		}
//    		catch(RemoteException e){
//    			Log.e(TAG, "RemoteException when trying to skip");
//    			e.printStackTrace();
//    		}
    		break;
    	case R.id.settings:
    		launchSettings();
            break;
    	}
        return true;
    }
	
	private ServiceConnection rpcConnection = new ServiceConnection() {
        
		public void onServiceConnected(ComponentName className, IBinder service) {
            phService = IPowerHourService.Stub.asInterface(service);
            try {
                // Cache the playing state in this process to avoid more remote calls
                int playing = phService.getPlayingState();
                if(playing != PowerHourService.NOT_STARTED){
                	//Log.d(TAG, "Binded to service. PH Started, get progress");
                	int secondsElapsed = phService.getProgress();
                	int nowplaying = PlaylistRepository.getInstance().getCurrentSong();
                	// Q: Why do we have to check if these values are valid?
                	// A: Due to a race condition, the Service could be "playing"
                	// but it could still be loading the first song. This will 
                	// check if the player is attempting to play but not actually 
                	// playing
                	if(secondsElapsed > 0 && nowplaying > 0){
                		//Log.d(TAG, "WARNING: minutesElapsed= " + minutesElapsed + " nowplaying= " + nowplaying);
                		// First call safeUpdateUI with both arguments to update progress bar
                		updateUI(secondsElapsed, nowplaying);
                	}
                	// Update the pause button if the service is paused. 
                	// This happens when the phone receives a call and the service pauses itself
                	if(playing == PowerHourService.PAUSED) {
                		pauseButton.setImageResource(R.drawable.play);
                	} else {
                		pauseButton.setImageResource(R.drawable.pause);
                	}
                }
                else{
                	// If playlist is not set...
                	if(PlaylistRepository.getInstance().getPlaylistSize() <= 0){
                		// start activity to select the playlist
                    	Intent getList = new Intent(getBaseContext(), TitleScreen.class);
                    	getList.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    	startActivityForResult(getList, 0);
                	}
                }
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        public void onServiceDisconnected(ComponentName className) {
        	if(phService != null){
	            // We have not nulled out phService so the process has disconnected 
	        	// unexpectadely. Show an error
        		// TODO: Throw up an error
	            phService = null;
	            Log.e(TAG, "The PowerHourService has exited unexpectadely");
	            finish();
        	}
        	//Log.d(TAG, "phService disconnected");
        }
    };
    
    private void restartApp(){
    	phService = null;
		Intent restart = new Intent(this, NowPlaying.class);
		startActivity(restart);
		finish();
    }
    
    public void pauseClick(View v) {
    	try{
    		phService.pause();
    		if(phService.getPlayingState() != PowerHourService.PLAYING){
    			pauseButton.setImageResource(R.drawable.play);
    		} else {
    			pauseButton.setImageResource(R.drawable.pause);
    		}
    	}
    	catch(RemoteException e){
    		Log.e(TAG, "RemoteException while trying to pause");
    	}
    }
    
    public void skipClick(View v) {
		try{
			int nextID = phService.skip();
			if(nextID < 0){
				Toast.makeText(this, "Cannot skip last song", Toast.LENGTH_LONG).show();
			}
			else{
				updateUI(nextID);
			}
		}
		catch(RemoteException e){
			Log.e(TAG, "RemoteException when trying to skip");
			e.printStackTrace();
		}
    }
    
    public void playlistClick(View v) {
    	Intent launchPlaylistEditor = new Intent().setClass(this, PlaylistEditor.class);
    	startActivity(launchPlaylistEditor);
    }
    
    public void launchSettings() {
    	Intent launchPreferencesIntent = new Intent().setClass(this, PowerHourPreferences.class);
        startActivity(launchPreferencesIntent);
    }
    
    void updateUI(int id){
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
    
    public void settingsClick(View v){
    	launchSettings();
    }
    
    void updateUI(int seconds, int id){
    	// If service sent the complete sentinel, end the power hour client
    	if(id == PowerHourService.COMPLETE_SENTINEL){
    		new AlertDialog.Builder(this)
    	      .setMessage(getString(R.string.completed))
    	      .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
    	    	  public void onClick(DialogInterface inteface, int button){
    	    		  try {
						phService.stop();
    	    		  } catch (RemoteException e) {
						e.printStackTrace();
						Log.e(TAG, "Cannot reach service to stop. We can trust that the service has stopped itself. Restart app");
    	    		  }
    	    		  restartApp();
    	    	  }
    	      })
    	      .show();
    		return;
    	}
    	
    	// Update the progress bar to reflect the passing second
    	// Update the main progress bar to refelct the total progress
    	int duration = new PreferenceRepository(this).getDuration();
    	double ratio = (double) seconds / (double) duration;
    	pBar.setSecondaryProgress((int) (10000 * ratio));
    	// Update the secondary progress bar to reflect progress into the current minute
    	ratio = (double) ((seconds + 1) % 60) / (double) 60;
    	pBar.setProgress((int) (ratio * 10000));
    	
    	// Calculate the minutes that have passed
    	int minutes = (seconds / 60) + 1;
    	if(minutes >= 10){
    		minutesText.setText("" + minutes);
    	}
    	else{
    		minutesText.setText("0" + minutes);
    	}
    	updateUI(id);
    }

	public void onSongUpdate(int songID, int seconds) {
		updateUI(seconds, songID);
	}
}
