package org.aohp.agentdriver.ui.uda;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.uda.UdaConfig;
import org.aohp.agentdriver.uda.UdaJobInfo;
import org.aohp.agentdriver.uda.UdaJobStatus;
import org.aohp.agentdriver.uda.UdaDemoSeeder;
import org.aohp.agentdriver.uda.UdaManager;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdaGeneratorFragment extends Fragment {
    private UdaManager mManager;
    private ExecutorService mBg;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    private TextInputEditText etApiKey;
    private TextInputEditText etModel;
    private TextInputEditText etBaseUrl;
    private TextInputEditText etProvider;
    private TextInputEditText etAppName;
    private TextInputEditText etIdea;
    private TextView tvConfigBanner;
    private TextView tvStage;
    private TextView tvLog;
    private TextView tvJobsEmpty;
    private ProgressBar progress;
    private MaterialButton btnGenerate;
    private MaterialButton btnOpenCurrent;

    private UdaJobAdapter mJobAdapter;
    private String mActiveJobId;
    private String mOpenJobId;

    private final Runnable mPollRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!isAdded() || mActiveJobId == null) {
                        return;
                    }
                    mBg.execute(
                            () -> {
                                try {
                                    JSONObject st =
                                            mManager.status(
                                                    new JSONObject().put("jobId", mActiveJobId));
                                    mMain.post(() -> applyStatus(st));
                                } catch (Exception ignored) {
                                }
                            });
                    if (mActiveJobId != null && isResumed()) {
                        mMain.postDelayed(this, 2000);
                    }
                }
            };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uda_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mBg != null) {
            mBg.shutdownNow();
        }
        mBg = Executors.newSingleThreadExecutor();
        mManager = UdaManager.getInstance(requireContext());

        etApiKey = view.findViewById(R.id.et_api_key);
        etModel = view.findViewById(R.id.et_model);
        etBaseUrl = view.findViewById(R.id.et_base_url);
        etProvider = view.findViewById(R.id.et_provider);
        etAppName = view.findViewById(R.id.et_app_name);
        etIdea = view.findViewById(R.id.et_idea);
        tvConfigBanner = view.findViewById(R.id.tv_config_banner);
        tvStage = view.findViewById(R.id.tv_stage);
        tvLog = view.findViewById(R.id.tv_log);
        tvJobsEmpty = view.findViewById(R.id.tv_jobs_empty);
        progress = view.findViewById(R.id.progress_generation);
        btnGenerate = view.findViewById(R.id.btn_generate);
        btnOpenCurrent = view.findViewById(R.id.btn_open_current);

        MaterialButton btnSave = view.findViewById(R.id.btn_save_config);
        btnSave.setOnClickListener(v -> saveConfig());
        btnGenerate.setOnClickListener(v -> startGenerate());
        btnOpenCurrent.setOnClickListener(
                v -> {
                    if (mOpenJobId != null) {
                        mManager.launchApp(requireContext(), mOpenJobId);
                    }
                });

        RecyclerView rv = view.findViewById(R.id.rv_jobs);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        mJobAdapter = new UdaJobAdapter();
        mJobAdapter.setInstallStore(mManager.installStore());
        mJobAdapter.setListener(
                new UdaJobAdapter.Listener() {
                    @Override
                    public void onOpen(@NonNull UdaJobInfo job) {
                        mManager.launchApp(requireContext(), job.jobId);
                    }

                    @Override
                    public void onPin(
                            @NonNull UdaJobInfo job, boolean installed, boolean pinned) {
                        if (pinned) {
                            unpinFromDesktop(job);
                        } else {
                            pinToDesktop(job, !installed);
                        }
                    }

                    @Override
                    public void onDelete(@NonNull UdaJobInfo job) {
                        confirmDelete(job);
                    }
                });
        rv.setAdapter(mJobAdapter);

        loadConfigIntoUi();
        refreshJobList();
    }

    @Override
    public void onResume() {
        super.onResume();
        mManager.submitIo(
                () -> {
                    UdaDemoSeeder.seedIfNeeded(
                            requireContext().getApplicationContext(), mManager);
                    mMain.post(this::refreshJobList);
                });
        if (mActiveJobId != null) {
            mMain.post(mPollRunnable);
        }
    }

    @Override
    public void onPause() {
        mMain.removeCallbacks(mPollRunnable);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        mMain.removeCallbacks(mPollRunnable);
        if (mBg != null) {
            mBg.shutdownNow();
            mBg = null;
        }
        super.onDestroyView();
    }

    private void loadConfigIntoUi() {
        UdaConfig c = mManager.configStore().load();
        if (c.apiKey != null) {
            etApiKey.setText(c.apiKey);
        }
        etModel.setText(c.model);
        etBaseUrl.setText(c.baseUrl);
        etProvider.setText(c.llmProvider);
        updateConfigBanner(c.isComplete());
    }

    private void saveConfig() {
        try {
            JSONObject p = new JSONObject();
            p.put("apiKey", text(etApiKey));
            p.put("model", text(etModel));
            p.put("baseUrl", text(etBaseUrl));
            p.put("provider", text(etProvider));
            mManager.configSet(p);
            loadConfigIntoUi();
            Toast.makeText(requireContext(), R.string.uda_config_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(
                            requireContext(),
                            getString(R.string.uda_save_failed, e.getMessage()),
                            Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void startGenerate() {
        String idea = text(etIdea);
        if (TextUtils.isEmpty(idea)) {
            Toast.makeText(requireContext(), R.string.uda_fill_idea, Toast.LENGTH_SHORT).show();
            return;
        }
        UdaConfig config = mManager.configStore().load();
        if (!config.isComplete()) {
            Toast.makeText(requireContext(), R.string.uda_save_llm_first, Toast.LENGTH_SHORT).show();
            return;
        }
        String appName = text(etAppName);
        if (TextUtils.isEmpty(appName)) {
            appName = "User Defined App";
        }
        final String finalAppName = appName;
        btnGenerate.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        tvStage.setVisibility(View.VISIBLE);
        tvLog.setVisibility(View.VISIBLE);
        tvStage.setText(R.string.uda_starting);

        mBg.execute(
                () -> {
                    try {
                        JSONObject req = new JSONObject();
                        req.put("appName", finalAppName);
                        req.put("idea", idea);
                        JSONObject res = mManager.generate(req);
                        if (res.optBoolean("error", false)) {
                            mMain.post(
                                    () ->
                                            Toast.makeText(
                                                            requireContext(),
                                                            res.optString(
                                                                    "message",
                                                                    getString(R.string.uda_start_failed)),
                                                            Toast.LENGTH_LONG)
                                                    .show());
                            mMain.post(this::resetGenerateUi);
                            return;
                        }
                        mActiveJobId = res.getString("jobId");
                        mMain.post(
                                () -> {
                                    btnOpenCurrent.setEnabled(false);
                                    mMain.post(mPollRunnable);
                                });
                    } catch (Exception e) {
                        mMain.post(
                                () ->
                                        Toast.makeText(
                                                        requireContext(),
                                                        e.getMessage(),
                                                        Toast.LENGTH_LONG)
                                                .show());
                        mMain.post(this::resetGenerateUi);
                    }
                });
    }

    private void applyStatus(@NonNull JSONObject st) {
        if (st.optBoolean("error", false)) {
            return;
        }
        String status = st.optString("status", "");
        String stage = st.optString("stage", "");
        tvStage.setText(
                getString(
                        R.string.uda_status_format,
                        status,
                        stage.isEmpty() ? "" : getString(R.string.uda_stage_suffix, stage)));
        String log = st.optString("logTail", "");
        if (!log.isEmpty()) {
            tvLog.setText(log);
        }
        boolean done =
                "completed".equalsIgnoreCase(status)
                        || "failed".equalsIgnoreCase(status)
                        || "cancelled".equalsIgnoreCase(status);
        if (done) {
            String finishedJob = st.optString("jobId", mActiveJobId);
            if ("completed".equalsIgnoreCase(status) && finishedJob != null) {
                mOpenJobId = finishedJob;
                btnOpenCurrent.setEnabled(true);
            }
            mActiveJobId = null;
            mMain.removeCallbacks(mPollRunnable);
            resetGenerateUi();
            if ("completed".equalsIgnoreCase(status)) {
                Toast.makeText(requireContext(), R.string.uda_generation_done, Toast.LENGTH_SHORT)
                        .show();
            } else {
                String err =
                        st.optString("errorMessage", getString(R.string.uda_generation_failed));
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
            }
            refreshJobList();
        } else if (st.optBoolean("hasApp", false)) {
            String jobId = st.optString("jobId", mActiveJobId);
            if (jobId != null && !jobId.isEmpty()) {
                mOpenJobId = jobId;
            }
            btnOpenCurrent.setEnabled(true);
        }
    }

    private void resetGenerateUi() {
        progress.setVisibility(View.GONE);
        btnGenerate.setEnabled(true);
    }

    private void refreshJobList() {
        mBg.execute(
                () -> {
                    List<UdaJobInfo> jobs = mManager.registry().listJobs();
                    mMain.post(
                            () -> {
                                mJobAdapter.setJobs(jobs);
                                tvJobsEmpty.setVisibility(jobs.isEmpty() ? View.VISIBLE : View.GONE);
                            });
                });
    }

    private void pinToDesktop(@NonNull UdaJobInfo job, boolean installFirst) {
        mBg.execute(
                () -> {
                    try {
                        if (installFirst) {
                            JSONObject install =
                                    mManager.install(
                                            new JSONObject()
                                                    .put("jobId", job.jobId)
                                                    .put("displayName", job.appName)
                                                    .put("pin", false));
                            if (install.optBoolean("error", false)) {
                                throw new Exception(install.optString("message", "install failed"));
                            }
                        }
                        JSONObject pin =
                                mManager.pin(new JSONObject().put("jobId", job.jobId));
                        if (pin.optBoolean("error", false)) {
                            throw new Exception(pin.optString("message", "pin failed"));
                        }
                        mMain.post(
                                () -> {
                                    Toast.makeText(
                                                    requireContext(),
                                                    pin.optBoolean("ok", false)
                                                            ? getString(R.string.uda_pin_requested)
                                                            : getString(R.string.uda_pin_failed),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                    refreshJobList();
                                });
                    } catch (Exception e) {
                        mMain.post(
                                () ->
                                        Toast.makeText(
                                                        requireContext(),
                                                        e.getMessage(),
                                                        Toast.LENGTH_LONG)
                                                .show());
                    }
                });
    }

    private void unpinFromDesktop(@NonNull UdaJobInfo job) {
        mBg.execute(
                () -> {
                    try {
                        JSONObject result =
                                mManager.unpin(new JSONObject().put("jobId", job.jobId));
                        if (result.optBoolean("error", false)) {
                            throw new Exception(result.optString("message", "unpin failed"));
                        }
                        boolean stillPinned = result.optBoolean("pinned", false);
                        mMain.post(
                                () -> {
                                    if (stillPinned) {
                                        Toast.makeText(
                                                        requireContext(),
                                                        getString(R.string.uda_unpin_failed),
                                                        Toast.LENGTH_LONG)
                                                .show();
                                    } else {
                                        Toast.makeText(
                                                        requireContext(),
                                                        job.demo
                                                                ? getString(R.string.uda_unpin_demo_hidden)
                                                                : getString(
                                                                        R.string
                                                                                .uda_unpin_shortcut_removed),
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                    refreshJobList();
                                });
                    } catch (Exception e) {
                        mMain.post(
                                () ->
                                        Toast.makeText(
                                                        requireContext(),
                                                        e.getMessage(),
                                                        Toast.LENGTH_LONG)
                                                .show());
                    }
                });
    }

    private void confirmDelete(@NonNull UdaJobInfo job) {
        if (job.demo) {
            Toast.makeText(requireContext(), R.string.uda_demo_cannot_delete, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.uda_delete_job_title)
                .setMessage(getString(R.string.uda_delete_job_message, job.appName))
                .setPositiveButton(
                        R.string.uda_job_delete,
                        (d, w) ->
                                mBg.execute(
                                        () -> {
                                            try {
                                                mManager.deleteJob(
                                                        new JSONObject().put("jobId", job.jobId));
                                                mMain.post(this::refreshJobList);
                                            } catch (Exception e) {
                                                mMain.post(
                                                        () ->
                                                                Toast.makeText(
                                                                                requireContext(),
                                                                                e.getMessage(),
                                                                                Toast.LENGTH_LONG)
                                                                        .show());
                                            }
                                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateConfigBanner(boolean configured) {
        tvConfigBanner.setVisibility(configured ? View.GONE : View.VISIBLE);
    }

    @Nullable
    private static String text(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) {
            return "";
        }
        return et.getText().toString().trim();
    }
}
