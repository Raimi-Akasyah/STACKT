package com.example.stackt;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    // Existing monitoring channel (DO NOT CHANGE - monitoring code uses this)
    private static final String CHANNEL_ID = "monitoring_requests";
    private static final String CHANNEL_NAME = "Monitoring Requests";
    private static final String CHANNEL_DESC = "Notifications for monitoring requests from other users";

    // NEW: Proximity notifications channel
    private static final String PROXIMITY_CHANNEL_ID = "proximity_alerts";
    private static final String PROXIMITY_CHANNEL_NAME = "Store Proximity Alerts";
    private static final String PROXIMITY_CHANNEL_DESC = "Notifications when you're near stores";

    /** Display a notification (existing method - DO NOT CHANGE) */
    public static void displayNotification(Context context, String title, String message) {
        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        int notificationId = (int) System.currentTimeMillis();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.notify(notificationId, builder.build());
    }

    /** NEW: Display proximity notification with custom styling */
    public static void displayProximityNotification(Context context, String title, String message) {
        createProximityNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PROXIMITY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500}); // Optional vibration pattern

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        int notificationId = (int) System.currentTimeMillis();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.notify(notificationId, builder.build());
    }

    /** Create notification channel for Android 8+ (existing - DO NOT CHANGE) */
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /** NEW: Create proximity notification channel for Android 8+ */
    private static void createProximityNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    PROXIMITY_CHANNEL_ID,
                    PROXIMITY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(PROXIMITY_CHANNEL_DESC);
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}