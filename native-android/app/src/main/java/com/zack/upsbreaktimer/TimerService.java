package com.zack.upsbreaktimer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class TimerService extends Service {
    public static final String EXTRA_END_AT = "end_at";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String EXTRA_WARNING_MINUTES = "warning_minutes";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long endAt = intent != null ? intent.getLongExtra(EXTRA_END_AT, System.currentTimeMillis()) : System.currentTimeMillis();
        int minutes = intent != null ? intent.getIntExtra(EXTRA_MINUTES, 10) : 10;
        int warning = intent != null ? intent.getIntExtra(EXTRA_WARNING_MINUTES, 2) : 2;
        startForeground(TimerNotification.RUNNING_ID, TimerNotification.runningNotification(this, endAt, minutes, warning));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
