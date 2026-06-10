package org.aohp.agentdriver.uda;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.ui.uda.UdaAppActivity;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;

/** Install registry, home-screen pin, and launcher intents for UDA apps. */
public final class UdaInstallManager {
    private static final String TAG = "UdaInstallManager";

    private final Context mAppContext;
    private final UdaInstallStore mInstallStore;
    private final UdaJobRegistry mJobRegistry;
    private final UdaGenerationEngine mEngine;
    private final UdaPreviewHelper mPreview;

    public UdaInstallManager(
            @NonNull Context context,
            @NonNull UdaInstallStore installStore,
            @NonNull UdaJobRegistry jobRegistry,
            @NonNull UdaGenerationEngine engine) {
        mAppContext = context.getApplicationContext();
        mInstallStore = installStore;
        mJobRegistry = jobRegistry;
        mEngine = engine;
        mPreview = new UdaPreviewHelper(new org.aohp.agentdriver.executor.AohpContainerClient(mAppContext));
    }

    public boolean canLaunch(@NonNull String jobId) {
        return mEngine.hasAppIndex(jobId);
    }

    @Nullable
    public UdaInstallInfo getInstall(@NonNull String jobId) {
        return mInstallStore.get(jobId);
    }

    public boolean isInstalled(@NonNull String jobId) {
        return mInstallStore.isInstalled(jobId);
    }

