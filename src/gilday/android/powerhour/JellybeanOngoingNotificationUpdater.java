package gilday.android.powerhour;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
    int currentMinute = 0;

    public JellybeanOngoingNotificationUpdater(Context context) {
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent nowPlayingIntent = new Intent(context, NowPlayingActivity.class);

        builder =
                new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.jellybeerstatusbar)
                        .setOngoing(true)
                        .setContentIntent(PendingIntent.getActivity(context, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Override
    public void onSongUpdate(int songID) {
        PlaylistItem songInfo = MusicUtils.getInfoPack(context, songID, false);

        songTitle = songInfo.song;
        songArtist = songInfo.artist;
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
        notificationManager.notify(notificationId, builder.build());
    }
}
