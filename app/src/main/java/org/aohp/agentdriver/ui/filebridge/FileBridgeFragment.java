package org.aohp.agentdriver.ui.filebridge;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.R;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.aohp.agentdriver.executor.AohpContainerClient;
import org.aohp.agentdriver.executor.ShellExecutor;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Main-tab demo and manual tester for File Bridge. */
public class FileBridgeFragment extends Fragment {
    private static final String TEST_IMAGE_DESC = "AOHP file bridge test image";

    private ExecutorService mIo;
    private AohpContainerClient mContainer;
    private TextView mLog;
    private TextView mStatus;
    private ImageView mPreview;
    private TextInputEditText mDisplayId;
    private String mLastPath;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        if (mIo != null) {
            mIo.shutdownNow();
        }
        mIo = Executors.newSingleThreadExecutor();
        mContainer = new AohpContainerClient(requireContext());
        return buildView(requireContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mIo != null) {
            mIo.shutdownNow();
            mIo = null;
        }
    }

    private View buildView(Context context) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 20);
        scroll.addView(root);

        TextView title = new TextView(context);
        title.setText(getString(R.string.filebridge_title));
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        mStatus = new TextView(context);
        mStatus.setText(getString(R.string.filebridge_status_unchecked));
        mStatus.setPadding(0, 8, 0, 8);
        root.addView(mStatus);

        mDisplayId = new TextInputEditText(context);
        mDisplayId.setHint("displayId");
        mDisplayId.setText("0");
        root.addView(mDisplayId);

        ImageView test = new ImageView(context);
        test.setContentDescription(TEST_IMAGE_DESC);
        test.setImageBitmap(buildTestBitmap());
        test.setAdjustViewBounds(true);
        test.setMaxHeight(360);
        test.setPadding(0, 12, 0, 12);
        test.setOnClickListener(v -> {
            try {
                mLastPath = saveTestImage();
                append(getString(R.string.filebridge_log_test_image_saved, mLastPath) + "\n"
                        + describeSavedPath(mLastPath));
                showPreview(mLastPath);
            } catch (Exception e) {
                append(getString(R.string.filebridge_log_save_failed, e.getMessage()));
            }
        });
        root.addView(test);

        addButton(root, getString(R.string.filebridge_refresh_sandbox), v -> refreshSandbox());
        addButton(root, getString(R.string.filebridge_run_tap_demo), v -> runTapNodeDemo());
        addButton(root, getString(R.string.filebridge_open_folder), v -> runFileCommand("show-in-folder"));
        addButton(root, getString(R.string.filebridge_share_last), v -> runFileCommand("share"));
        addButton(root, getString(R.string.filebridge_recent_scan), v -> runRecent());

        mPreview = new ImageView(context);
        mPreview.setAdjustViewBounds(true);
        mPreview.setMaxHeight(360);
        mPreview.setVisibility(View.GONE);
        root.addView(mPreview);

        mLog = new TextView(context);
        mLog.setTextSize(11);
        mLog.setTextColor(Color.rgb(0, 220, 0));
        mLog.setBackgroundColor(Color.rgb(16, 16, 16));
        mLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        mLog.setPadding(12, 12, 12, 12);
        root.addView(mLog);

        refreshSandbox();
        return scroll;
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(requireContext());
        b.setText(text);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private void refreshSandbox() {
        executeIo(() -> {
            String env = resolveSandboxEnv();
            runOnUi(
                    () -> {
                        String status =
                                env != null
                                        ? getString(R.string.filebridge_status_format, env)
                                        : getString(R.string.filebridge_status_none);
                        mStatus.setText(status);
                    });
        });
    }

    private void runTapNodeDemo() {
        executeIo(() -> {
            String env = resolveSandboxEnv();
            if (env == null) return;
            int display = displayId();
            ShellExecutor.CommandResult tree = sbxExec(env,
                    "aohp ui tree -e -d " + display, 30000);
            String node = findNodeId(tree.output);
            if (node == null) {
                append(getString(R.string.filebridge_log_node_not_found));
                return;
            }
            ShellExecutor.CommandResult tap = sbxExec(env,
                    "aohp act tap-node -d " + display + " -i " + node
                            + " -F --file-path-roots downloads,pictures,dcim,documents,screenshots"
                            + " --file-path-mime image/*",
                    120000);
            parsePathAndPreview(tap.output);
        });
    }

    private void runFileCommand(String cmd) {
        executeIo(() -> {
            String env = resolveSandboxEnv();
            if (env == null || mLastPath == null || mLastPath.isEmpty()) {
                append(getString(R.string.filebridge_log_no_sandbox_or_path));
                return;
            }
            int display = displayId();
            int settleMs = "share".equals(cmd) ? 1800 : 800;
            sbxExec(env, "aohp file " + cmd + " --path " + shellQuote(mLastPath)
                    + " --display " + display + " --settle-ms " + settleMs, 60000);
            String shot = "/sdcard/Download/AOHP/file_bridge_" + cmd + "_"
                    + System.currentTimeMillis() + ".jpg";
            sbxExec(env, "aohp shot full -d " + display + " -O " + shellQuote(shot), 60000);
            showPreview(shot);
        });
    }

    private void runRecent() {
        executeIo(() -> {
            String env = resolveSandboxEnv();
            if (env == null) return;
            ShellExecutor.CommandResult r = sbxExec(env,
                    "aohp file recent --mime image/* --roots downloads,pictures,dcim,documents,screenshots --since 30s",
                    60000);
            parsePathAndPreview(r.output);
        });
    }

    private String resolveSandboxEnv() {
        String[] names = mContainer.listContainers();
        if (names != null && names.length > 0) {
            append(getString(R.string.filebridge_log_auto_select_sandbox, names[0]));
            return names[0];
        }
        append(getString(R.string.filebridge_log_select_sandbox_first));
        return null;
    }

    private ShellExecutor.CommandResult sbxExec(String env, String command, int timeoutMs) {
        append("$ aohp sandbox exec --name " + env + " -- " + command);
        ShellExecutor.CommandResult r = mContainer.execSync(env, command, timeoutMs);
        append("exit=" + r.exitCode + "\nstdout:\n" + safe(r.output) + "\nstderr:\n" + safe(r.error));
        return r;
    }

    private void parsePathAndPreview(String output) {
        try {
            JSONObject o = new JSONObject(output.trim());
            JSONObject best = null;
            if (o.has("files")) {
                JSONObject files = o.getJSONObject("files");
                if (files.optBoolean("detected") && files.has("best")) {
                    best = files.getJSONObject("best");
                }
            } else if (o.has("best")) {
                best = o.getJSONObject("best");
            }
            if (best != null) {
                mLastPath = best.optString("devicePath", "");
                append(getString(R.string.filebridge_log_path_detected, mLastPath));
                showPreview(mLastPath);
            }
        } catch (Exception ignored) {
        }
    }

    private String findNodeId(String output) {
        if (output == null) return null;
        int idx = output.indexOf(TEST_IMAGE_DESC);
        if (idx < 0) return null;
        int start = Math.max(0, idx - 500);
        int end = Math.min(output.length(), idx + 500);
        String around = output.substring(start, end);
        Matcher m = Pattern.compile("\"(?:id|temp_id)\"\\s*:\\s*(\\d+)").matcher(around);
        return m.find() ? m.group(1) : null;
    }

    private String saveTestImage() throws Exception {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "file_bridge_test_" + ts + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveTestImageViaMediaStore(fileName);
        }
        return saveTestImageDirect(fileName);
    }

    private String saveTestImageViaMediaStore(String fileName) throws IOException {
        ContentResolver resolver = requireContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/AOHP");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore insert returned null");
        }
        try {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("MediaStore openOutputStream returned null");
                }
                boolean ok = buildTestBitmap().compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                if (!ok) {
                    throw new IOException("Bitmap.compress returned false");
                }
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            return "/sdcard/Download/AOHP/" + fileName;
        } catch (IOException e) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private String saveTestImageDirect(String fileName) throws IOException {
        File dir = new File(Environment.getExternalStorageDirectory(), "Download/AOHP");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("mkdir failed: " + dir);
        }
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            boolean ok = buildTestBitmap().compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            if (!ok) {
                throw new IOException("Bitmap.compress returned false");
            }
        }
        return "/sdcard/Download/AOHP/" + out.getName();
    }

    private Bitmap buildTestBitmap() {
        Bitmap bmp = Bitmap.createBitmap(800, 420, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(28, 80, 180));
        c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), p);
        p.setColor(Color.WHITE);
        p.setTextSize(52);
        p.setFakeBoldText(true);
        c.drawText("AOHP File Bridge", 70, 170, p);
        p.setTextSize(30);
        p.setFakeBoldText(false);
        c.drawText("Tap this image to save a test file", 70, 235, p);
        return bmp;
    }

    private void showPreview(String path) {
        runOnUi(() -> {
            File f = new File(path);
            if (!f.exists() && path.startsWith("/sdcard/")) {
                f = new File("/storage/emulated/0/" + path.substring("/sdcard/".length()));
            }
            if (f.exists() && f.isFile()) {
                mPreview.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
                mPreview.setVisibility(View.VISIBLE);
            }
        });
    }

    private String describeSavedPath(String path) {
        File f = new File(path);
        if (!f.exists() && path.startsWith("/sdcard/")) {
            f = new File("/storage/emulated/0/" + path.substring("/sdcard/".length()));
        }
        return getString(
                R.string.filebridge_log_write_verify,
                f.exists(),
                f.exists() ? f.length() : 0L,
                f.getAbsolutePath());
    }

    private int displayId() {
        try {
            return Integer.parseInt(mDisplayId.getText() != null ? mDisplayId.getText().toString() : "0");
        } catch (Exception e) {
            return 0;
        }
    }

    private void append(String msg) {
        runOnUi(() -> {
            if (mLog != null) {
                mLog.append(msg + "\n\n");
            }
        });
    }

    private void runOnUi(Runnable r) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(r);
        }
    }

    private void executeIo(Runnable r) {
        ExecutorService executor = mIo;
        if (executor != null && !executor.isShutdown()) {
            executor.execute(r);
        }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
