package org.aohp.agentdriver.uda;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Facade shared by UI, foreground service, and JSON-RPC handlers. */
public final class UdaManager {
    private static volatile UdaManager sInstance;

    private final Context mAppContext;
    private final UdaConfigStore mConfigStore;
    private final UdaJobRegistry mRegistry;
    private final UdaGenerationEngine mEngine;
    private final UdaPreviewHelper mPreview;
    private final UdaInstallStore mInstallStore;
    private final UdaInstallManager mInstallManager;
    private final ExecutorService mBg = Executors.newCachedThreadPool();

    private UdaManager(Context context) {
        mAppContext = context.getApplicationContext();
        mConfigStore = new UdaConfigStore(mAppContext);
        mRegistry = new UdaJobRegistry(mAppContext);
        mEngine = new UdaGenerationEngine(mAppContext, mRegistry);
        mPreview = new UdaPreviewHelper(new org.aohp.agentdriver.executor.AohpContainerClient(mAppContext));
        mInstallStore = new UdaInstallStore(mAppContext);
        mInstallManager = new UdaInstallManager(mAppContext, mInstallStore, mRegistry, mEngine);
        UdaDemoLauncher.applySavedVisibility(mAppContext);
        UdaDemoLauncher.ensureDefaultVisible(mAppContext);
        UdaPublicRegistrySync.sync(mAppContext, mInstallStore, mRegistry);
    }

