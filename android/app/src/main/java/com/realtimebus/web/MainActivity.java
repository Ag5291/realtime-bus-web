package com.realtimebus.web;

import android.Manifest;
import android.app.Activity;
import android.content.res.AssetManager;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST_CODE = 1001;
    private static final String APP_HOST = "apph5.ibuscloud.com";
    private static final String APP_TEST_HOST = "apph5-test.ibuscloud.com";
    private static final String HOME_URL =
        "https://" + APP_HOST + "/index.html?curPage=realTimeListPage&city=140300&appSource=com.ibuscloud.yangquanbus&pageTitle=%E5%AE%9E%E6%97%B6%E5%85%AC%E4%BA%A4";

    private WebView webView;
    private String pendingGeolocationOrigin;
    private GeolocationPermissions.Callback pendingGeolocationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.setWebViewClient(new AppHostWebViewClient());
        webView.setWebChromeClient(new GeoChromeClient());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST_CODE || pendingGeolocationCallback == null) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        pendingGeolocationCallback.invoke(pendingGeolocationOrigin, granted, false);
        pendingGeolocationOrigin = null;
        pendingGeolocationCallback = null;
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isAppHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return APP_HOST.equals(normalized) || APP_TEST_HOST.equals(normalized);
    }

    private static String normalizeAssetPath(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "index.html";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String guessMimeType(String path) {
        String mimeType = URLConnection.guessContentTypeFromName(path);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private final class AppHostWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null || !isAppHost(request.getUrl())) {
                return super.shouldInterceptRequest(view, request);
            }
            if (!"GET".equalsIgnoreCase(request.getMethod())) {
                return super.shouldInterceptRequest(view, request);
            }

            String assetPath = normalizeAssetPath(request.getUrl());
            AssetManager assetManager = getAssets();
            try {
                InputStream stream = assetManager.open("site/" + assetPath);
                return new WebResourceResponse(guessMimeType(assetPath), null, stream);
            } catch (IOException notFound) {
                return super.shouldInterceptRequest(view, request);
            }
        }
    }

    private final class GeoChromeClient extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (hasLocationPermission()) {
                callback.invoke(origin, true, false);
                return;
            }
            pendingGeolocationOrigin = origin;
            pendingGeolocationCallback = callback;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                    new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQUEST_CODE
                );
            } else {
                callback.invoke(origin, false, false);
            }
        }
    }
}
