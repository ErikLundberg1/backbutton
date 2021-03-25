package se.kth.youeye;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;

public class ForegroundService extends LifecycleService {
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    private static final int FOREGROUND_ID = 1;
    private static final String CHANNEL_ID = "CHANNEL_1";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("EYE", "onStartCommand: SERVICE STARTED!");
        super.onStartCommand(intent, flags, startId);
        if (intent != null) { // This will select if the intent wants to start or turn of the foreground
            String action = intent.getAction();
            switch (action) {
                case ACTION_START_FOREGROUND_SERVICE:
                    createNotificationChannel();
                    startForeground(FOREGROUND_ID, createNotification("Running", intent));
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    stopForegroundService();
                    break;
            }
        }
        return START_STICKY;
    }


    //This method creates the notification channel that is required on higher android os version than oreo 8.0
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel("CHANNEL_1", "Foreground Notification", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("EYE", "onBind: SERVICE BOUND!");
        super.onBind(intent);
        return null;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        stopSelf();

        super.onDestroy();
    }

    // This method can be used to update the content of the notification
    private void updateNotification(Intent intent) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(FOREGROUND_ID, createNotification("Updated Notification", intent));
    }

    // This method creates the notification that will be displayed
    private Notification createNotification(String text, Intent intent) {
        Intent notificationIntent = new Intent(this, MainService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0); // PendingIntent affects what happens when you press the notification

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("YouEye Camera Service").setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pendingIntent).build();

        return notification;
    }

    // This method turns off the foreground
    private void stopForegroundService() {
        stopForeground(true);
        stopSelf();
    }
}