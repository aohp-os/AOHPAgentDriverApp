package org.aohp.agentdriver.uda;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes a world-readable registry under {@link UdaConstants#PUBLIC_REGISTRY_PATH} for Launcher /
 * system integrations (P3).
 */
public final class UdaPublicRegistrySync {
    private static final String TAG = "UdaPublicRegistrySync";

    private UdaPublicRegistrySync() {}

    public static void sync(
            @NonNull Context context,
            @NonNull UdaInstallStore installStore,
            @NonNull UdaJobRegistry jobRegistry) {
        try {
            JSONArray installs = new JSONArray();
            List<UdaInstallInfo> list = installStore.list();
            for (UdaInstallInfo inst : list) {
                if (!UdaLauncherSync.isVisibleOnLauncher(context, inst)) {
                    continue;
                }
                UdaJobInfo job = jobRegistry.getJob(inst.jobId);
                JSONObject o = inst.toJson();
                if (job != null) {
                    o.put("appName", job.appName);
                    o.put("idea", job.idea);
                    o.put("hostOutputDir", job.hostOutputDir());
                    o.put(
                            "launchUri",
                            UdaConstants.SCHEME
                                    + "://"
                                    + UdaConstants.HOST_APP
                                    + "/"
                                    + inst.jobId);
                }
                installs.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("updatedAtMs", System.currentTimeMillis());
            root.put("installs", installs);

            File out = new File(UdaConstants.PUBLIC_REGISTRY_PATH);
            File parent = out.getParentFile();
            if (parent != null) {
                // eslint-disable-next-line ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            // Best-effort readability for other UIDs on device images that allow chmod.
            // eslint-disable-next-line ResultOfMethodCallIgnored
            out.setReadable(true, false);
        } catch (Exception e) {
            Log.w(TAG, "sync public registry failed", e);
        }
    }
}