    @NonNull
    public static UdaManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (UdaManager.class) {
                if (sInstance == null) {
                    sInstance = new UdaManager(context);
                }
            }
        }
        return sInstance;
    }

    @NonNull
    public UdaConfigStore configStore() {
        return mConfigStore;
    }

    @NonNull
    public UdaJobRegistry registry() {
        return mRegistry;
    }

    @NonNull
    public UdaGenerationEngine engine() {
        return mEngine;
    }

    @NonNull
    public UdaInstallStore installStore() {
        return mInstallStore;
    }

    @NonNull
    public UdaInstallManager installManager() {
        return mInstallManager;
    }

    public boolean canLaunch(@NonNull String jobId) {
        return mInstallManager.canLaunch(jobId);
    }

    @NonNull
    public JSONObject configGet() throws JSONException {
        UdaConfig c = mConfigStore.load();
        JSONObject o = new JSONObject();
        o.put("apiKey", c.maskedApiKey());
        o.put("model", c.model);
        o.put("baseUrl", c.baseUrl);
        o.put("provider", c.llmProvider);
        o.put("configured", c.isComplete());
        return o;
    }

    @NonNull
    public JSONObject configSet(@NonNull JSONObject p) throws JSONException {
        UdaConfig prev = mConfigStore.load();
        String apiKey = p.has("apiKey") ? p.optString("apiKey", null) : prev.apiKey;
        if (apiKey != null && apiKey.contains("****")) {
            apiKey = prev.apiKey;
        }
        UdaConfig next =
                new UdaConfig(
                        apiKey,
                        p.optString("model", prev.model),
                        p.optString("baseUrl", prev.baseUrl),
                        p.optString("provider", p.optString("llmProvider", prev.llmProvider)));
        mConfigStore.save(next);
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("configured", next.isComplete());
        return o;
    }

    @NonNull
    public JSONObject generate(@NonNull JSONObject p) throws JSONException {
        UdaConfig config = mConfigStore.load();
        if (!config.isComplete()) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "config_incomplete");
            err.put("message", "LLM config incomplete; set apiKey, model, baseUrl");
            return err;
        }
        if (!mEngine.isContainerServiceAvailable()) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "container_unavailable");
            err.put("message", "aohp_container service not available");
            return err;
        }
        String jobId = p.optString("jobId", mEngine.newJobId());
        String appName = p.optString("appName", "User Defined App");
        String idea = p.getString("idea");
        String inputDir = p.has("inputDir") ? p.optString("inputDir", null) : null;
        if (inputDir != null && inputDir.trim().isEmpty()) {
            inputDir = null;
        }
        UdaJobInfo job =
                new UdaJobInfo(
                        jobId,
                        appName,
                        idea,
                        System.currentTimeMillis(),
                        UdaJobStatus.PENDING);
        job.inputDir = inputDir;
        mRegistry.addJob(job);

        Intent intent =
                new Intent(mAppContext, UdaGenerationService.class)
                        .setAction(UdaGenerationService.ACTION_START)
                        .putExtra(UdaGenerationService.EXTRA_JOB_ID, jobId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAppContext.startForegroundService(intent);
        } else {
            mAppContext.startService(intent);
        }

        JSONObject o = new JSONObject();
        o.put("jobId", jobId);
        o.put("status", "started");
        return o;
    }

    /** Called from {@link UdaGenerationService} background thread. */
    public void runGenerationBlocking(@NonNull String jobId) {
        UdaJobInfo job = mRegistry.getJob(jobId);
        if (job == null) {
            return;
        }
        UdaConfig config = mConfigStore.load();
        if (!config.isComplete()) {
            job.status = UdaJobStatus.FAILED;
            job.errorMessage = "LLM config incomplete";
            mRegistry.updateJob(job);
            return;
        }
        job.status = UdaJobStatus.RUNNING;
        mRegistry.updateJob(job);
        long pid =
                mEngine.startGenerationService(
                        jobId, config, job.appName, job.idea, job.inputDir);
        if (pid <= 0) {
            job.status = UdaJobStatus.FAILED;
            job.errorMessage = "failed to start udagen service in container";
            mRegistry.updateJob(job);
            return;
        }
        mEngine.pollUntilDone(job, UdaPaths.GENERATION_TIMEOUT_MS, null);
    }

    @NonNull
    public JSONObject inputInit(@NonNull JSONObject p) throws JSONException {
        String jobId = p.optString("jobId", mEngine.newJobId());
        boolean fromTemplate = p.optBoolean("fromTemplate", true);
        return mEngine.initJobInput(jobId, fromTemplate);
    }

    @NonNull
    public JSONObject inputWrite(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        String path = p.getString("path");
        String content = p.getString("content");
        return mEngine.writeJobInputFile(jobId, path, content);
    }

    @NonNull
    public JSONObject status(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        UdaJobInfo job = mRegistry.getJob(jobId);
        if (job == null) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "not_found");
            err.put("message", "unknown jobId: " + jobId);
            return err;
        }
        job.stage = mEngine.inferStage(jobId);
        job.logTail =
                mEngine.mergeLogTailPublic(
                        mEngine.fetchGenerationLog(jobId, 8192),
                        mEngine.readContainerLogTail(jobId, 12000));
        mEngine.reconcileJobStatus(job);
        mRegistry.updateJob(job);
        return jobJson(job);
    }

    @NonNull
    private JSONObject jobJson(@NonNull UdaJobInfo job) throws JSONException {
        UdaInstallInfo inst = mInstallStore.get(job.jobId);
        boolean hasApp = mEngine.hasAppIndex(job.jobId);
        boolean installed = job.demo ? hasApp : inst != null;
        boolean pinned =
                job.demo
                        ? UdaDemoLauncher.isLauncherEnabled(mAppContext, job.jobId)
                        : (inst != null && inst.pinned);
        return job.toJson(hasApp, installed, pinned);
    }

    @NonNull
    public JSONObject listJobsJson() throws JSONException {
        List<UdaJobInfo> jobs = mRegistry.listJobs();
        JSONArray arr = new JSONArray();
        for (UdaJobInfo j : jobs) {
            if (j.demo
                    && !UdaDemoLauncher.isLauncherEnabled(mAppContext, j.jobId)) {
                continue;
            }
            mEngine.reconcileJobStatus(j);
            mRegistry.updateJob(j);
            arr.put(jobJson(j));
        }
        JSONObject o = new JSONObject();
        o.put("jobs", arr);
        return o;
    }

    @NonNull
    public JSONObject deleteJob(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        mInstallManager.uninstall(new JSONObject().put("jobId", jobId));
        mPreview.stopPreviewServices(jobId);
        mEngine.stopGeneration(jobId);
        mEngine.deleteJobOutput(jobId);
        boolean ok = mRegistry.removeJob(jobId);
        JSONObject o = new JSONObject();
        o.put("ok", ok);
        o.put("jobId", jobId);
        return o;
    }

    public void launchPreview(@NonNull Context context, @NonNull String jobId) {
        mBg.execute(
                () -> {
                    UdaJobInfo job = mRegistry.getJob(jobId);
                    if (job == null || !mEngine.hasAppIndex(jobId)) {
                        return;
                    }
                    boolean ok = mPreview.startPreviewServices(job);
                    if (!ok) {
                        new Handler(Looper.getMainLooper())
                                .post(
                                        () ->
                                                Toast.makeText(
                                                                context.getApplicationContext(),
                                                                context.getString(
                                                                        R.string
                                                                                .uda_preview_start_failed),
                                                                Toast.LENGTH_SHORT)
                                                        .show());
                        return;
                    }
                    Intent intent =
                            new Intent(context, org.aohp.agentdriver.ui.uda.UdaPreviewActivity.class)
                                    .putExtra(
                                            org.aohp.agentdriver.ui.uda.UdaPreviewActivity.EXTRA_JOB_ID,
                                            jobId)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(intent);
                });
    }

    @NonNull
    public JSONObject preview(@NonNull JSONObject p) throws JSONException {
        String jobId = p.getString("jobId");
        UdaJobInfo job = mRegistry.getJob(jobId);
        if (job == null) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "not_found");
            err.put("message", "unknown jobId");
            return err;
        }
        if (!mEngine.hasAppIndex(jobId)) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "not_ready");
            err.put("message", "app/index.html not generated yet");
            return err;
        }
        launchPreview(mAppContext, jobId);
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("previewUrl", UdaPreviewHelper.previewUrl());
        o.put("jobId", jobId);
        return o;
    }

    public void launchApp(@NonNull Context context, @NonNull String jobId) {
        mInstallManager.launch(context, jobId);
    }

    @NonNull
    public JSONObject launch(@NonNull JSONObject p) throws JSONException {
        return mInstallManager.launchJson(p);
    }

    @NonNull
    public JSONObject install(@NonNull JSONObject p) throws JSONException {
        return mInstallManager.install(p);
    }

    @NonNull
    public JSONObject uninstall(@NonNull JSONObject p) throws JSONException {
        return mInstallManager.uninstall(p);
    }

    @NonNull
    public JSONObject unpin(@NonNull JSONObject p) throws JSONException {
        return mInstallManager.unpin(p);
    }

    @NonNull
    public JSONObject pin(@NonNull JSONObject p) throws JSONException {
        return mInstallManager.pin(p);
    }

    public void submitIo(@NonNull Runnable task) {
        mBg.execute(task);
    }
}
