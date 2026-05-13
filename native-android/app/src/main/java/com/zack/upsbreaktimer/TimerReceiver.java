package com.zack.upsbreaktimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TimerNotification.createChannels(context);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UPSBreakTimer:TimerAlarm");
        try {
            wl.acquire(10000);
            String action = intent != null ? intent.getAction() : "";
            if (TimerAlarms.ACTION_WARNING.equals(action)) {
                int warning = intent.getIntExtra("warning_minutes", 2);
                TimerNotification.showWarning(context, warning);
            } else if (TimerAlarms.ACTION_FINISH.equals(action)) {
                context.stopService(new Intent(context, TimerService.class));
                TimerNotification.showFinished(context);
            }
        } finally {
            if (wl.isHeld()) wl.release();
        }
    }
}
