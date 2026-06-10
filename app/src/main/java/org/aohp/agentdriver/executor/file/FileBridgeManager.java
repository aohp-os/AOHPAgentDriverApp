package org.aohp.agentdriver.executor.file;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import org.aohp.agentdriver.executor.AohpFileBridgeClient;
import org.aohp.agentdriver.ui.filebridge.FileBridgeActivity;
import org.aohp.agentdriver.ui.filebridge.FileBridgeShareActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/** Thin JSON-RPC facade for file bridge operations backed by the AOSP service. */
public final class FileBridgeManager {
    public static final String FILE_PROVIDER_AUTHORITY = "org.aohp.agentdriver.fileprovider";
    private static final int DEFAULT_RETRY_DELAY_MS = 1000;

    private final Context mContext;
    private final AohpFileBridgeClient mClient;

    public FileBridgeManager(Context context) {
        mContext = context.getApplicationContext();
        mClient = new AohpFileBridgeClient(mContext);
    }

    public JSONObject stat(JSONObject p) {
        return mClient.stat(pathParam(p));
    }

    public JSONObject list(JSONObject p) {
        return mClient.list(pathParam(p), p);
    }

    public JSONObject recent(JSONObject p) {
        return mClient.recent(p);
    }

    public JSONObject snapshot(JSONObject p) {
        return mClient.snapshot(p);
    }

    public JSONObject diff(JSONObject p) {
        return mClient.diff(p.optString("beforeSnapshotId"), p.optString("afterSnapshotId"), p);
    }

    public JSONObject showInFolder(JSONObject p) {
        try {
            String path = pathParam(p);
            int displayId = p.optInt("displayId", -1);
            Intent intent = new Intent(mContext, FileBridgeActivity.class)
                    .setAction(FileBridgeActivity.ACTION_SHOW_IN_FOLDER)
                    .putExtra(FileBridgeActivity.EXTRA_PATH, path)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchOnDisplay(intent, displayId);
            waitForUiSettle(p);
            JSONObject out = ok();
            out.put("launched", true);
            out.put("displayId", displayId);
            out.put("path", path);
            return out;
        } catch (Exception e) {
            return error("show_in_folder_failed", e.getMessage());
        }
    }

    public JSONObject reveal(JSONObject p) {
        return showInFolder(p);
    }

    public JSONObject share(JSONObject p) {
        try {
            String path = pathParam(p);
            int displayId = p.optInt("displayId", -1);
            File file = new File(path);
            if (!file.exists() && path.startsWith("/sdcard/")) {
                file = new File("/storage/emulated/0/" + path.substring("/sdcard/".length()));
            }
            if (!file.exists() || !file.isFile()) {
                return error("path_not_readable", path);
            }
            String pkg = p.optString("packageName", p.optString("package", ""));
            Intent intent = new Intent(mContext, FileBridgeShareActivity.class)
                    .setAction(FileBridgeShareActivity.ACTION_SHARE_FILE)
                    .putExtra(FileBridgeShareActivity.EXTRA_PATH, path)
                    .putExtra(FileBridgeShareActivity.EXTRA_PACKAGE_NAME, pkg)
                    .putExtra(FileBridgeShareActivity.EXTRA_DISPLAY_ID, displayId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchOnDisplay(intent, displayId);
            waitForUiSettle(p);
            JSONObject out = ok();
            out.put("launched", true);
            out.put("displayId", displayId);
            out.put("path", path);
            out.put("via", "FileBridgeShareActivity");
            return out;
        } catch (Exception e) {
            return error("share_failed", e.getMessage());
        }
    }

    public CompletableFuture<JSONObject> withFilePathReport(
            JSONObject params, CompletableFuture<JSONObject> action) {
        JSONObject report = params != null ? params.optJSONObject("filePathReport") : null;
        if (report == null) {
            return action;
        }
        long startMs = System.currentTimeMillis();
        return action.thenApply(result -> {
            try {
                long reportStartMs = System.currentTimeMillis();
                int settleMs = report.optInt("settleMs", 1200);
                if (settleMs > 0) {
                    SystemClock.sleep(Math.min(settleMs, 10_000));
                }
                JSONObject query = new JSONObject(report.toString());
                query.put("sinceMs", startMs - Math.max(0, report.optLong("windowMs", 30_000)));
                JSONObject files = mClient.recent(query);
                boolean retried = false;
                if (!isDetected(files)) {
                    int retryDelayMs = report.optInt("retryDelayMs", DEFAULT_RETRY_DELAY_MS);
                    if (retryDelayMs > 0) {
                        SystemClock.sleep(Math.min(retryDelayMs, 10_000));
                        files = mClient.recent(query);
                        retried = true;
                    }
                }
                JSONObject normalized = normalizeFiles(files);
                normalized.put("retried", retried);
                normalized.put("elapsedMs", System.currentTimeMillis() - reportStartMs);
                result.put("files", normalized);
            } catch (Exception e) {
                try {
                    result.put("files", errorFiles("file_path_report_failed", e.getMessage()));
                } catch (JSONException ignored) {
                }
            }
            return result;
        });
    }

    private static boolean isDetected(JSONObject raw) {
        if (raw == null || !raw.optBoolean("ok", false)) {
            return false;
        }
        JSONArray candidates = raw.optJSONArray("candidates");
        return raw.optBoolean("detected", candidates != null && candidates.length() > 0);
    }

    private JSONObject normalizeFiles(JSONObject raw) throws JSONException {
        JSONObject files = new JSONObject();
        boolean ok = raw.optBoolean("ok", false);
        if (!ok) {
            files.put("detected", false);
            files.put("reason", raw.optString("code", "file_bridge_unavailable"));
            files.put("message", raw.optString("message", ""));
            return files;
        }
        JSONArray candidates = raw.optJSONArray("candidates");
        boolean detected = raw.optBoolean("detected", candidates != null && candidates.length() > 0);
        files.put("detected", detected);
        files.put("partial", raw.optBoolean("partial", false));
        files.put("elapsedMs", raw.optLong("elapsedMs", 0));
        files.put("candidates", candidates != null ? candidates : new JSONArray());
        if (detected && raw.has("best")) {
            files.put("best", raw.getJSONObject("best"));
        } else {
            files.put("reason", raw.optString("reason", "no_change_in_window"));
        }
        return files;
    }

    private JSONObject errorFiles(String code, String message) throws JSONException {
        JSONObject files = new JSONObject();
        files.put("detected", false);
        files.put("reason", code);
        files.put("message", message != null ? message : code);
        return files;
    }

    private void launchOnDisplay(Intent intent, int displayId) {
        if (displayId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(displayId);
            mContext.startActivity(intent, opts.toBundle());
        } else {
            mContext.startActivity(intent);
        }
    }

    private static void waitForUiSettle(JSONObject p) {
        int settleMs = p.optInt("settleUiMs", p.optInt("settleMs", 0));
        if (settleMs > 0) {
            SystemClock.sleep(Math.min(settleMs, 10_000));
        }
    }

    private static String pathParam(JSONObject p) {
        String path = p.optString("path", "");
        if (path.isEmpty()) {
            path = p.optString("devicePath", "");
        }
        return path;
    }

    private static JSONObject ok() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        return o;
    }

    private static JSONObject error(String code, String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false);
            o.put("error", true);
            o.put("code", code);
            o.put("message", message != null ? message : code);
        } catch (JSONException ignored) {
        }
        return o;
    }
}
