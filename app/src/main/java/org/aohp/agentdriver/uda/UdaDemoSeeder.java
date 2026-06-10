package org.aohp.agentdriver.uda;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/** Seeds bundled demo apps into host storage, job registry, and install store. */
public final class UdaDemoSeeder {
    private static final String TAG = "UdaDemoSeeder";
    private static final String PREFS = "uda_demo_seed";
    private static final String KEY_VERSION = "seed_version";
    private static final int SEED_VERSION = 3;

    private UdaDemoSeeder() {}

    /** Ensures one built-in demo is unpacked before launcher entry (may block briefly). */
    public static void ensureDemoReady(@NonNull Context context, @NonNull UdaManager manager,
            @NonNull String jobId) {
        if (UdaPaths.hasAppIndex(context, jobId)) {
            return;
        }
        UdaDemoCatalog.Entry demo = UdaDemoCatalog.find(jobId);
        if (demo != null) {
            seedOne(context, manager, demo);
        }
    }

    /** Idempotent; safe to call from a background thread. */
    public static void seedIfNeeded(@NonNull Context context, @NonNull UdaManager manager) {
        UdaDemoLauncher.ensureDefaultVisible(context);
        if (allDemosReady(context)
                && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_VERSION, 0)
                        >= SEED_VERSION) {
            return;
        }
        boolean allOk = true;
        for (UdaDemoCatalog.Entry demo : UdaDemoCatalog.all()) {
            if (!seedOne(context, manager, demo)) {
                allOk = false;
            }
        }
        if (allOk) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_VERSION, SEED_VERSION)
                    .apply();
            UdaPublicRegistrySync.sync(context, manager.installStore(), manager.registry());
            Log.i(TAG, "demo apps seeded");
        } else {
            Log.w(TAG, "demo seed incomplete; will retry on next launch");
        }
    }

    private static boolean seedOne(
            @NonNull Context context,
            @NonNull UdaManager manager,
            @NonNull UdaDemoCatalog.Entry demo) {
        if (!UdaHostFiles.copyDemoAssets(context, demo.assetDir, demo.jobId)) {
            Log.e(TAG, "failed to copy assets for " + demo.jobId);
            return false;
        }
        UdaJobRegistry registry = manager.registry();
        UdaJobInfo existing = registry.getJob(demo.jobId);
        if (existing == null) {
            UdaJobInfo job =
                    new UdaJobInfo(
                            demo.jobId,
                            demo.appName,
                            demo.idea,
                            1L,
                            UdaJobStatus.COMPLETED);
            job.demo = true;
            job.stage = "build";
            registry.addJob(job);
        } else {
            UdaJobInfo job =
                    new UdaJobInfo(
                            demo.jobId,
                            demo.appName,
                            demo.idea,
                            existing.createdAtMs,
                            UdaJobStatus.COMPLETED);
            job.demo = true;
            job.stage = "build";
            job.errorMessage = null;
            registry.updateJob(job);
        }
        try {
            JSONObject install =
                    new JSONObject()
                            .put("jobId", demo.jobId)
                            .put("displayName", demo.appName)
                            .put("pin", false);
            JSONObject res = manager.install(install);
            if (res.optBoolean("error", false)) {
                Log.e(TAG, "install demo failed: " + res.optString("message"));
                return false;
            }
            boolean onLauncher = UdaDemoLauncher.isLauncherEnabled(context, demo.jobId);
            UdaInstallInfo info = manager.installStore().get(demo.jobId);
            if (info != null) {
                info.displayName = demo.appName;
                info.pinned = onLauncher;
                manager.installStore().put(info);
                UdaPublicRegistrySync.sync(context, manager.installStore(), manager.registry());
            }
        } catch (JSONException e) {
            Log.e(TAG, "install demo json error", e);
            return false;
        }
        return true;
    }

    private static boolean allDemosReady(@NonNull Context context) {
        for (UdaDemoCatalog.Entry demo : UdaDemoCatalog.all()) {
            if (!UdaPaths.hasAppIndex(context, demo.jobId)) {
                return false;
            }
        }
        return true;
    }
}
