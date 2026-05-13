package com.zack.upsbreaktimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class TimerAlarms {
    public static final String ACTION_WARNING = "com.zack.upsbreaktimer.WARNING";
    public static final String ACTION_FINISH = "com.zack.upsbreaktimer.FINISH";

    public static void scheduleWarning(Context context, long triggerAt, int warningMinutes) {
        Intent i = new Intent(context, TimerReceiver.class);
        i.setAction(ACTION_WARNING);
        i.putExtra("warning_minutes", warningMinutes);
        schedule(context, i, triggerAt, 201);
    }

    public static void scheduleFinish(Context context, long triggerAt) {
        Intent i = new Intent(context, TimerReceiver.class);
        i.setAction(ACTION_FINISH);
        schedule(context, i, triggerAt, 202);
    }

    private static void schedule(Context context, Intent intent, long triggerAt, int requestCode) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        else alarm.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }

    public static void cancelAll(Context context) {
        cancel(context, ACTION_WARNING, 201);
        cancel(context, ACTION_FINISH, 202);
    }

    private static void cancel(Context context, String action, int requestCode) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, TimerReceiver.class);
        i.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarm.cancel(pi);
        pi.cancel();
    }
}
