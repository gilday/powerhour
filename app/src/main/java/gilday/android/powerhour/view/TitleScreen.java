package gilday.android.powerhour.view;

import gilday.android.powerhour.PowerHourPreferences;
import gilday.android.powerhour.R;
import gilday.android.powerhour.data.InitializePlaylistTask;
import gilday.android.powerhour.data.Keys;
import gilday.android.powerhour.data.PreferenceRepository;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class TitleScreen extends Activity {

	private static final String TAG = "TitleScreenActivity";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView tv = (TextView)findViewById(R.id.main_title);
        if(tv != null)
        	tv.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/COLLEGE.TTF"));

        Button playNowButton = (Button) findViewById(R.id.playNowButton);
        Button startPlayListButton = (Button) findViewById(R.id.selectPlaylistButton);
        
        playNowButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                startFromRandom();
            }
        });
        
        startPlayListButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                startFromPlaylist();
            }
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.title_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	// There is only one option on this menu and it's the settings 
    	// list. Launch preferences
        Intent launchPreferencesIntent = new Intent().setClass(this, PowerHourPreferences.class);
        startActivity(launchPreferencesIntent);
        return true;
    }
    
    @Override
    public void onConfigurationChanged(Configuration confg)
    {
    	super.onConfigurationChanged(confg);
    	// do nothing. Prevents the Activity from dying and coming back when the 
    	// screen is rotated
    }

	private void startFromPlaylist(){
		/*
		 * TODO
		 * Android didn't finish the damn PlaylistBrowserActivity. The PICK action 
		 * does not supply a URI. This method is ideal, so leave commented code here 
		 * until Google fixes. Bug # 958
		 */
		
		/*
		Intent i = new Intent(Intent.ACTION_PICK);
		i.setType(MediaStore.Audio.Playlists.CONTENT_TYPE);
		startActivityForResult(i, 1);
		*/
		
		Intent i = new Intent(this, MyPlaylistBrowserActivity.class);
		startActivityForResult(i, 0);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(resultCode == RESULT_CANCELED){
			Log.v(TAG, "Result is a cancel! Do nothing, wait for user to do something useful");
		}
		else{
			// Get playlist ID from returning activity
			int id = data.getIntExtra(Keys.PLAYLIST_ID_KEY, 0);
			try {
				MyInitializePlaylistTask task = new MyInitializePlaylistTask(this, id);
				task.execute((Void[])null);
			} catch (IllegalStateException ie) {
				// So there were no songs in that playlist and so there was an exception thrown
				displayEmptyPlaylistError(getString(R.string.error_noSongsOnPlaylist));
			}
		}
	}
	
	private void startFromRandom(){
		// TODO: Set shuffle preference to true if this is truely "start from random"
		try {
			MyInitializePlaylistTask task = new MyInitializePlaylistTask(this);
			task.execute((Void[])null);
		} catch (IllegalStateException ie) {
			// Could not find any songs so there was an exception thrown
			displayEmptyPlaylistError(getString(R.string.error_noSongsOnDevice));
		}
	}
	
	private void displayEmptyPlaylistError(String message) {
		// So there's apparently no songs on the SD card
    	new AlertDialog.Builder(this)
  	      .setMessage(message)
  	      .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
  	    	  public void onClick(DialogInterface inteface, int button){
  	    		  // Do nothing, just want user to acknowledge
  	    	  }
  	      })
  	      .show();
	}
	
	/**
	 * Initializes the Power Hour playlist with either all the songs in the Android 
	 * MediaStore or a specific playlist. This implementation of InitializePlaylistTask 
	 * reports progress to the user with a ProgressDialog. When the playlist is done 
	 * loading, this TitleScreen Activity sets result and finishes
	 * @author jgilday
	 *
	 */
	private class MyInitializePlaylistTask extends InitializePlaylistTask 
	{
		private ProgressDialog progressDialog;

		public MyInitializePlaylistTask(Context context) {
			super(context);
		}
		
		public MyInitializePlaylistTask(Context context, int playlistId) {
			super(context, playlistId);
		}
		
		@Override
		public void onPreExecute() {
			progressDialog = new ProgressDialog(context);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage("Reading playlist...");
			progressDialog.setMax(songsToImportCount);
			progressDialog.setCancelable(false);
			progressDialog.show();
		}
		
		@Override
		public void onProgressUpdate(Void... params) {
			progressDialog.incrementProgressBy(reportInterval);
		}
		
		@Override
		public void onPostExecute(Integer importedSongsCount) {
			progressDialog.dismiss();
			Intent respondIntent = new Intent();
			// if importedSongsCount < duration ...
			int duration = new PreferenceRepository(context).getDuration();
			if(importedSongsCount < duration) {
				// ... then report that the power hour will be short
				String message = "You have added only " + importedSongsCount + " songs. Your Power Hour is going to be a little short";
				Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
			}
			TitleScreen.this.setResult(RESULT_OK, respondIntent);
			TitleScreen.this.finish();
		}
		
	}
}