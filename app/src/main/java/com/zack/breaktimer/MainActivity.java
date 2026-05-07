package com.zack.breaktimer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private WebView webView;
    private volatile boolean alarmPlaying = false;
    private byte[] alarmPcmBytes = null;
    private static final int ALARM_SAMPLE_RATE = 48000;

    public class AndroidBridge {
        @JavascriptInterface
        public void sendSms(String phoneNumbers, String message) {
            runOnUiThread(() -> openSms(phoneNumbers, message));
        }

        @JavascriptInterface
        public void playAlarmSound() {
            playAlarm();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        loadPcmAlarm();

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

    private void loadPcmAlarm() {
        try {
            InputStream inputStream = getAssets().open("alarm_pcm_48k.b64");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line.trim());
            }
            reader.close();
            alarmPcmBytes = Base64.decode(builder.toString(), Base64.DEFAULT);
        } catch (Exception e) {
            alarmPcmBytes = null;
        }
    }

    private short[] pcmBytesToShorts(byte[] bytes) {
        int sampleCount = bytes.length / 2;
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int low = bytes[i * 2] & 0xff;
            int high = bytes[i * 2 + 1];
            samples[i] = (short) ((high << 8) | low);
        }
        return samples;
    }

    private void playAlarm() {
        if (alarmPlaying) return;
        alarmPlaying = true;

        new Thread(() -> {
            AudioTrack track = null;
            try {
                short[] samples;
                if (alarmPcmBytes == null || alarmPcmBytes.length == 0) {
                    loadPcmAlarm();
                }
                if (alarmPcmBytes != null && alarmPcmBytes.length > 0) {
                    samples = pcmBytesToShorts(alarmPcmBytes);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Alarm audio file missing.", Toast.LENGTH_LONG).show());
                    return;
                }

                int minBuffer = AudioTrack.getMinBufferSize(
                        ALARM_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                track = new AudioTrack(
                        AudioManager.STREAM_ALARM,
                        ALARM_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuffer, samples.length * 2),
                        AudioTrack.MODE_STATIC
                );

                track.write(samples, 0, samples.length);
                track.setStereoVolume(1.0f, 1.0f);
                track.play();

                long durationMs = Math.max(500, (long) ((samples.length / (double) ALARM_SAMPLE_RATE) * 1000.0));
                Thread.sleep(durationMs + 250);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not play alarm.", Toast.LENGTH_LONG).show());
            } finally {
                if (track != null) {
                    try { track.stop(); } catch (Exception ignored) {}
                    try { track.release(); } catch (Exception ignored) {}
                }
                alarmPlaying = false;
            }
        }).start();
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
}
