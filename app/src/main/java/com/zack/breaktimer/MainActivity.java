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
            InputStream inputStream = getAssets().open("alarm_pcm_16k.b64");
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

    private short[] makeEmergencyTone() {
        final int sampleRate = 16000;
        final double durationSeconds = 3.2;
        final int samples = (int) (sampleRate * durationSeconds);
        final int rampSamples = (int) (sampleRate * 0.025);
        short[] buffer = new short[samples];

        for (int i = 0; i < samples; i++) {
            double t = i / (double) sampleRate;
            double sweepA = 760.0 + 520.0 * (0.5 + 0.5 * Math.sin(2.0 * Math.PI * 2.15 * t));
            double sweepB = 1120.0 + 420.0 * (0.5 + 0.5 * Math.sin(2.0 * Math.PI * 3.05 * t));
            double pulse = (Math.sin(2.0 * Math.PI * 7.0 * t) > -0.15) ? 1.0 : 0.18;
            double wave = Math.sin(2.0 * Math.PI * sweepA * t) * 0.58;
            wave += Math.sin(2.0 * Math.PI * sweepB * t) * 0.28;
            wave += Math.sin(2.0 * Math.PI * 1880.0 * t) * 0.12;
            wave *= pulse;

            double envelope = 1.0;
            if (i < rampSamples) envelope = i / (double) rampSamples;
            if (samples - i < rampSamples) envelope = (samples - i) / (double) rampSamples;

            int value = (int) (wave * envelope * 30000.0);
            if (value > Short.MAX_VALUE) value = Short.MAX_VALUE;
            if (value < Short.MIN_VALUE) value = Short.MIN_VALUE;
            buffer[i] = (short) value;
        }
        return buffer;
    }

    private void playAlarm() {
        if (alarmPlaying) return;
        alarmPlaying = true;

        new Thread(() -> {
            AudioTrack track = null;
            try {
                final int sampleRate = 16000;
                short[] samples;
                if (alarmPcmBytes == null || alarmPcmBytes.length == 0) {
                    loadPcmAlarm();
                }
                if (alarmPcmBytes != null && alarmPcmBytes.length > 0) {
                    samples = pcmBytesToShorts(alarmPcmBytes);
                } else {
                    samples = makeEmergencyTone();
                }

                int minBuffer = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                track = new AudioTrack(
                        AudioManager.STREAM_ALARM,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuffer, samples.length * 2),
                        AudioTrack.MODE_STATIC
                );

                track.write(samples, 0, samples.length);
                track.setStereoVolume(1.0f, 1.0f);
                track.play();

                long durationMs = Math.max(500, (long) ((samples.length / (double) sampleRate) * 1000.0));
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
