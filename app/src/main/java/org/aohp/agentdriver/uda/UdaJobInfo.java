package org.aohp.agentdriver.uda;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/** Metadata for one UDA generation job. */
public final class UdaJobInfo {
    @NonNull public final String jobId;
    @NonNull public final String appName;
    @NonNull public final String idea;
    public final long createdAtMs;
    @NonNull public UdaJobStatus status;
    @Nullable public String stage;
    @Nullable public String logTail;
    @Nullable public String errorMessage;
    /** Optional container input dir override for UDAGen (-i). */
    @Nullable public String inputDir;
    /** Built-in demo shipped with the APK; not from user generation. */
    public boolean demo;

    public UdaJobInfo(
            @NonNull String jobId,
            @NonNull String appName,
            @NonNull String idea,
            long createdAtMs,
            @NonNull UdaJobStatus status) {
        this.jobId = jobId;
        this.appName = appName;
        this.idea = idea;
        this.createdAtMs = createdAtMs;
        this.status = status;
    }

    @NonNull
    public String hostOutputDir() {
        return UdaPaths.HOST_SHARED_ROOT + "/" + jobId;
    }

    @NonNull
    public String containerOutputDir() {
        return UdaPaths.CONTAINER_WORKSPACE + "/" + jobId;
    }

    @NonNull
    public String hostAppIndex() {
        return hostOutputDir() + "/app/index.html";
    }

    @NonNull
    public JSONObject toJson(boolean hasApp) throws JSONException {
        return toJson(hasApp, false, false);
    }

    @NonNull
    public JSONObject toJson(boolean hasApp, boolean installed, boolean pinned) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("jobId", jobId);
        o.put("appName", appName);
        o.put("idea", idea);
        o.put("createdAtMs", createdAtMs);
        o.put("status", status.name().toLowerCase());
        o.put("hostOutputDir", hostOutputDir());
        o.put("containerOutputDir", containerOutputDir());
        o.put("hostInputDir", UdaPaths.jobInputHostPath(jobId));
        o.put("containerInputDir", UdaPaths.jobInputContainerPath(jobId));
        if (inputDir != null) {
            o.put("inputDir", inputDir);
        }
        if (stage != null) {
            o.put("stage", stage);
        }
        if (logTail != null) {
            o.put("logTail", logTail);
        }
        if (errorMessage != null) {
            o.put("errorMessage", errorMessage);
        }
        o.put("hasApp", hasApp);
        o.put("installed", installed);
        o.put("pinned", pinned);
        o.put("demo", demo);
        if (hasApp) {
            o.put("launchUri", UdaInstallManager.launchUri(jobId));
        }
        return o;
    }
}
