package org.aohp.agentdriver.uda;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.executor.AohpContainerClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaScript bridge for generated apps: proxies mock API calls to the UDA container mock server
 * when present, and exposes a small {@code window.AOHP_UDA} helper.
 */
public final class AohpUdaJsBridge {
    private static final String TAG = "AohpUdaJsBridge";
    public static final String INTERFACE_NAME = "AohpUdaBridge";

    private final UdaJobInfo mJob;
    private final UdaPreviewHelper mPreview;
    private final AtomicBoolean mMockReady = new AtomicBoolean(false);
    private final ExecutorService mHttp = Executors.newCachedThreadPool();

    public AohpUdaJsBridge(
            @NonNull UdaJobInfo job, @NonNull AohpContainerClient container) {
        mJob = job;
        mPreview = new UdaPreviewHelper(container);
    }

    public void attach(@NonNull WebView webView) {
        webView.addJavascriptInterface(this, INTERFACE_NAME);
    }

    public void detach(@NonNull WebView webView) {
        webView.removeJavascriptInterface(INTERFACE_NAME);
    }

    /** Ensure mock HTTP server is running (no-op if app has no mock). */
    public boolean ensureMockReady() {
        if (mMockReady.get()) {
            return true;
        }
        if (!mPreview.hasMockServer(mJob)) {
            mMockReady.set(true);
            return true;
        }
        boolean ok = mPreview.startMockOnly(mJob);
        if (ok) {
            mMockReady.set(true);
        }
        return ok;
    }

    public void releaseMock() {
        if (mPreview.hasMockServer(mJob)) {
            mPreview.stopPreviewServices(mJob.jobId);
        }
        mMockReady.set(false);
    }

    @JavascriptInterface
    @NonNull
    public String getJobId() {
        return mJob.jobId;
    }

    /** HTTP GET proxy to mock server (path e.g. {@code /api/todos}). */
    @JavascriptInterface
    @NonNull
    public String httpGet(@NonNull String path) {
        return httpRequest("GET", path, null);
    }

    @JavascriptInterface
    @NonNull
    public String httpPost(@NonNull String path, @Nullable String bodyJson) {
        return httpRequest("POST", path, bodyJson);
    }

    @NonNull
    private String httpRequest(@NonNull String method, @NonNull String path, @Nullable String body) {
        if (!ensureMockReady()) {
            return errorJson("mock_unavailable", "mock server not ready");
        }
        if (!mPreview.hasMockServer(mJob)) {
            return errorJson("no_mock", "this app has no mock backend");
        }
        try {
            String normalized = path.startsWith("/") ? path : "/" + path;
            URL url =
                    new URL(
                            "http://127.0.0.1:"
                                    + UdaConstants.LEGACY_MOCK_PORT
                                    + normalized);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(bytes);
            }
            int code = conn.getResponseCode();
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                                    StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            conn.disconnect();
            return String.format(
                    Locale.US,
                    "{\"ok\":%s,\"status\":%d,\"body\":%s}",
                    code >= 200 && code < 300 ? "true" : "false",
                    code,
                    quoteJsonString(sb.toString()));
        } catch (Exception e) {
            Log.w(TAG, "httpRequest failed: " + method + " " + path, e);
            return errorJson("http_error", e.getMessage() != null ? e.getMessage() : "error");
        }
    }

    @NonNull
    private static String quoteJsonString(@NonNull String raw) {
        return org.json.JSONObject.quote(raw);
    }

    @NonNull
    private static String errorJson(@NonNull String code, @NonNull String message) {
        return String.format(
                Locale.US,
                "{\"ok\":false,\"error\":{\"code\":\"%s\",\"message\":%s}}",
                code.replace("\"", ""),
                quoteJsonString(message));
    }

    /** Injected after page load to rewrite legacy mock fetch URLs. */
    @NonNull
    public static String buildInjectionScript() {
        return "(function(){"
                + "if(window.__aohpUdaBridgeInstalled)return;"
                + "window.__aohpUdaBridgeInstalled=true;"
                + "window.AOHP_UDA={jobId:function(){return "
                + INTERFACE_NAME
                + ".getJobId();},"
                + "get:function(p){return JSON.parse("
                + INTERFACE_NAME
                + ".httpGet(p));},"
                + "post:function(p,b){return JSON.parse("
                + INTERFACE_NAME
                + ".httpPost(p,typeof b==='string'?b:JSON.stringify(b||{})));}};"
                + "var _fetch=window.fetch;"
                + "window.fetch=function(input,init){"
                + "try{"
                + "var u=typeof input==='string'?input:(input&&input.url?input.url:'');"
                + "if(u.indexOf('127.0.0.1:"
                + UdaConstants.LEGACY_MOCK_PORT
                + "')>=0||u.indexOf('localhost:"
                + UdaConstants.LEGACY_MOCK_PORT
                + "')>=0){"
                + "var path=u.replace(/^https?:\\/\\/[^/]+/,'');"
                + "var m=(init&&init.method)?init.method:'GET';"
                + "var body=(init&&init.body)?(typeof init.body==='string'?init.body:''):'';"
                + "var r=m==='POST'?JSON.parse("
                + INTERFACE_NAME
                + ".httpPost(path,body)):"
                + "JSON.parse("
                + INTERFACE_NAME
                + ".httpGet(path));"
                + "if(!r.ok){return Promise.reject(new Error(r.error?r.error.message:'http'));}"
                + "return Promise.resolve(new Response(r.body,{status:r.status||200}));"
                + "}"
                + "}catch(e){}"
                + "return _fetch.apply(this,arguments);"
                + "};"
                + "})();";
    }

    public void shutdown() {
        mHttp.shutdownNow();
    }
}
