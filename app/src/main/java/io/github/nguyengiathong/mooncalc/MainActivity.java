package io.github.nguyengiathong.mooncalc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Hosts the MoonCalc page (assets/www) in a full-screen WebView.
 *
 * The page is served from https://appassets.androidplatform.net/ (the host reserved for this by
 * WebView) rather than file://, so it runs in a secure context with its own origin for
 * localStorage. Nothing is fetched from the network.
 *
 * The page's navigator.geolocation works once the user grants the location permission, which is
 * asked for the first time the page wants a position ("Định vị").
 */
public class MainActivity extends Activity {
    private static final String TAG = "MoonCalc";
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int REQUEST_LOCATION = 1;

    private WebView web;
    private ClipboardManager clipboard;
    private LocationManager locationManager;

    // a geolocation request from the page, waiting for the user to answer the permission dialog
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clipboard = getSystemService(ClipboardManager.class);
        locationManager = getSystemService(LocationManager.class);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.night));
        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.night));
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        keepClearOfSystemBars(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // saved location lives in localStorage
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);        // the map has its own pinch zoom
        s.setTextZoom(100);
        s.setGeolocationEnabled(true);

        web.addJavascriptInterface(new Bridge(), "MoonCalcAndroid");
        web.setWebViewClient(new AssetClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.println(m.messageLevel() == ConsoleMessage.MessageLevel.ERROR ? Log.ERROR : Log.DEBUG,
                        TAG, m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                // retain=false: the page asks again next time, so revoking the permission takes effect
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }
                if (pendingGeoCallback != null) pendingGeoCallback.invoke(pendingGeoOrigin, false, false);
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                // asking for both lets the user choose precise or approximate (Android 12+);
                // approximate is plenty for the Moon
                requestPermissions(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION }, REQUEST_LOCATION);
            }
        });
        web.loadUrl(START_URL);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQUEST_LOCATION || pendingGeoCallback == null) return;
        pendingGeoCallback.invoke(pendingGeoOrigin, hasLocationPermission(), false);
        pendingGeoCallback = null;
        pendingGeoOrigin = null;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        if (locationManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return locationManager.isLocationEnabled();
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * Pads the content by the status bar, navigation bar, display cutout and keyboard. Android 15+
     * always draws apps edge to edge, and there adjustResize no longer shrinks the window for the
     * keyboard, so both are handled here. On older versions the system has already consumed
     * these insets and the padding stays zero.
     */
    private void keepClearOfSystemBars(FrameLayout root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
    }

    @Override
    protected void onPause() {
        // stops the page's clock and "play" timers while the app is in the background
        web.onPause();
        web.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    /** Serves https://appassets.androidplatform.net/<path> from assets/www/<path>. */
    private final class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri url = req.getUrl();
            if (!"https".equals(url.getScheme()) || !HOST.equals(url.getHost())) return null;
            String path = url.getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            if (path.contains("..")) return notFound();
            try {
                InputStream in = getAssets().open("www" + path);
                String mime = mimeType(path);
                return new WebResourceResponse(mime, mime.startsWith("font/") ? null : "utf-8", in);
            } catch (IOException e) {
                return notFound();
            }
        }

        /** Links to anything outside the app open in the browser instead of replacing the page. */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri url = req.getUrl();
            if (HOST.equals(url.getHost())) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, url));
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No app to open " + url);
            }
            return true;
        }

        private WebResourceResponse notFound() {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null,
                    new ByteArrayInputStream(new byte[0]));
        }

        private String mimeType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "text/javascript";
            if (path.endsWith(".woff2")) return "font/woff2";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".png")) return "image/png";
            return "application/octet-stream";
        }
    }

    /** Exposed to the page as window.MoonCalcAndroid. */
    private final class Bridge {
        @JavascriptInterface
        public boolean copyText(String text) {
            if (clipboard == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("MoonCalc", text));
            return true;
        }

        @JavascriptInterface
        public boolean hasLocationPermission() {
            return MainActivity.this.hasLocationPermission();
        }

        /** Whether the device's location setting is on at all. */
        @JavascriptInterface
        public boolean isLocationEnabled() {
            return MainActivity.this.isLocationEnabled();
        }

        /** "location" opens the device's location settings, "app" this app's settings (permissions). */
        @JavascriptInterface
        public void openSettings(String which) {
            Intent i = "location".equals(which)
                    ? new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
            try {
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No settings screen for " + which);
            }
        }
    }
}
