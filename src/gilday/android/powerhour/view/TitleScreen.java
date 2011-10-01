package gilday.android.powerhour.view;

import gilday.android.powerhour.PowerHourPreferences;
import gilday.android.powerhour.R;
import gilday.android.powerhour.model.InitializePlaylistTask;
import gilday.android.powerhour.model.Keys;
import gilday.android.powerhour.model.PlaylistRepository;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
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

	private static final String TAG = "Get a Playlist";
	private Button doitnow, startPlayList;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView tv = (TextView)findViewById(R.id.main_title);
        tv.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/COLLEGE.TTF"));
        
        doitnow = (Button)findViewById(R.id.playNowButton);
        startPlayList = (Button)findViewById(R.id.selectPlaylistButton);
        
        doitnow.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    startFromRandom();
                }
            });
        
        startPlayList.setOnClickListener(new OnClickListener(){
        	public void onClick(View v){
        		startFromPlaylist();
        	}
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settingsmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	launchPreferences();
        return true;
    }
    
    @Override
    public void onConfigurationChanged(Configuration confg)
    {
    	super.onConfigurationChanged(confg);
    	// do nothing
    }

	private void launchPreferences() {
        Intent launchPreferencesIntent = new Intent().setClass(this, PowerHourPreferences.class);
        startActivity(launchPreferencesIntent);
	}
	
	private void startFromPlaylist(){
		// Android didn't finish the damn PlaylistBrowserActivity. The PICK action 
		// does not supply a URI. This method is ideal, so leave commented code here 
		// until Google pushes an update. 2.0?
		/*
		Intent i = new Intent(Intent.ACTION_PICK);
		i.setType(MediaStore.Audio.Playlists.CONTENT_TYPE);
		startActivityForResult(i, PLAYLIST);
		*/
		Intent i = new Intent(this, MyPlaylistBrowserActivity.class);
		startActivityForResult(i, 0);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(resultCode == RESULT_CANCELED){
			Log.v(TAG, "Result is a cancel! Finish now");
		}
		else{
			// Get playlist ID from returning activity
			int id = data.getIntExtra(Keys.PLAYLIST_ID_KEY, 0);
			// Set PlaylistRepository
			PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
			playlistRepo.init(getApplicationContext());
			try {
				MyInitializePlaylistTask task = new MyInitializePlaylistTask(this, id);
				task.execute((Void[])null);
			} catch (IllegalArgumentException ie) {
				// So there were no songs in that playlist and there was an exception thrown
				Toast.makeText(this, "This playlist is empty! Cannot start Power Hour", Toast.LENGTH_SHORT).show();
				return;
			}
		}
	}
	
	private void startFromRandom(){
		// TODO: Set shuffle preference to true if this is truely "start from random"
		// Tell PlaylistRepository to set the playlist to all songs
		PlaylistRepository playlistRepo = PlaylistRepository.getInstance();
		playlistRepo.init(getApplicationContext());
		try {
			MyInitializePlaylistTask task = new MyInitializePlaylistTask(this);
			task.execute((Void[])null);
		} catch (IllegalArgumentException ie) {
			// So there's apparently no songs on the SD card
			Log.e(TAG, ie.getMessage());
			Toast.makeText(this, "There are no songs on your SD card! Cannot start Power Hour.", Toast.LENGTH_SHORT).show();
			return;
		}
	}
	
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
		public void onPostExecute(Void result) {
			progressDialog.dismiss();
			Intent respondIntent = new Intent();
			TitleScreen.this.setResult(RESULT_OK, respondIntent);
			TitleScreen.this.finish();
		}
		
	}
}