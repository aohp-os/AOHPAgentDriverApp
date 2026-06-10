package org.aohp.agentdriver.uda;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.executor.AohpContainerClient;
import org.aohp.agentdriver.executor.AohpServiceInfo;
import org.aohp.agentdriver.executor.ShellExecutor;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Runs `python3 -m udagen run` inside the dedicated UDA Linux container. */
public final class UdaGenerationEngine {
    private static final String TAG = "UdaGenerationEngine";

    private final Context mAppContext;
    private final AohpContainerClient mContainer;
    private final UdaContainerFs mFs;
    private final UdaJobRegistry mRegistry;

    public UdaGenerationEngine(@NonNull Context context, @NonNull UdaJobRegistry registry) {
        mAppContext = context.getApplicationContext();
        mContainer = new AohpContainerClient(mAppContext);
        mFs = new UdaContainerFs(mContainer);
        mRegistry = registry;
    }

    public boolean isContainerServiceAvailable() {
        return mContainer.isServiceAvailable();
    }

    /** Ensures the dedicated {@code uda} alpine container exists. */
    public boolean ensureUdaContainer() {
        if (!mContainer.isServiceAvailable()) {
            return false;
        }
        String[] names = mContainer.listContainers();
        if (names != null) {
            for (String n : names) {
                if (UdaPaths.CONTAINER_NAME.equals(n)) {
                    return true;
                }
            }
        }
        ShellExecutor.CommandResult r =
                mContainer.createContainer(UdaPaths.CONTAINER_NAME, UdaPaths.CONTAINER_TEMPLATE);
        if (!r.success) {
            Log.e(TAG, "create uda container failed: " + r.error);
        }
        return r.success;
    }

    /** Point container DNS at Android netd (127.0.0.1) when host resolv is not bind-mounted. */
    private void ensureContainerDns() {
        if (!mContainer.isServiceAvailable()) {
            return;
        }
        ShellExecutor.CommandResult r =
                mContainer.execSync(
                        UdaPaths.CONTAINER_NAME,
                        "printf 'nameserver 127.0.0.1\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf",
                        5000);
        if (!r.success) {
            Log.w(TAG, "ensureContainerDns failed: " + r.error);
        }
    }

    @NonNull
    public String newJobId() {
        return "job-" + System.currentTimeMillis();
    }

    @NonNull
    private static String outputDir(@NonNull String jobId) {
        return UdaPaths.CONTAINER_WORKSPACE + "/" + jobId;
    }

  /** Prefer per-job input when the agent staged files under workspace/{jobId}/input. */
    @NonNull
    public String resolveGenerationInputDir(@NonNull String jobId, @Nullable String override) {
        if (override != null && !override.trim().isEmpty()) {
            return override.trim();
        }
        String jobInput = UdaPaths.jobInputContainerPath(jobId);
        if (hasUsableInputDir(jobInput)) {
            return jobInput;
        }
        return UdaPaths.CONTAINER_INPUT;
    }

    /** True when {@code containerDir} contains at least one UDAGen-supported input file. */
    public boolean hasUsableInputDir(@NonNull String containerDir) {
        if (!mFs.isDirectory(containerDir)) {
            return false;
        }
        String quoted = UdaShellUtils.shellQuote(containerDir);
        ShellExecutor.CommandResult r =
                mContainer.execSync(
                        UdaPaths.CONTAINER_NAME,
                        "find "
                                + quoted
                                + " -type f \\( -name '*.md' -o -name '*.txt' -o -name '*.json' "
                                + "-o -name '*.yaml' -o -name '*.yml' -o -name '*.csv' -o -name '*.tsv' \\) "
                                + "| head -n 1",
                        10000);
        return r.success && r.output != null && !r.output.trim().isEmpty();
    }

