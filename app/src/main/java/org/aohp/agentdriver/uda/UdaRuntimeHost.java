package org.aohp.agentdriver.uda;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import org.aohp.agentdriver.executor.AohpContainerClient;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Loads a generated UDA app into a {@link WebView} via {@link WebViewAssetLoader}. */
public final class UdaRuntimeHost {
    private static final String TAG = "UdaRuntimeHost";
    private static final String MIME_HTML = "text/html";
    private static final String MIME_JS = "application/javascript";
    private static final String MIME_CSS = "text/css";
    private static final String MIME_JSON = "application/json";
    private static final String MIME_PNG = "image/png";
    private static final String MIME_SVG = "image/svg+xml";

    private final Context mContext;
    private final UdaJobInfo mJob;
    private final AohpUdaJsBridge mBridge;
    private final WebViewAssetLoader mAssetLoader;
    private final File mAppDir;
    private boolean mReleased;

    public UdaRuntimeHost(@NonNull Context context, @NonNull UdaJobInfo job) {
        mContext = context.getApplicationContext();
        mJob = job;
        AohpContainerClient container = new AohpContainerClient(mContext);
        UdaHostFiles.ensureJobReadable(mContext, new UdaContainerFs(container), job.jobId);
        mAppDir = new File(UdaPaths.resolveJobRoot(mContext, job.jobId), "app");
        mBridge = new AohpUdaJsBridge(job, container);
        mAssetLoader =
                new WebViewAssetLoader.Builder()
                        .setDomain(UdaConstants.ASSET_HOST)
                        .addPathHandler("/", this::serveAsset)
                        .build();
        patchAppInContainer(container);
    }

    private void patchAppInContainer(@NonNull AohpContainerClient container) {
        UdaContainerFs fs = new UdaContainerFs(container);
        UdaAppCompat.patchForPreview(fs, mJob.containerOutputDir());
    }

    @Nullable
    private WebResourceResponse serveAsset(@NonNull String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        if (rel.isEmpty()) {
            rel = "index.html";
        }
        File file = new File(mAppDir, rel);
        if (!file.isFile()) {
            return null;
        }
        try {
            String mime = guessMime(rel);
            FileInputStream in = new FileInputStream(file);
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse(mime, "utf-8", 200, "OK", headers, in);
        } catch (Exception e) {
            Log.w(TAG, "serveAsset failed: " + rel, e);
            return null;
        }
    }

    @NonNull
    private static String guessMime(@NonNull String path) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return MIME_HTML;
        }
        if (lower.endsWith(".js")) {
            return MIME_JS;
        }
        if (lower.endsWith(".css")) {
            return MIME_CSS;
        }
        if (lower.endsWith(".json")) {
            return MIME_JSON;
        }
        if (lower.endsWith(".png")) {
            return MIME_PNG;
        }
        if (lower.endsWith(".svg")) {
            return MIME_SVG;
        }
        return "application/octet-stream";
    }

    public void attach(@NonNull WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        mBridge.attach(webView);
        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        if (uri != null && UdaConstants.ASSET_HOST.equals(uri.getHost())) {
                            return false;
                        }
                        return super.shouldOverrideUrlLoading(view, request);
                    }

                    @Nullable
                    @Override
                    public WebResourceResponse shouldInterceptRequest(
                            WebView view, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        if (uri != null) {
                            WebResourceResponse asset = mAssetLoader.shouldInterceptRequest(uri);
                            if (asset != null) {
                                return asset;
                            }
                        }
                        return super.shouldInterceptRequest(view, request);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        mBridge.ensureMockReady();
                        view.evaluateJavascript(AohpUdaJsBridge.buildInjectionScript(), null);
                    }
                });
    }

    public void load(@NonNull WebView webView) {
        if (!mAppDir.isDirectory() || !new File(mAppDir, "index.html").isFile()) {
            Log.e(TAG, "missing app index for job=" + mJob.jobId);
            return;
        }
        webView.loadUrl(UdaConstants.ASSET_BASE + "index.html#/");
    }

    public void release(boolean stopMock) {
        if (mReleased) {
            return;
        }
        mReleased = true;
        if (stopMock) {
            mBridge.releaseMock();
        }
        mBridge.shutdown();
    }

    @NonNull
    public UdaJobInfo job() {
        return mJob;
    }
}
