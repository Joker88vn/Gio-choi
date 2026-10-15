package vn.daopham.amlich;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = isSystemDark();
        applyBars(dark);

        web = new WebView(this);
        web.setBackgroundColor(dark ? 0xFF12100E : 0xFFF5EFE6);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/web/index.html" + hashOf(getIntent()));

        Notifier.ensureChannels(this);
        AlarmReceiver.schedule(this);
        Notifier.updateOngoing(this);
        Widgets.updateAll(this);
    }

    private String hashOf(Intent i) {
        if (i == null) return "";
        String d = i.getStringExtra("d"), t = i.getStringExtra("t");
        if (d == null && t == null) return "";
        StringBuilder sb = new StringBuilder("#");
        if (d != null) sb.append("d=").append(d);
        if (t != null) sb.append(sb.length() > 1 ? "&" : "").append("t=").append(t);
        return sb.toString();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ("vn.daopham.amlich.PINNED".equals(intent.getAction())) {
            Widgets.updateAll(this);
            if (web != null) web.evaluateJavascript("window.App&&App.pinned()", null);
            return;
        }
        String h = hashOf(intent);
        if (!h.isEmpty() && web != null) web.evaluateJavascript("window.App&&App.handleHash('" + h.substring(1) + "')", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Hộp thoại thêm widget đã hiện → huỷ hướng dẫn dự phòng
        if (web != null) web.evaluateJavascript("window.App&&App.cancelPin&&App.cancelPin()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.App&&App.onResume&&App.onResume()", null);
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("window.App?App.back():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Notifier.updateOngoing(this);
    }

    private boolean isSystemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("deprecation")
    private void applyBars(boolean dark) {
        Window w = getWindow();
        w.setStatusBarColor(dark ? 0xFF12100E : 0xFFF5EFE6);
        w.setNavigationBarColor(dark ? 0xFF1C1916 : 0xFFFFFCF7);
        View decor = w.getDecorView();
        int f = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(dark ? (f & ~light) : (f | light));
    }

    private class Bridge {
        @JavascriptInterface
        public String getData(String key) { return Store.get(MainActivity.this, key); }

        @JavascriptInterface
        public void setData(String key, String json) {
            Store.put(MainActivity.this, key, json);
            runOnUiThread(() -> {
                try {
                    if ("settings".equals(key)) {
                        AlarmReceiver.schedule(MainActivity.this);
                        Notifier.updateOngoing(MainActivity.this);
                    }
                    Widgets.updateAll(MainActivity.this);
                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public boolean isSystemDark() { return MainActivity.this.isSystemDark(); }

        @JavascriptInterface
        public void setBars(boolean dark) {
            runOnUiThread(() -> {
                applyBars(dark);
                web.setBackgroundColor(dark ? 0xFF12100E : 0xFFF5EFE6);
            });
        }

        @JavascriptInterface
        public void requestNotif() {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7));
            }
        }

        @JavascriptInterface
        public boolean pinWidget(String type) {
            Class<?> cls;
            switch (type) {
                case "detail": cls = WidgetDetail.class; break;
                case "month": cls = WidgetMonth.class; break;
                case "tet": cls = WidgetTet.class; break;
                case "line": cls = WidgetLine.class; break;
                default: cls = WidgetDay.class;
            }
            AppWidgetManager m = AppWidgetManager.getInstance(MainActivity.this);
            if (m == null || !m.isRequestPinAppWidgetSupported()) return false;
            final Class<?> target = cls;
            runOnUiThread(() -> {
                try {
                    Intent cb = new Intent(MainActivity.this, MainActivity.class)
                            .setAction("vn.daopham.amlich.PINNED")
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 77, cb, fl);
                    boolean ok = m.requestPinAppWidget(new ComponentName(MainActivity.this, target), null, pi);
                    if (!ok) web.evaluateJavascript("window.App&&App.pinFailed()", null);
                } catch (Exception e) {
                    web.evaluateJavascript("window.App&&App.pinFailed()", null);
                }
            });
            return true;
        }

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }
}
