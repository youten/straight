
package youten.redo.smartextension.straight;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

public class Notify {
    // http://developer.android.com/wear/notifications/creating.html
    public static final int ID = 001;

    public static void notify(Context context, String title, String text) {
        PendingIntent pi = PendingIntent.getActivity(context, 0, new Intent(), 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi);

        // TODO: NotificationManagerCompatは22.6以上にしてから考える。
        //        NotificationManagerCompat notificationManager =
        //                NotificationManagerCompat.from(this);
        NotificationManager manager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(ID, builder.build());
    }
}