    @NonNull
    public String buildGenerationCommand(
            @NonNull UdaConfig config,
            @NonNull String jobId,
            @NonNull String appName,
            @NonNull String idea,
            @Nullable String inputDirOverride) {
        String out = outputDir(jobId);
        String inputDir = resolveGenerationInputDir(jobId, inputDirOverride);
        // Alpine /bin/sh (ash): inline env vars; skip LiteLLM GitHub model-cost fetch.
        return String.format(
                Locale.US,
                "mkdir -p %s && "
                        + "PYTHONPATH=%s "
                        + "LITELLM_LOCAL_MODEL_COST_MAP=True "
                        + "UDA_API_KEY=%s UDA_MODEL=%s UDA_BASE_URL=%s UDA_LLM_PROVIDER=%s "
                        + "python3 -m udagen run "
                        + "-i %s -o %s "
                        + "--app-name %s --idea %s --log-json "
                        + "> %s/run.log 2>&1; "
                        + "echo $? > %s/exit.code",
                UdaShellUtils.shellQuote(out),
                UdaShellUtils.shellQuote(UdaPaths.CONTAINER_PYTHONPATH),
                UdaShellUtils.shellQuote(config.apiKey),
                UdaShellUtils.shellQuote(config.model),
                UdaShellUtils.shellQuote(config.baseUrl),
                UdaShellUtils.shellQuote(config.llmProvider),
                UdaShellUtils.shellQuote(inputDir),
                UdaShellUtils.shellQuote(out),
                UdaShellUtils.shellQuote(appName),
                UdaShellUtils.shellQuote(idea),
                UdaShellUtils.shellQuote(out),
                UdaShellUtils.shellQuote(out));
    }

    public long startGenerationService(
            @NonNull String jobId,
            @NonNull UdaConfig config,
            @NonNull String appName,
            @NonNull String idea,
            @Nullable String inputDirOverride) {
        if (!ensureUdaContainer()) {
            return -1;
        }
        ensureContainerDns();
        String cmd = buildGenerationCommand(config, jobId, appName, idea, inputDirOverride);
        return mContainer.startService(
                UdaPaths.CONTAINER_NAME, UdaPreviewHelper.serviceIdGenerate(jobId), cmd);
    }

