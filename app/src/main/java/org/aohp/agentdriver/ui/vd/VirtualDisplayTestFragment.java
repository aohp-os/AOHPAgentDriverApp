package org.aohp.agentdriver.ui.vd;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.executor.AohpVdClient;
import org.aohp.agentdriver.executor.MultiVirtualDisplayManager;
import org.aohp.agentdriver.executor.ShellExecutor;
import org.aohp.agentdriver.ui.aohp.AohpDisplaySnapshotAdapter;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 虚拟屏幕测试：创建/销毁 AOHP 逻辑屏、展示 displayId 列表、全屏任务快照。
 */
public class VirtualDisplayTestFragment extends Fragment {

    private static final String TAG = "VirtualDisplayTest";

    /** @hide Matches frameworks/base AOHP permission; not in public SDK stubs. */
    private static final String PERM_MANAGE_AOHP_VD = "android.permission.MANAGE_AOHP_VIRTUAL_DISPLAY";

    private TextView tvTestAohpStatus;
    private TextView tvTestLog;
    private ScrollView svTestLog;
    private MaterialButton btnTestDisplaySnapshot;
    private TextView tvMultiVdList;
    private ShellExecutor shellExecutor;
    private final StringBuilder testLogBuilder = new StringBuilder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void runOnUiThreadIfAdded(Runnable action) {
        Activity a = getActivity();
        if (a == null || !isAdded()) {
            return;
        }
        a.runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            action.run();
        });
    }

    private void initVirtualDisplayTestViews(View root) {
        tvTestAohpStatus = root.findViewById(R.id.tv_test_aohp_status);
        tvTestLog = root.findViewById(R.id.tv_test_log);
        svTestLog = root.findViewById(R.id.sv_test_log);
        btnTestDisplaySnapshot = root.findViewById(R.id.btn_test_display_snapshot);
        tvMultiVdList = root.findViewById(R.id.tv_multi_vd_list);

        root.findViewById(R.id.btn_clear_vd_log).setOnClickListener(v -> {
            testLogBuilder.setLength(0);
            if (tvTestLog != null) {
                tvTestLog.setText("");
            }
            if (svTestLog != null) {
                svTestLog.setVisibility(View.GONE);
            }
        });

        shellExecutor = ShellExecutor.getInstance();
        ShellExecutor.getInstance().bindHostContext(requireContext().getApplicationContext());

        scheduleAohpServiceProbe();
        refreshMultiVdListUi();

        btnTestDisplaySnapshot.setOnClickListener(v -> fetchAndShowAohpDisplaySnapshot());
        root.findViewById(R.id.btn_aohp_create_multi_vd).setOnClickListener(v -> createAohpMultiVd());
        root.findViewById(R.id.btn_aohp_destroy_last_vd).setOnClickListener(v -> destroyLastAohpVd());
    }

    private void createAohpMultiVd() {
        if (!shellExecutor.isAohpVirtualDisplayServiceAvailable()) {
            appendTestLog(getString(R.string.vd_log_cannot_create_no_service));
            Toast.makeText(requireContext(), R.string.vd_toast_service_unavailable, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (!hasManageAohpPermission()) {
            appendTestLog(getString(R.string.vd_log_cannot_create_no_perm));
            Toast.makeText(requireContext(), R.string.vd_toast_need_manage_perm, Toast.LENGTH_LONG)
                    .show();
            return;
        }

        MultiVirtualDisplayManager mgr = MultiVirtualDisplayManager.getInstance(requireContext());
        new Thread(() -> {
            try {
                shellExecutor.enableMultiDisplayFriendlySettings().get();
            } catch (Exception e) {
                Log.w(TAG, "enableMultiDisplayFriendlySettings: " + e.getMessage());
            }
            int[] dm = defaultAohpVdMetricsPx();
            ShellExecutor.CommandResult r = mgr.createDisplay("ui-test-vd", dm[0], dm[1], dm[2], 0);
            runOnUiThreadIfAdded(() -> {
                if (r.success) {
                    appendTestLog("✅ AOHP createVirtualDisplay id=" + r.output);
                    Toast.makeText(requireContext(), "VD id=" + r.output, Toast.LENGTH_SHORT).show();
                } else {
                    appendTestLog("❌ AOHP createVirtualDisplay: " + r.error);
                    Toast.makeText(requireContext(), r.error != null ? r.error : "fail", Toast.LENGTH_LONG).show();
                }
                refreshMultiVdListUi();
            });
        }).start();
    }

    private void destroyLastAohpVd() {
        MultiVirtualDisplayManager mgr = MultiVirtualDisplayManager.getInstance(requireContext());
        Set<Integer> ids = mgr.getKnownDisplayIds();
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), R.string.vd_toast_no_vd_records, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        int last = -1;
        for (int id : ids) {
            last = id;
        }
        final int destroyId = last;
        new Thread(() -> {
            ShellExecutor.CommandResult r = mgr.destroyDisplay(destroyId);
            runOnUiThreadIfAdded(() -> {
                appendTestLog(r.success ? ("✅ destroyed VD " + destroyId) : ("❌ destroy: " + r.error));
                refreshMultiVdListUi();
            });
        }).start();
    }

    private void refreshMultiVdListUi() {
        if (tvMultiVdList == null || getContext() == null) {
            return;
        }
        Set<Integer> ids = MultiVirtualDisplayManager.getInstance(requireContext()).getKnownDisplayIds();
        tvMultiVdList.setText(
                ids.isEmpty() ? getString(R.string.vd_none) : ids.toString());
    }

    private void fetchAndShowAohpDisplaySnapshot() {
        final AohpVdClient client = AohpVdClient.getInstance(requireContext());
        ArrayList<Integer> extras = new ArrayList<>();
        for (int id : MultiVirtualDisplayManager.getInstance(requireContext()).getKnownDisplayIds()) {
            extras.add(id);
        }
        int[] extraDisplayIds = null;
        if (!extras.isEmpty()) {
            extraDisplayIds = new int[extras.size()];
            for (int i = 0; i < extras.size(); i++) {
                extraDisplayIds[i] = extras.get(i);
            }
        }
        btnTestDisplaySnapshot.setEnabled(false);
        final int[] extrasFinal = extraDisplayIds;
        new Thread(() -> {
            ShellExecutor.CommandResult r = client.getDisplayRuntimeSnapshotJson(extrasFinal);
            runOnUiThreadIfAdded(() -> {
                btnTestDisplaySnapshot.setEnabled(true);
                if (!r.success) {
                    Toast.makeText(requireContext(),
                            r.error != null ? r.error : getString(R.string.vd_snapshot_failed),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    List<AohpDisplaySnapshotAdapter.Row> rows =
                            AohpDisplaySnapshotAdapter.parseSnapshotJson(requireContext(), r.output);
                    showAohpSnapshotDialog(rows);
                } catch (JSONException e) {
                    Toast.makeText(requireContext(),
                            getString(R.string.vd_parse_snapshot_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void showAohpSnapshotDialog(List<AohpDisplaySnapshotAdapter.Row> rows) {
        int displayCount = 0;
        for (AohpDisplaySnapshotAdapter.Row row : rows) {
            if (row.viewType() == AohpDisplaySnapshotAdapter.TYPE_HEADER) {
                displayCount++;
            }
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_aohp_snapshot, null, false);
        RecyclerView rv = content.findViewById(R.id.rv_aohp_snapshot);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        AohpDisplaySnapshotAdapter adapter = new AohpDisplaySnapshotAdapter();
        adapter.setRows(rows);
        rv.setAdapter(adapter);
        String title = getString(R.string.vd_snapshot_dialog_title, displayCount);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean hasManageAohpPermission() {
        if (!isAdded() || getContext() == null) {
            return false;
        }
        return requireContext().checkSelfPermission(PERM_MANAGE_AOHP_VD)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void scheduleAohpServiceProbe() {
        final Context app = requireContext().getApplicationContext();
        ShellExecutor.getInstance().bindHostContext(app);
        final int attempts = 24;
        final int stepMs = 500;
        new Thread(
                () -> {
                    ShellExecutor.getInstance().bindHostContext(app);
                    boolean found = false;
                    for (int i = 0; i < attempts; i++) {
                        if (!isAdded()) {
                            return;
                        }
                        if (shellExecutor.isAohpVirtualDisplayServiceAvailable()) {
                            found = true;
                            break;
                        }
                        if (i + 1 >= attempts) {
                            break;
                        }
                        try {
                            Thread.sleep(stepMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    final boolean f = found;
                    runOnUiThreadIfAdded(() -> applyAohpServiceProbeResult(f));
                },
                "aohp-vd-probe")
                .start();
    }

    private void applyAohpServiceProbeResult(boolean serviceFound) {
        final boolean hasPerm = hasManageAohpPermission();
        if (!isAdded() || tvTestAohpStatus == null) {
            return;
        }
        if (serviceFound && hasPerm) {
            tvTestAohpStatus.setText(R.string.vd_status_available);
            tvTestAohpStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            appendTestLog(getString(R.string.vd_log_service_ok));
        } else if (serviceFound) {
            tvTestAohpStatus.setText(R.string.vd_status_no_permission);
            tvTestAohpStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            appendTestLog(getString(R.string.vd_log_missing_perm));
            appendTestLog(getString(R.string.vd_log_use_platform_build));
        } else {
            tvTestAohpStatus.setText(R.string.vd_status_unavailable);
            tvTestAohpStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            appendTestLog(getString(R.string.vd_log_binder_missing));
            appendTestLog(getString(R.string.vd_log_flash_aohp_image));
            appendTestLog(getString(R.string.vd_log_sepolicy_hint, Build.FINGERPRINT));
        }
    }

    private int[] defaultAohpVdMetricsPx() {
        int[] m = shellExecutor.getBuiltinDisplayRealMetricsPx();
        return m != null ? m : new int[]{1080, 1920, 320};
    }

    private void appendTestLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";
        testLogBuilder.append(logLine);

        if (tvTestLog != null) {
            tvTestLog.setText(testLogBuilder.toString());
            if (svTestLog != null) {
                svTestLog.setVisibility(View.VISIBLE);
            }
            tvTestLog.post(() -> {
                if (svTestLog != null) {
                    svTestLog.fullScroll(View.FOCUS_DOWN);
                }
            });
        }

        Log.d(TAG, "[VD Test] " + message);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_virtual_display_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initVirtualDisplayTestViews(view);
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
