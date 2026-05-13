package com.zack.upsbreaktimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private NumberPicker minutePicker;
    private NumberPicker warningPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimerNotification.createChannels(this);
        requestNotificationPermission();
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(0xFF0F0F0F);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("UPS Break Timer");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, fullWrap());

        TextView sub = new TextView(this);
        sub.setText("Lock-screen alerts like a regular timer.");
        sub.setTextColor(0xFFBBBBBB);
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, fullWrap());

        status = new TextView(this);
        status.setText("Set your break time and press Start.");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(18), dp(16), dp(18));
        root.addView(status, fullWrap());

        minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(1);
        minutePicker.setMaxValue(60);
        minutePicker.setValue(10);
        minutePicker.setWrapSelectorWheel(false);
        root.addView(label("Break Length - Minutes"), fullWrap());
        root.addView(minutePicker, fullWrap());

        warningPicker = new NumberPicker(this);
        warningPicker.setMinValue(0);
        warningPicker.setMaxValue(10);
        warningPicker.setValue(2);
        warningPicker.setWrapSelectorWheel(false);
        root.addView(label("Warning Before End - Minutes"), fullWrap());
        root.addView(warningPicker, fullWrap());

        Button start10 = button("Start 10 Minute Break");
        start10.setOnClickListener(v -> startTimer(10, warningPicker.getValue()));
        root.addView(start10, fullWrap());

        Button startCustom = button("Start Custom Timer");
        startCustom.setOnClickListener(v -> startTimer(minutePicker.getValue(), warningPicker.getValue()));
        root.addView(startCustom, fullWrap());

        Button stop = button("Stop Timer");
        stop.setOnClickListener(v -> stopTimer());
        root.addView(stop, fullWrap());

        Button exactSettings = button("Exact Alarm Permission");
        exactSettings.setOnClickListener(v -> openExactAlarmSettings());
        root.addView(exactSettings, fullWrap());

        setContentView(root);
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(0xFFB8860B);
        v.setTextSize(15);
        v.setTypeface(null, 1);
        v.setPadding(0, dp(12), 0, dp(6));
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setAllCaps(false);
        return b;
    }

    private void startTimer(int minutes, int warningMinutes) {
        if (!canScheduleExactAlarms()) {
            Toast.makeText(this, "Allow Exact Alarm permission for reliable alerts.", Toast.LENGTH_LONG).show();
            openExactAlarmSettings();
            return;
        }
        long now = System.currentTimeMillis();
        long endAt = now + minutes * 60000L;
        long warnAt = warningMinutes > 0 ? endAt - warningMinutes * 60000L : 0;

        Intent service = new Intent(this, TimerService.class);
        service.putExtra(TimerService.EXTRA_END_AT, endAt);
        service.putExtra(TimerService.EXTRA_MINUTES, minutes);
        service.putExtra(TimerService.EXTRA_WARNING_MINUTES, warningMinutes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service); else startService(service);

        TimerAlarms.scheduleFinish(this, endAt);
        if (warnAt > now) TimerAlarms.scheduleWarning(this, warnAt, warningMinutes);
        status.setText("Timer running: " + minutes + " minutes. You can lock the phone.");
    }

    private void stopTimer() {
        stopService(new Intent(this, TimerService.class));
        TimerAlarms.cancelAll(this);
        TimerNotification.cancelAll(this);
        status.setText("Timer stopped.");
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        return alarmManager.canScheduleExactAlarms();
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private LinearLayout.LayoutParams fullWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        return lp;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