    /** Scaffold per-job input; optionally seed from baked template-input. */
    @NonNull
    public JSONObject initJobInput(@NonNull String jobId, boolean fromTemplate) throws JSONException {
        if (!ensureUdaContainer()) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "container_unavailable");
            err.put("message", "aohp_container service not available");
            return err;
        }
        String containerInput = UdaPaths.jobInputContainerPath(jobId);
        mFs.ensureDirectory(containerInput);
        if (fromTemplate && !hasUsableInputDir(containerInput)) {
            ShellExecutor.CommandResult r =
                    mContainer.execSync(
                            UdaPaths.CONTAINER_NAME,
                            "cp -a "
                                    + UdaShellUtils.shellQuote(UdaPaths.CONTAINER_INPUT + "/.")
                                    + " "
                                    + UdaShellUtils.shellQuote(containerInput + "/"),
                            30000);
            if (!r.success) {
                Log.w(TAG, "initJobInput template copy failed: " + r.error);
            }
        }
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("jobId", jobId);
        o.put("containerInputDir", containerInput);
        o.put("hostInputDir", UdaPaths.jobInputHostPath(jobId));
        o.put("hasInput", hasUsableInputDir(containerInput));
        return o;
    }

    @NonNull
    public JSONObject writeJobInputFile(
            @NonNull String jobId, @NonNull String relativePath, @NonNull String content)
            throws JSONException {
        if (!ensureUdaContainer()) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "container_unavailable");
            err.put("message", "aohp_container service not available");
            return err;
        }
        String rel = relativePath.replace('\\', '/').trim();
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        if (rel.isEmpty() || rel.contains("..")) {
            JSONObject err = new JSONObject();
            err.put("error", true);
            err.put("code", "invalid_path");
            err.put("message", "relative path must be non-empty and must not contain ..");
            return err;
        }
        String containerInput = UdaPaths.jobInputContainerPath(jobId);
        mFs.ensureDirectory(containerInput);
        String fullPath = containerInput + "/" + rel;
        boolean ok = mFs.writeTextFile(fullPath, content);
        JSONObject o = new JSONObject();
        o.put("ok", ok);
        o.put("jobId", jobId);
        o.put("path", rel);
        o.put("containerPath", fullPath);
        o.put("hostPath", UdaPaths.jobInputHostPath(jobId) + "/" + rel);
        o.put("hasInput", hasUsableInputDir(containerInput));
        if (!ok) {
            o.put("error", true);
            o.put("code", "write_failed");
            o.put("message", "failed to write input file in uda container");
        }
        return o;
    }

    public void deleteJobOutput(@NonNull String jobId) {
        if (mContainer.isServiceAvailable()) {
            mFs.deleteRecursive(outputDir(jobId));
        }
    }

    public boolean isGenerationServiceAlive(@NonNull String jobId) {
        return findGenerationService(jobId) != null;
    }

    /** True once the shell wrapper wrote {@code exit.code} (generation finished or crashed). */
    public boolean isGenerationTerminal(@NonNull String jobId) {
        return readExitCode(jobId) != null;
    }

    /** Output dir has pipeline artifacts but generation may still be running. */
    public boolean hasGenerationActivity(@NonNull String jobId) {
        String root = outputDir(jobId);
        return mFs.isFile(root + "/run.log")
                || mFs.isFile(root + "/udagen.log")
                || mFs.isFile(root + "/prd.json")
                || mFs.isDirectory(root + "/mock_docs");
    }

    @Nullable
    private AohpServiceInfo findGenerationService(@NonNull String jobId) {
        if (!mContainer.isServiceAvailable()) {
            return null;
        }
        String sid = UdaPreviewHelper.serviceIdGenerate(jobId);
        List<AohpServiceInfo> services =
                AohpServiceInfo.listFromJson(mContainer.listServicesJson(UdaPaths.CONTAINER_NAME));
        for (AohpServiceInfo svc : services) {
            if (sid.equals(svc.serviceId) && svc.alive) {
                return svc;
            }
        }
        return null;
    }

    /**
     * Updates {@code job.status} from container state. Avoids marking failed while the udagen
     * process is still running but not yet visible in {@code listServices}.
     */
    public void reconcileJobStatus(@NonNull UdaJobInfo job) {
        if (hasAppIndex(job.jobId)) {
            job.status = UdaJobStatus.COMPLETED;
            job.errorMessage = null;
            return;
        }
        if (isGenerationServiceAlive(job.jobId) || hasGenerationActivity(job.jobId)) {
            if (job.status == UdaJobStatus.FAILED || job.status == UdaJobStatus.PENDING) {
                job.status = UdaJobStatus.RUNNING;
                job.errorMessage = null;
            }
            return;
        }
        if (!isGenerationTerminal(job.jobId)) {
            long elapsed = System.currentTimeMillis() - job.createdAtMs;
            if (elapsed < UdaPaths.SERVICE_START_GRACE_MS
                    && (job.status == UdaJobStatus.PENDING || job.status == UdaJobStatus.RUNNING)) {
                job.status = UdaJobStatus.RUNNING;
                job.errorMessage = null;
            }
            return;
        }
        String exit = readExitCode(job.jobId);
        job.status = UdaJobStatus.FAILED;
        job.errorMessage = exit != null ? "exit code " + exit : "generation failed";
    }

    private boolean shouldKeepPolling(@NonNull UdaJobInfo job) {
        if (hasAppIndex(job.jobId)) {
            return false;
        }
        if (isGenerationServiceAlive(job.jobId)) {
            return true;
        }
        if (!isGenerationTerminal(job.jobId)) {
            if (hasGenerationActivity(job.jobId)) {
                return true;
            }
            long elapsed = System.currentTimeMillis() - job.createdAtMs;
            return elapsed < UdaPaths.SERVICE_START_GRACE_MS;
        }
        return false;
    }

    @NonNull
    public String fetchGenerationLog(@NonNull String jobId, int tailBytes) {
        return mContainer.serviceLog(
                UdaPaths.CONTAINER_NAME, UdaPreviewHelper.serviceIdGenerate(jobId), tailBytes);
    }

    @NonNull
    public String readContainerLogTail(@NonNull String jobId, int maxChars) {
        String out = outputDir(jobId);
        String log = mFs.readTextTail(out + "/run.log", maxChars);
        if (log.isEmpty()) {
            log = mFs.readTextTail(out + "/udagen.log", maxChars);
        }
        return log;
    }

    public boolean hasAppIndex(@NonNull String jobId) {
        // Partial app/ output during udagen refinement is not launch-ready yet.
        if (isGenerationServiceAlive(jobId)) {
            return false;
        }
        if (!isGenerationTerminal(jobId) && hasGenerationActivity(jobId)) {
            return false;
        }
        if (UdaHostFiles.isJobReadable(mAppContext, jobId)) {
            UdaHostFiles.ensureJobReadable(mAppContext, mFs, jobId);
            return UdaHostFiles.isJobReadable(mAppContext, jobId);
        }
        if (mFs.isFile(outputDir(jobId) + "/app/index.html")) {
            UdaHostFiles.ensureJobReadable(mAppContext, mFs, jobId);
            return UdaHostFiles.isJobReadable(mAppContext, jobId);
        }
        return false;
    }

    @Nullable
    public String readExitCode(@NonNull String jobId) {
        String text = mFs.readSmallText(outputDir(jobId) + "/exit.code");
        return text.isEmpty() ? null : text.trim();
    }

    @NonNull
    public String inferStage(@NonNull String jobId) {
        String root = outputDir(jobId);
        if (mFs.isFile(root + "/app/index.html")) {
            return "build";
        }
        if (mFs.isFile(root + "/design_spec.json")) {
            return "draft";
        }
        if (mFs.isFile(root + "/prd.json")) {
            return "prd";
        }
        if (mFs.isDirectory(root + "/mock_docs")) {
            return "mock";
        }
        return "starting";
    }

    public void stopGeneration(@NonNull String jobId) {
        mContainer.stopService(
                UdaPaths.CONTAINER_NAME, UdaPreviewHelper.serviceIdGenerate(jobId));
    }

    /** Poll generation until completion, timeout, or cancel. Updates registry entry in place. */
    public void pollUntilDone(
            @NonNull UdaJobInfo job,
            long timeoutMs,
            @Nullable Runnable onTick) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (Thread.currentThread().isInterrupted()) {
                job.status = UdaJobStatus.CANCELLED;
                stopGeneration(job.jobId);
                mRegistry.updateJob(job);
                return;
            }
            job.stage = inferStage(job.jobId);
            String svcLog = fetchGenerationLog(job.jobId, 8192);
            String containerLog = readContainerLogTail(job.jobId, 12000);
            job.logTail = mergeLogTail(svcLog, containerLog);
            if (hasAppIndex(job.jobId)) {
                job.status = UdaJobStatus.COMPLETED;
                mRegistry.updateJob(job);
                return;
            }
            if (shouldKeepPolling(job)) {
                mRegistry.updateJob(job);
                if (onTick != null) {
                    onTick.run();
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    job.status = UdaJobStatus.CANCELLED;
                    stopGeneration(job.jobId);
                    mRegistry.updateJob(job);
                    return;
                }
                continue;
            }
            if (isGenerationTerminal(job.jobId)) {
                String exit = readExitCode(job.jobId);
                if (hasAppIndex(job.jobId)) {
                    job.status = UdaJobStatus.COMPLETED;
                } else {
                    job.status = UdaJobStatus.FAILED;
                    job.errorMessage =
                            exit != null
                                    ? "exit code " + exit
                                    : "generation finished without app output";
                }
                mRegistry.updateJob(job);
                return;
            }
            job.status = UdaJobStatus.FAILED;
            job.errorMessage = "generation stopped before producing output";
            mRegistry.updateJob(job);
            return;
        }
        job.status = UdaJobStatus.FAILED;
        job.errorMessage = "generation timed out";
        stopGeneration(job.jobId);
        mRegistry.updateJob(job);
    }

    @NonNull
    public String mergeLogTailPublic(@Nullable String a, @Nullable String b) {
        return mergeLogTail(a, b);
    }

    @NonNull
    private static String mergeLogTail(@Nullable String a, @Nullable String b) {
        StringBuilder sb = new StringBuilder();
        if (a != null && !a.isEmpty()) {
            sb.append(a.trim());
        }
        if (b != null && !b.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(b.trim());
        }
        String merged = sb.toString();
        if (merged.length() > 16000) {
            return merged.substring(merged.length() - 16000);
        }
        return merged;
    }
}
