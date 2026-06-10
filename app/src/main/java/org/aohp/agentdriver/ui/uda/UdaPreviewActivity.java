package org.aohp.agentdriver.ui.uda;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.executor.AohpContainerClient;
import org.aohp.agentdriver.uda.UdaPreviewHelper;

/** Full-screen WebView preview for a generated UDA HTML app. */
public class UdaPreviewActivity extends AppCompatActivity {
    public static final String EXTRA_JOB_ID = "jobId";

    private String mJobId;
    private WebView mWebView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_uda_preview);

        mJobId = getIntent().getStringExtra(EXTRA_JOB_ID);
        if (mJobId == null || mJobId.isEmpty()) {
            finish();
            return;
        }

        mWebView = findViewById(R.id.web_preview);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.setWebChromeClient(
                new WebChromeClient() {
                    @Override
                    public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                        android.util.Log.d(
                                "UdaPreview",
                                msg.message()
                                        + " ("
                                        + msg.sourceId()
                                        + ":"
                                        + msg.lineNumber()
                                        + ")");
                        return true;
                    }
                });

        loadPreview();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String newJobId = intent.getStringExtra(EXTRA_JOB_ID);
        if (newJobId == null || newJobId.isEmpty() || newJobId.equals(mJobId)) {
            return;
        }
        mJobId = newJobId;
        loadPreview();
    }

    private void loadPreview() {
        mWebView.loadUrl(UdaPreviewHelper.previewUrl());
    }

    @Override
    protected void onDestroy() {
        if (mJobId != null) {
            UdaPreviewHelper.releasePreview(new AohpContainerClient(this), mJobId);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
