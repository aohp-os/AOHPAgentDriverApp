package org.aohp.agentdriver.ui.uda;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.uda.UdaDemoCatalog;
import org.aohp.agentdriver.uda.UdaDemoSeeder;
import org.aohp.agentdriver.uda.UdaInstallManager;
import org.aohp.agentdriver.uda.UdaJobInfo;
import org.aohp.agentdriver.uda.UdaJobStatus;
import org.aohp.agentdriver.uda.UdaManager;
import org.aohp.agentdriver.uda.UdaRuntimeHost;

/** Full-screen launcher runtime for one generated UDA HTML app. */
public class UdaAppActivity extends AppCompatActivity {
    private String mJobId;
    private WebView mWebView;
    private UdaRuntimeHost mRuntime;
    private UdaManager mManager;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_uda_app);

        mManager = UdaManager.getInstance(this);
        mWebView = findViewById(R.id.web_uda_app);
        mWebView.setWebChromeClient(
                new WebChromeClient() {
                    @Override
                    public boolean onConsoleMessage(android.webkit.ConsoleMessage msg) {
                        android.util.Log.d(
                                "UdaApp",
                                msg.message()
                                        + " ("
                                        + msg.sourceId()
                                        + ":"
                                        + msg.lineNumber()
                                        + ")");
                        return true;
                    }
                });

        String jobId = UdaInstallManager.resolveJobId(getIntent());
        if (jobId == null || jobId.isEmpty()) {
            finish();
            return;
        }
        loadJob(jobId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String jobId = UdaInstallManager.resolveJobId(intent);
        if (jobId == null || jobId.isEmpty()) {
            return;
        }
        loadJob(jobId);
    }

    private void loadJob(@NonNull String jobId) {
        if (jobId.equals(mJobId) && mRuntime != null) {
            return;
        }
        releaseRuntime();

        mJobId = jobId;
        if (UdaDemoCatalog.isDemoJobId(jobId)) {
            UdaDemoSeeder.ensureDemoReady(this, mManager, jobId);
        }
        if (!mManager.canLaunch(jobId)) {
            android.widget.Toast.makeText(
                            this, R.string.uda_app_not_ready, android.widget.Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        UdaJobInfo job = mManager.registry().getJob(jobId);
        if (job == null) {
            job =
                    new UdaJobInfo(
                            jobId,
                            jobId,
                            "",
                            System.currentTimeMillis(),
                            UdaJobStatus.COMPLETED);
        }

        mWebView.stopLoading();
        mWebView.clearHistory();
        mWebView.loadUrl("about:blank");

        mRuntime = new UdaRuntimeHost(this, job);
        mRuntime.attach(mWebView);
        mRuntime.load(mWebView);
    }

    private void releaseRuntime() {
        if (mRuntime != null) {
            boolean stopMock =
                    mJobId == null || !mManager.installManager().isInstalled(mJobId);
            mRuntime.release(stopMock);
            mRuntime = null;
        }
    }

    @Override
    protected void onDestroy() {
        releaseRuntime();
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
