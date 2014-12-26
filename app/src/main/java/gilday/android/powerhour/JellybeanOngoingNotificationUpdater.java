package gilday.android.powerhour;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import gilday.android.powerhour.model.PlaylistItem;
import gilday.android.powerhour.view.NowPlayingActivity;

/**
 * User: Johnathan Gilday
 * Date: 6/8/12
 */
public class JellybeanOngoingNotificationUpdater implements NotificationController {

    private NotificationManager notificationManager;
    private Context context;
    private NotificationCompat.Builder builder;
    private int notificationId = 1;

    private String songTitle;
    private String songArtist;
    private Bitmap coverArt;

    int currentMinute = 0;
    int notificationIconPixels; // Icon should be 64dp but we'll need to convert that to actual pixels

    public JellybeanOngoingNotificationUpdater(Context context) {
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent nowPlayingIntent = new Intent(context, NowPlayingActivity.class);

        builder =
                new NotificationCompat.Builder(context)
                	.setOngoing(true)
                    .setSmallIcon(R.drawable.jellybeerstatusbar)
                    .setContentIntent(PendingIntent.getActivity(context, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        // Get screen density
        final float scale = context.getResources().getDisplayMetrics().density;
        // convert to dp to pixels to determine notification icon size
        notificationIconPixels = (int) Math.ceil(64 * scale + 0.5f);
    }

    @Override
    public void onSongUpdate(int songID) {
        PlaylistItem songInfo = MusicUtils.getInfoPack(context, songID, false);

        songTitle = songInfo.song;
        songArtist = songInfo.artist;
        Bitmap bitmap = MusicUtils.getArtwork(context, songInfo.albumId, 2);
        if(bitmap == null) {
        	coverArt = null;
        } else {
        	coverArt = Bitmap.createScaledBitmap(bitmap, notificationIconPixels, notificationIconPixels, false);
        }

        updateNotificationContent();
    }

    @Override
    public void onProgressUpdate(int currentMinute) {
        this.currentMinute = ++currentMinute;
        updateNotificationContent();
    }

    @Override
    public void dispose() {
        notificationManager.cancel(notificationId);
    }

	@Override
	public void onProgressPaused() {
		// Do nothing. Will use this when play / pause controls are in the notification area
	}

	@Override
	public void onProgressResumed() {
		// Do nothing. Will use this when play / pause controls are in the notification area
	}

    private void updateNotificationContent() {
        builder.setContentTitle(songTitle);
        builder.setContentText(songArtist);
        builder.setContentInfo(String.format(context.getString(R.string.notification_drink), this.currentMinute));
        builder.setLargeIcon(coverArt);
        notificationManager.notify(notificationId, builder.build());
    }
}
