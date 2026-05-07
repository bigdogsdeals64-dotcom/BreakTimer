package com.zack.breaktimer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;
    private volatile boolean alarmPlaying = false;
    private static final int SAMPLE_RATE = 44100;

    public class AndroidBridge {
        @JavascriptInterface
        public void sendSms(String phoneNumbers, String message) {
            runOnUiThread(() -> openSms(phoneNumbers, message));
        }

        @JavascriptInterface
        public void playAlarmSound(String style) {
            playAlarm(style == null ? "siren" : style);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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

    private void playAlarm(String style) {
        if (alarmPlaying) return;
        alarmPlaying = true;

        new Thread(() -> {
            AudioTrack track = null;
            try {
                short[] samples = buildTone(style);
                int minBuffer = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                track = new AudioTrack(
                        AudioManager.STREAM_ALARM,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuffer, samples.length * 2),
                        AudioTrack.MODE_STATIC
                );

                track.write(samples, 0, samples.length);
                track.setVolume(1.0f);
                track.play();

                long durationMs = Math.max(500, (long) ((samples.length / (double) SAMPLE_RATE) * 1000.0));
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

    private short[] buildTone(String style) {
        switch (style) {
            case "pulse": return makePulseTone();
            case "fast": return makeFastBeepTone();
            case "warble": return makeWarbleTone();
            case "triple": return makeTripleTone();
            case "deep": return makeDeepHornTone();
            case "digital": return makeDigitalTone();
            case "siren":
            default: return makeSirenTone();
        }
    }

    private short[] makeSirenTone() {
        int samples = (int) (SAMPLE_RATE * 3.5);
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 650 + 650 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 1.35 * t));
            double pulse = (Math.sin(2 * Math.PI * 5.0 * t) > -0.35) ? 1.0 : 0.28;
            double wave = Math.sin(2 * Math.PI * f * t) * 0.72 + Math.sin(2 * Math.PI * (f * 1.52) * t) * 0.22;
            buffer[i] = shapedSample(wave, pulse, i, samples);
        }
        return buffer;
    }

    private short[] makePulseTone() {
        int samples = (int) (SAMPLE_RATE * 3.2);
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double on = (t % 0.42) < 0.24 ? 1.0 : 0.0;
            double wave = Math.sin(2 * Math.PI * 980 * t) * 0.75 + Math.sin(2 * Math.PI * 1470 * t) * 0.20;
            buffer[i] = shapedSample(wave, on, i, samples);
        }
        return buffer;
    }

    private short[] makeFastBeepTone() {
        int samples = (int) (SAMPLE_RATE * 3.0);
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double on = (t % 0.20) < 0.105 ? 1.0 : 0.0;
            double freq = ((int)(t / 0.20) % 2 == 0) ? 1300 : 930;
            double wave = Math.sin(2 * Math.PI * freq * t);
            buffer[i] = shapedSample(wave, on, i, samples);
        }
        return buffer;
    }

    private short[] makeWarbleTone() {
        int samples = (int) (SAMPLE_RATE * 3.6);
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double freq = (Math.sin(2 * Math.PI * 11.0 * t) > 0) ? 740 : 1040;
            double wave = Math.sin(2 * Math.PI * freq * t) * 0.82;
            buffer[i] = shapedSample(wave, 1.0, i, samples);
        }
        return buffer;
    }

    private short[] makeTripleTone() {
        int samples = (int) (SAMPLE_RATE * 3.4);
        short[] buffer = new short[samples];
        double[] freqs = { 880, 1175, 1568 };
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double cycle = t % 0.72;
            double on = cycle < 0.16 || (cycle > 0.24 && cycle < 0.40) || (cycle > 0.48 && cycle < 0.64) ? 1.0 : 0.0;
            int index = cycle < 0.24 ? 0 : cycle < 0.48 ? 1 : 2;
            double wave = Math.sin(2 * Math.PI * freqs[index] * t) * 0.82;
            buffer[i] = shapedSample(wave, on, i, samples);
        }
        return buffer;
    }

    private short[] makeDeepHornTone() {
        int samples = (int) (SAMPLE_RATE * 3.6);
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double pulse = (t % 0.85) < 0.55 ? 1.0 : 0.0;
            double wave = Math.sin(2 * Math.PI * 320 * t) * 0.68 + Math.sin(2 * Math.PI * 640 * t) * 0.24 + Math.sin(2 * Math.PI * 960 * t) * 0.12;
            buffer[i] = shapedSample(wave, pulse, i, samples);
        }
        return buffer;
    }

    private short[] makeDigitalTone() {
        int samples = (int) (SAMPLE_RATE * 3.2);
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            int step = (int)(t / 0.08) % 8;
            double freq = 650 + step * 120;
            double on = (t % 0.16) < 0.11 ? 1.0 : 0.0;
            double wave = Math.signum(Math.sin(2 * Math.PI * freq * t)) * 0.62;
            buffer[i] = shapedSample(wave, on, i, samples);
        }
        return buffer;
    }

    private short shapedSample(double wave, double gate, int i, int samples) {
        int ramp = (int) (SAMPLE_RATE * 0.018);
        double envelope = 1.0;
        if (i < ramp) envelope = i / (double) ramp;
        if (samples - i < ramp) envelope = (samples - i) / (double) ramp;
        double value = wave * gate * envelope * 30000.0;
        if (value > Short.MAX_VALUE) value = Short.MAX_VALUE;
        if (value < Short.MIN_VALUE) value = Short.MIN_VALUE;
        return (short) value;
    }

    private boolean isSmsUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("mms:") || lower.startsWith("mmsto:");
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
