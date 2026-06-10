package org.aohp.agentdriver.uda;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/** One installed / pinned UDA app record. */
public final class UdaInstallInfo {
    @NonNull public final String jobId;
    @NonNull public String displayName;
    public final long installedAtMs;
    public boolean pinned;
    @Nullable public String shortcutId;
    @NonNull public String runtimeMode;

    public UdaInstallInfo(
            @NonNull String jobId,
            @NonNull String displayName,
            long installedAtMs,
            boolean pinned,
            @Nullable String shortcutId,
            @NonNull String runtimeMode) {
        this.jobId = jobId;
        this.displayName = displayName;
        this.installedAtMs = installedAtMs;
        this.pinned = pinned;
        this.shortcutId = shortcutId;
        this.runtimeMode = runtimeMode;
    }

    @NonNull
    public static UdaInstallInfo fromJson(@NonNull JSONObject o) throws JSONException {
        return new UdaInstallInfo(
                o.getString("jobId"),
                o.optString("displayName", o.getString("jobId")),
                o.optLong("installedAtMs", System.currentTimeMillis()),
                o.optBoolean("pinned", false),
                o.optString("shortcutId", null),
                o.optString("runtimeMode", "asset_loader"));
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("jobId", jobId);
        o.put("displayName", displayName);
        o.put("installedAtMs", installedAtMs);
        o.put("pinned", pinned);
        if (shortcutId != null) {
            o.put("shortcutId", shortcutId);
        }
        o.put("runtimeMode", runtimeMode);
        return o;
    }

    @NonNull
    public static String shortcutIdFor(@NonNull String jobId) {
        return "uda:" + jobId;
    }
}
