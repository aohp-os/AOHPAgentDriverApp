package org.aohp.agentdriver.uda;

import android.content.Context;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.executor.ExecutorManager;

import java.util.Collections;

/** Keeps Launcher / app-drawer entries in sync with UDA pin state. */
public final class UdaLauncherSync {
    private static final String TAG = "UdaLauncherSync";

    private UdaLauncherSync() {}

    /** Whether this install should appear on the system launcher or in {@link UdaConstants#PUBLIC_REGISTRY_PATH}. */
    public static boolean isVisibleOnLauncher(@NonNull Context context, @NonNull UdaInstallInfo inst) {
        if (UdaDemoCatalog.isDemoJobId(inst.jobId)) {
            return UdaDemoLauncher.isLauncherEnabled(context, inst.jobId);
        }
        return inst.pinned;
    }

    /** Desktop visibility for history list rows (built-in demos vs user-generated installs). */
    public static boolean isVisibleOnLauncher(
            @NonNull Context context, @NonNull UdaJobInfo job, @Nullable UdaInstallInfo inst) {
        if (job.demo || UdaDemoCatalog.isDemoJobId(job.jobId)) {
            return UdaDemoLauncher.isLauncherEnabled(context, job.jobId);
        }
        return inst != null && inst.pinned;
    }

    /** Disable and remove pinned/dynamic shortcuts for one job. */
    public static void clearDesktopShortcut(@NonNull Context context, @NonNull String jobId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }
        UdaInstallInfo info =
                UdaManager.getInstance(context).installStore().get(jobId);
        String shortcutId =
                info != null && info.shortcutId != null
                        ? info.shortcutId
                        : UdaInstallInfo.shortcutIdFor(jobId);
        ShortcutManager sm = context.getSystemService(ShortcutManager.class);
        if (sm == null) {
            return;
        }
        try {
            sm.disableShortcuts(
                    Collections.singletonList(shortcutId),
                    context.getString(R.string.uda_shortcut_disabled_reason));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sm.removeLongLivedShortcuts(Collections.singletonList(shortcutId));
            }
            sm.removeDynamicShortcuts(Collections.singletonList(shortcutId));
        } catch (Exception e) {
            Log.w(TAG, "clearDesktopShortcut failed for " + jobId, e);
        }
    }

    /** After pin/unpin: refresh public registry, Launcher model, and cached app list. */
    public static void afterDesktopVisibilityChanged(
            @NonNull Context context,
            @NonNull UdaInstallStore installStore,
            @NonNull UdaJobRegistry jobRegistry) {
        UdaPublicRegistrySync.sync(context, installStore, jobRegistry);
        refreshCachedAppList(context);
    }

    private static void refreshCachedAppList(@NonNull Context context) {
        try {
            ExecutorManager em = ExecutorManager.getInstance();
            if (em != null) {
                em.getAppInfoManager().refreshInBackground();
            }
        } catch (Exception e) {
            Log.d(TAG, "AppInfoManager refresh skipped", e);
        }
    }
}
