package org.aohp.agentdriver.uda;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

/** Shows or hides built-in demo apps on the system launcher. */
public final class UdaDemoLauncher {
    private static final String TAG = "UdaDemoLauncher";
    private static final String PREFS = "uda_demo_launcher";

    private UdaDemoLauncher() {}

    public static boolean isLauncherEnabled(@NonNull Context context, @NonNull String jobId) {
        UdaDemoCatalog.Entry demo = UdaDemoCatalog.find(jobId);
        if (demo == null) {
            return false;
        }
        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.contains(jobId)) {
            return prefs.getBoolean(jobId, false);
        }
        return isComponentEnabledOnLauncher(context, demo.gatewayActivity);
    }

    /** Resolves {@link PackageManager#getComponentEnabledSetting} including manifest default. */
    private static boolean isComponentEnabledOnLauncher(
            @NonNull Context context, @NonNull String gatewayActivity) {
        ComponentName cn = new ComponentName(context.getApplicationContext(), gatewayActivity);
        PackageManager pm = context.getPackageManager();
        int state = pm.getComponentEnabledSetting(cn);
        switch (state) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default:
                try {
                    return pm.getActivityInfo(cn, 0).enabled;
                } catch (PackageManager.NameNotFoundException e) {
                    return false;
                }
        }
    }

    public static void setLauncherEnabled(
            @NonNull Context context, @NonNull String jobId, boolean enabled) {
        UdaDemoCatalog.Entry demo = UdaDemoCatalog.find(jobId);
        if (demo == null) {
            return;
        }
        Context app = context.getApplicationContext();
        if (!app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(jobId, enabled)
                .commit()) {
            Log.w(TAG, "failed to persist launcher visibility for " + jobId);
        }
        ComponentName cn = new ComponentName(app, demo.gatewayActivity);
        int newState =
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try {
            app.getPackageManager()
                    .setComponentEnabledSetting(
                            cn, newState, PackageManager.DONT_KILL_APP);
            notifyLauncherPackageChanged(app, cn);
        } catch (Exception e) {
            Log.e(TAG, "setComponentEnabledSetting failed for " + jobId, e);
        }
    }

    /**
     * First boot: show demo icons when the user has not chosen to hide them yet. Skips demos the
     * user already unpinned (prefs entry exists).
     */
    public static void ensureDefaultVisible(@NonNull Context context) {
        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (UdaDemoCatalog.Entry demo : UdaDemoCatalog.all()) {
            if (!demo.defaultLauncherVisible || prefs.contains(demo.jobId)) {
                continue;
            }
            setLauncherEnabled(context, demo.jobId, true);
        }
    }

    /** Re-apply explicit user choices after package updates or process start. */
    public static void applySavedVisibility(@NonNull Context context) {
        SharedPreferences prefs =
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (UdaDemoCatalog.Entry demo : UdaDemoCatalog.all()) {
            if (!prefs.contains(demo.jobId)) {
                continue;
            }
            boolean enabled = prefs.getBoolean(demo.jobId, false);
            setLauncherEnabled(context, demo.jobId, enabled);
        }
    }

    private static void notifyLauncherPackageChanged(
            @NonNull Context app, @NonNull ComponentName changed) {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        intent.setData(Uri.fromParts("package", app.getPackageName(), null));
        intent.putExtra(Intent.EXTRA_DONT_KILL_APP, true);
        intent.putExtra(
                Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST,
                new String[] {changed.flattenToString()});
        app.sendBroadcast(intent);
    }
}
