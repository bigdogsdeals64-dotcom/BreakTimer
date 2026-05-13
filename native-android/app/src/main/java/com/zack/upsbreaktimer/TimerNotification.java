package com.zack.upsbreaktimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class TimerNotification {
    public static final int RUNNING_ID = 1001;
    public static final int WARNING_ID = 1002;
    public static final int FINISHED_ID = 1003;
    private static final String RUNNING_CHANNEL = "timer_running";
    private static final String ALARM_CHANNEL = "timer_alarm";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel running = new NotificationChannel(RUNNING_CHANNEL, "Running Timer", NotificationManager.IMPORTANCE_LOW);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel alarm = new NotificationChannel(ALARM_CHANNEL, "Timer Alerts", NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(alarmSound, attrs);
        alarm.enableVibration(true);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(running);
        nm.createNotificationChannel(alarm);
    }

    public static Notification runningNotification(Context context, long endAt, int minutes, int warningMinutes) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return builder(context, RUNNING_CHANNEL)
            .setSmallIcon(com.zack.upsbreaktimer.R.drawable.ic_timer)
            .setContentTitle("Break timer running")
            .setContentText(minutes + " minute break. Warning: " + warningMinutes + " min before end.")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build();
    }

    public static void showWarning(Context context, int warningMinutes) {
        Notification n = builder(context, ALARM_CHANNEL)
            .setSmallIcon(com.zack.upsbreaktimer.R.drawable.ic_timer)
            .setContentTitle("Break almost over")
            .setContentText(warningMinutes + " minutes remaining.")
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(WARNING_ID, n);
    }

    public static void showFinished(Context context) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(context, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = builder(context, ALARM_CHANNEL)
            .setSmallIcon(com.zack.upsbreaktimer.R.drawable.ic_timer)
            .setContentTitle("Break time is over")
            .setContentText("Return from break now.")
            .setContentIntent(openPi)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(FINISHED_ID, n);
    }

    public static void cancelAll(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(RUNNING_ID);
        nm.cancel(WARNING_ID);
        nm.cancel(FINISHED_ID);
    }

    private static Notification.Builder builder(Context context, String channel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new Notification.Builder(context, channel);
        return new Notification.Builder(context);
    }
}
