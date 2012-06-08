package gilday.android.powerhour;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import gilday.android.powerhour.model.PlaylistItem;
import gilday.android.powerhour.view.NowPlayingActivity;

/**
 * User: Johnathan Gilday
 * Date: 6/8/12
 */
public class OngoingNotificationUpdater implements IMusicUpdateListener, IProgressUpdateListener, IDisposable {

    private NotificationManager notificationManager;
    private Context context;
    private Notification notification;

    public OngoingNotificationUpdater(Context context) {
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notification = new Notification();
        notification.icon = R.drawable.beerstatusbar;
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        Intent nowPlayingIntent = new Intent(context, NowPlayingActivity.class);
        notification.contentIntent = PendingIntent.getActivity(context, 0, nowPlayingIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notification.contentView = new RemoteViews(context.getPackageName(), R.layout.custom_notification_layout);
    }

    @Override
    public void onSongUpdate(int songID) {
        PlaylistItem songInfo = MusicUtils.getInfoPack(context, songID, false);
        notification.contentView.setTextViewText(R.id.notificationSongTitle, songInfo.song);
        notification.contentView.setTextViewText(R.id.notificationArtist, songInfo.artist);
        notificationManager.notify(R.layout.custom_notification_layout, notification);
    }

    @Override
    public void onProgressUpdate(int currentMinute) {
        notification.contentView.setTextViewText(R.id.notificationDrink, context.getString(R.string.notification_drink) + ": " + currentMinute + 1);
        notificationManager.notify(R.layout.custom_notification_layout, notification);
    }

    @Override
    public void dispose() {
        notificationManager.cancel(R.layout.custom_notification_layout);
    }
}
