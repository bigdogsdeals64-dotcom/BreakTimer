package com.zack.breaktimer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private WebView webView;
    private MediaPlayer mediaPlayer;
    private File alarmFile;

    public class AndroidBridge {
        @JavascriptInterface
        public void sendSms(String phoneNumbers, String message) {
            runOnUiThread(() -> openSms(phoneNumbers, message));
        }

        @JavascriptInterface
        public void playAlarmSound() {
            runOnUiThread(() -> playAlarm());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prepareAlarmFile();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isSmsUrl(url)) {
                    openSmsUrl(url);
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isSmsUrl(url)) {
                    openSmsUrl(url);
                    return true;
                }
                return false;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void prepareAlarmFile() {
        try {
            InputStream inputStream = getAssets().open("alarm2.b64");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line.trim());
            }
            reader.close();

            byte[] audioBytes = Base64.decode(builder.toString(), Base64.DEFAULT);
            alarmFile = new File(getCacheDir(), "break_timer_alarm.mp3");
            FileOutputStream outputStream = new FileOutputStream(alarmFile);
            outputStream.write(audioBytes);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            alarmFile = null;
        }
    }

    private void playAlarm() {
        try {
            if (alarmFile == null || !alarmFile.exists()) {
                prepareAlarmFile();
            }

            if (alarmFile == null || !alarmFile.exists()) {
                Toast.makeText(this, "Custom alarm file not found.", Toast.LENGTH_LONG).show();
                return;
            }

            if (mediaPlayer != null) {
                try {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(alarmFile.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(mp -> {
                try {
                    mp.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            });
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Toast.makeText(this, "Could not play custom alarm.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isSmsUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("sms:") || lower.startsWith("smsto:") ||
               lower.startsWith("mms:") || lower.startsWith("mmsto:");
    }

    private void openSmsUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No text messaging app found.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open text message.", Toast.LENGTH_LONG).show();
        }
    }

    private void openSms(String phoneNumbers, String message) {
        try {
            String phones = phoneNumbers == null ? "" : phoneNumbers.trim();
            String body = message == null ? "" : message;

            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + phones));
            intent.putExtra("sms_body", body);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No text messaging app found.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open text message.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
