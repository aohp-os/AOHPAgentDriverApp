package org.aohp.agentdriver.system;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.aohp.agentdriver.executor.MyAccessibilityService;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Optional integration for platform / privileged ({@code priv-app}) builds:
 * enables this package's {@link MyAccessibilityService} via {@link Settings.Secure} so the user
 * does not have to toggle it manually. Requires {@link android.Manifest.permission#WRITE_SECURE_SETTINGS}.
 */
public final class SystemPrivilegeBootstrap {
    private static final String TAG = "SystemPrivilegeBootstrap";
    private SystemPrivilegeBootstrap() {}

    /** Allow {@link android.provider.Settings#canDrawOverlays} for demo / agent overlays on priv-app builds. */
    public static boolean ensureSystemAlertWindowAllowed(Context context) {
        Context app = context.getApplicationContext();
        AppOpsManager appOps = app.getSystemService(AppOpsManager.class);
        if (appOps == null) {
            return false;
        }
        int uid = Process.myUid();
        String pkg = app.getPackageName();
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg);
        if (mode == AppOpsManager.MODE_ALLOWED) {
            return false;
        }
        try {
            appOps.getClass()
                    .getMethod("setMode", String.class, int.class, String.class, int.class)
                    .invoke(appOps, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg,
                            AppOpsManager.MODE_ALLOWED);
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "Failed to allow SYSTEM_ALERT_WINDOW for " + pkg, e);
            return false;
        }
        Log.i(TAG, "SYSTEM_ALERT_WINDOW appops set to allow for " + pkg);
        return true;
    }

    /**
     * If {@code WRITE_SECURE_SETTINGS} is granted, merges our accessibility service into
     * {@link Settings.Secure#ENABLED_ACCESSIBILITY_SERVICES} and sets
     * {@link Settings.Secure#ACCESSIBILITY_ENABLED} to 1.
     *
     * @return true if a change was written to Settings
     */
    public static boolean ensureAccessibilityServiceEnabledInSecureSettings(Context context) {
        Context app = context.getApplicationContext();
        if (app.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted; skip secure-settings accessibility bootstrap");
            return false;
        }

        String flat = new ComponentName(app.getPackageName(), MyAccessibilityService.class.getName())
                .flattenToString();

        String existing = Settings.Secure.getString(
                app.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        Set<String> services = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(existing)) {
            for (String part : existing.split(":")) {
                if (!TextUtils.isEmpty(part)) {
                    services.add(part);
                }
            }
        }
        if (services.contains(flat)) {
            try {
                int enabled = Settings.Secure.getInt(
                        app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
                if (enabled == 1) {
                    return false;
                }
            } catch (Settings.SettingNotFoundException ignored) {
            }
        } else {
            services.add(flat);
        }

        String merged = TextUtils.join(":", services.toArray(new String[0]));
        boolean ok = Settings.Secure.putString(
                app.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                merged);
        if (!ok) {
            Log.e(TAG, "Failed to write ENABLED_ACCESSIBILITY_SERVICES");
            return false;
        }
        ok = Settings.Secure.putInt(app.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        if (!ok) {
            Log.e(TAG, "Failed to write ACCESSIBILITY_ENABLED");
            return false;
        }
        Log.i(TAG, "Accessibility enabled in Secure settings for " + flat);
        return true;
    }
}
