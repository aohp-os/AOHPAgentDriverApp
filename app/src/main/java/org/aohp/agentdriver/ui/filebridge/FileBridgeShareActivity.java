package org.aohp.agentdriver.ui.filebridge;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.aohp.agentdriver.executor.file.FileBridgeManager;

import java.io.File;

/**
 * Display-local trampoline for system sharesheet.
 *
 * <p>Starting the Android chooser directly from the JSON-RPC service has no source activity on a
 * virtual display. Launching this activity on the target display first gives ActivityTaskManager a
 * source record on that display, so AOHP's virtual-display launch policy can keep Resolver/Chooser
 * UI on the same screen.</p>
 */
public class FileBridgeShareActivity extends Activity {
    public static final String ACTION_SHARE_FILE = "org.aohp.agentdriver.action.SHARE_FILE";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_PACKAGE_NAME = "packageName";
    public static final String EXTRA_DISPLAY_ID = "displayId";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mStatus;
    private String mPath;
    private String mPackageName;
    private int mDisplayId = -1;
    private boolean mChooserLaunched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPath = getIntent().getStringExtra(EXTRA_PATH);
        mPackageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        mDisplayId = getIntent().getIntExtra(EXTRA_DISPLAY_ID, -1);
        setContentView(buildContent());
        mHandler.postDelayed(this::shareFromIntent, 250);
    }

    private void shareFromIntent() {
        try {
            File file = toFile(mPath);
            if (file == null || !file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("path not readable: " + mPath);
            }
            Uri uri = FileProvider.getUriForFile(this,
                    FileBridgeManager.FILE_PROVIDER_AUTHORITY, file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(mimeFor(file))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(getContentResolver(), "AOHP shared file", uri));
            if (mPackageName != null && !mPackageName.isEmpty()) {
                send.setPackage(mPackageName);
            }
            Intent chooser = Intent.createChooser(send, "Share file")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            int targetDisplayId = mDisplayId >= 0
                    ? mDisplayId
                    : (getDisplay() != null ? getDisplay().getDisplayId() : -1);
            mStatus.setText("Opening Android share sheet on display "
                    + (targetDisplayId >= 0 ? targetDisplayId : "default") + "...");
            mChooserLaunched = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && targetDisplayId >= 0) {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchDisplayId(targetDisplayId);
                startActivity(chooser, opts.toBundle());
            } else {
                startActivity(chooser);
            }
        } catch (Exception e) {
            mChooserLaunched = false;
            mStatus.setText("Share failed: " + e.getMessage());
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mChooserLaunched) {
            mStatus.setText("Share sheet closed. You can retry or go back.");
        }
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(0xFFF6F7FB);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(22));
        card.setCardElevation(dp(3));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.addView(inner);
        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("File Bridge Share");
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        inner.addView(title);

        TextView path = new TextView(this);
        path.setText(mPath != null ? mPath : "(no path)");
        path.setTextSize(12);
        path.setTypeface(android.graphics.Typeface.MONOSPACE);
        path.setPadding(0, dp(10), 0, dp(12));
        path.setTextIsSelectable(true);
        inner.addView(path);

        ProgressBar progress = new ProgressBar(this);
        inner.addView(progress);

        mStatus = new TextView(this);
        mStatus.setText("Preparing Android share sheet...");
        mStatus.setTextSize(14);
        mStatus.setPadding(0, dp(12), 0, dp(12));
        inner.addView(mStatus);

        MaterialButton retry = new MaterialButton(this);
        retry.setText("Retry share");
        retry.setOnClickListener(v -> shareFromIntent());
        inner.addView(retry);

        MaterialButton close = new MaterialButton(this);
        close.setText("Close");
        close.setOnClickListener(v -> finish());
        inner.addView(close);
        return root;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static File toFile(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("/sdcard")) {
            return new File("/storage/emulated/0" + path.substring("/sdcard".length()));
        }
        return new File(path);
    }

    private static String mimeFor(File file) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) : null;
        return mime != null ? mime : "application/octet-stream";
    }
}