    @NonNull
    public JSONObject install(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        boolean pin = p.optBoolean("pin", false);
        UdaJobInfo job = mJobRegistry.getJob(jobId);
        if (job == null) {
            return notFound(jobId);
        }
        if (!mEngine.hasAppIndex(jobId)) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "not_ready");
            err.put("message", "app/index.html not generated yet");
            return err;
        }
        String displayName = p.optString("displayName", job.appName);
        UdaInstallInfo existing = mInstallStore.get(jobId);
        UdaInstallInfo info =
                new UdaInstallInfo(
                        jobId,
                        displayName,
                        existing != null ? existing.installedAtMs : System.currentTimeMillis(),
                        existing != null && existing.pinned,
                        existing != null ? existing.shortcutId : UdaInstallInfo.shortcutIdFor(jobId),
                        "asset_loader");
        mInstallStore.put(info);
        syncPublic();
        if (pin) {
            JSONObject pinResult = requestPin(job, info);
            if (pinResult.optBoolean("error", false)) {
                return pinResult;
            }
            info.pinned = true;
            mInstallStore.put(info);
            syncPublic();
        }
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("jobId", jobId);
        o.put("installed", true);
        o.put("pinned", info.pinned);
        return o;
    }

    @NonNull
    public JSONObject uninstall(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        mPreview.stopPreviewServices(jobId);
        clearDesktopPresence(jobId);
        boolean removed = mInstallStore.remove(jobId);
        syncPublic();
        JSONObject o = new JSONObject();
        o.put("ok", removed);
        o.put("jobId", jobId);
        return o;
    }

    /** Clears desktop pin state but keeps the install registry entry. */
    @NonNull
    public JSONObject unpin(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        UdaInstallInfo info = mInstallStore.get(jobId);
        if (info != null) {
            info.pinned = false;
            mInstallStore.put(info);
        }
        clearDesktopPresence(jobId);
        if (info != null) {
            info.pinned = false;
            mInstallStore.put(info);
        }
        syncPublic();
        boolean pinned =
                UdaDemoCatalog.isDemoJobId(jobId)
                        ? UdaDemoLauncher.isLauncherEnabled(mAppContext, jobId)
                        : (info != null && info.pinned);
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("jobId", jobId);
        o.put("pinned", pinned);
        o.put("installed", info != null);
        return o;
    }

    @NonNull
    public JSONObject requestPin(@NonNull UdaJobInfo job, @NonNull UdaInstallInfo info)
            throws JSONException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "unsupported");
            err.put("message", "pin shortcut requires API 26+");
            return err;
        }
        ShortcutManager sm = mAppContext.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "unsupported");
            err.put("message", "launcher does not support pin shortcuts");
            return err;
        }
        Intent launch = buildLaunchIntent(job.jobId);
        ShortcutInfo shortcut =
                new ShortcutInfo.Builder(mAppContext, info.shortcutId != null ? info.shortcutId : UdaInstallInfo.shortcutIdFor(job.jobId))
                        .setShortLabel(info.displayName)
                        .setLongLabel(info.displayName)
                        .setIcon(UdaIconHelper.loadShortcutIcon(mAppContext, job))
                        .setIntent(launch)
                        .build();
        boolean requested = sm.requestPinShortcut(shortcut, null);
        if (requested) {
            UdaPinConfirmHelper.scheduleAutoConfirm();
        }
        JSONObject o = new JSONObject();
        o.put("ok", requested);
        o.put("pinned", requested);
        o.put("jobId", job.jobId);
        return o;
    }

    @NonNull
    public JSONObject pin(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        UdaJobInfo job = mJobRegistry.getJob(jobId);
        if (job == null) {
            return notFound(jobId);
        }
        if (!mEngine.hasAppIndex(jobId)) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "not_ready");
            err.put("message", "app not ready");
            return err;
        }
        UdaInstallInfo info = mInstallStore.get(jobId);
        if (info == null) {
            JSONObject install = install(new JSONObject().put("jobId", jobId).put("pin", false));
            if (install.optBoolean("error", false)) {
                return install;
            }
            info = mInstallStore.get(jobId);
        }
        if (info == null) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "install_failed");
            err.put("message", "could not register install");
            return err;
        }
        if (UdaDemoCatalog.isDemoJobId(jobId)) {
            UdaDemoLauncher.setLauncherEnabled(mAppContext, jobId, true);
            info.pinned = UdaDemoLauncher.isLauncherEnabled(mAppContext, jobId);
            mInstallStore.put(info);
            syncPublic();
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("pinned", true);
            o.put("jobId", jobId);
            return o;
        }
        JSONObject pinResult = requestPin(job, info);
        if (!pinResult.optBoolean("error", false) && pinResult.optBoolean("ok", false)) {
            info.pinned = true;
            mInstallStore.put(info);
            syncPublic();
        }
        return pinResult;
    }

    private void clearDesktopPresence(@NonNull String jobId) {
        if (UdaDemoCatalog.isDemoJobId(jobId)) {
            UdaDemoLauncher.setLauncherEnabled(mAppContext, jobId, false);
        }
        UdaLauncherSync.clearDesktopShortcut(mAppContext, jobId);
    }

    public void launch(@NonNull Context context, @NonNull String jobId) {
        if (!canLaunch(jobId)) {
            Toast.makeText(mAppContext, R.string.uda_app_not_generated, Toast.LENGTH_SHORT).show();
            return;
        }
        context.startActivity(buildLaunchIntent(jobId));
    }

    @NonNull
    public JSONObject launchJson(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        if (!canLaunch(jobId)) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "not_ready");
            err.put("message", "app/index.html not generated yet");
            return err;
        }
        launch(mAppContext, jobId);
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("jobId", jobId);
        o.put("launchUri", launchUri(jobId));
        return o;
    }

    /**
     * Each {@code jobId} uses a distinct {@code aohp-uda://} URI so launcher entries open separate
     * documents instead of reusing one {@link UdaAppActivity} instance.
     */
    @NonNull
    public static Intent buildLaunchIntent(@NonNull String jobId) {
        // Pinned shortcuts require a non-null action (ShortcutInfo.Builder validates this).
        return new Intent(Intent.ACTION_VIEW, Uri.parse(launchUri(jobId)))
                .setClassName(
                        "org.aohp.agentdriver",
                        UdaAppActivity.class.getName())
                .putExtra(UdaConstants.EXTRA_JOB_ID, jobId)
                .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    @NonNull
    public static String launchUri(@NonNull String jobId) {
        return UdaConstants.SCHEME + "://" + UdaConstants.HOST_APP + "/" + jobId;
    }

    @Nullable
    public static String resolveJobId(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        String extra = intent.getStringExtra(UdaConstants.EXTRA_JOB_ID);
        if (extra != null && !extra.isEmpty()) {
            return extra;
        }
        Uri data = intent.getData();
        if (data == null) {
            return null;
        }
        if (!UdaConstants.SCHEME.equals(data.getScheme())
                || !UdaConstants.HOST_APP.equals(data.getHost())) {
            return null;
        }
        if (data.getPathSegments() != null && !data.getPathSegments().isEmpty()) {
            return data.getPathSegments().get(0);
        }
        return data.getLastPathSegment();
    }

    private void syncPublic() {
        UdaLauncherSync.afterDesktopVisibilityChanged(mAppContext, mInstallStore, mJobRegistry);
    }

    @NonNull
    private static JSONObject notFound(@NonNull String jobId) throws JSONException {
        JSONObject err = new JSONObject();
        err.put("error", true);
        err.put("code", "not_found");
        err.put("message", "unknown jobId: " + jobId);
        return err;
    }
}
