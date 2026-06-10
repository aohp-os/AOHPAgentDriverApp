package org.aohp.agentdriver;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.os.Build;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import androidx.navigation.fragment.NavHostFragment;

import org.aohp.agentdriver.executor.MyAccessibilityService;
import org.aohp.agentdriver.executor.ExecutorManager;
import org.aohp.agentdriver.executor.ExecutionModeManager;
import org.aohp.agentdriver.executor.MultiVirtualDisplayManager;
import org.aohp.agentdriver.databinding.ActivityMainBinding;
import org.aohp.agentdriver.permission.PermissionManager;

import org.aohp.agentdriver.executor.AohpVdClient;
import org.aohp.agentdriver.executor.ShellExecutor;
import org.aohp.agentdriver.ui.aohp.AohpDisplaySnapshotAdapter;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    /** @hide AOHP virtual display permission; not in public SDK stubs. */
    private static final String PERM_MANAGE_AOHP_VD = "android.permission.MANAGE_AOHP_VIRTUAL_DISPLAY";

    // 保留这些静态变量
    public static ArrayList<String> appNameAll;
    public static ArrayList<String> appPkgAll;
    public static ArrayList<String> appLauncherAll;
    public static Map<String, List<String>> appNameLocalesMap;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    public static final int RECORD_REQUEST_CODE = 101;

    private ActivityMainBinding binding;
    private Menu optionsMenu;
    private NavController navController;
    
    // 权限请求队列
    private List<PermissionManager.PermissionType> pendingPermissions = new ArrayList<>();

    private static final String TAG = "MainActivity";

    // 供后台模块获取当前前台 Activity（用于“前台冻结遮罩”抑制闪烁）
    private static volatile WeakReference<MainActivity> activeInstance = new WeakReference<>(null);

    public static MainActivity getActiveInstance() {
        return activeInstance.get();
    }
    
    // 快捷启动相关
    private TextInputEditText etTaskInput;
    private MaterialButton btnStartTask;
    private LinearLayout layoutExecutionStatus;
    private TextView tvExecutionStatus;
    private TextView tvExecutionLog;
    private String pendingTaskDescription;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * 程序化 setSelectedItemId 会再次触发 OnItemSelectedListener，进而再次 NavigationUI.onNavDestinationSelected，
     * 与 onDestinationChanged 形成递归导航直至栈溢出。同步 UI 选中态时置 true，监听器内直接消费、不再 navigate。
     */
    private boolean suppressNavigationBarReselectCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeInstance = new WeakReference<>(this);
        
        // 使用 ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.mainToolbar);
        setTitle(R.string.app_name);

        // 正确初始化导航控制器
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            
            // 添加导航状态监听
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                Log.d(TAG, "Navigation changed to: " + destination.getLabel());
                syncNavigationBarSelection(destination.getId());
            });
        } else {
            Log.e(TAG, "NavHostFragment not found!");
        }

        // 根据屏幕宽度选择导航方式
        if (shouldUseNavigationRail()) {
            setupNavigationRail();
        } else {
            setupBottomNavigation();
        }
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // ✅ 初始化快捷启动视图
        initQuickStartViews();
        
        // ✅ 使用 PermissionManager 检查并请求权限
        checkAndRequestPermissions();
        
        // ✅ 检查是否有待恢复的权限（从通知点击进入）
        handlePermissionRestore();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeInstance = new WeakReference<>(this);
    }
    
    /**
     * 初始化快捷启动任务视图
     */
    private void initQuickStartViews() {
        etTaskInput = findViewById(R.id.et_task_input);
        btnStartTask = findViewById(R.id.btn_start_task);
        layoutExecutionStatus = findViewById(R.id.layout_execution_status);
        tvExecutionStatus = findViewById(R.id.tv_execution_status);
        tvExecutionLog = findViewById(R.id.tv_execution_log);
        
        // 初始化 MediaProjectionManager
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        
        btnStartTask.setOnClickListener(v -> {
            String taskDescription = etTaskInput.getText() != null ? etTaskInput.getText().toString().trim() : "";
            if (taskDescription.isEmpty()) {
                Toast.makeText(this, R.string.toast_enter_task_description, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            
            // 保存任务描述
            pendingTaskDescription = taskDescription;
            
            // 开始执行快捷启动流程
            startQuickExecution();
        });
    }
    
    /**
     * 开始快捷执行流程：启动 WebSocket → 通过 AOHP 创建逻辑虚拟屏 → 执行任务（不再使用 MediaProjection 虚拟屏服务）。
     */
    private void startQuickExecution() {
        Log.d(TAG, "========== 开始快捷执行流程 ==========");
        layoutExecutionStatus.setVisibility(View.VISIBLE);
        updateExecutionStatus(getString(R.string.exec_status_preparing));
        appendExecutionLog(getString(R.string.exec_log_task, pendingTaskDescription));
        btnStartTask.setEnabled(false);
        continueQuickExecution();
    }

    private void continueQuickExecution() {
        Log.d(TAG, "========== 继续快捷执行流程 ==========");
        updateExecutionStatus(getString(R.string.exec_status_starting_service));
        appendExecutionLog(getString(R.string.exec_log_ws_start));
        try {
            ExecutorManager executor = ExecutorManager.getInstance();
            if (!executor.isRunning()) {
                executor.startServer(0);
            }
            appendExecutionLog(getString(R.string.exec_log_ws_started));
        } catch (Exception e) {
            Log.e(TAG, "启动 WebSocket 服务失败", e);
            appendExecutionLog(getString(R.string.exec_log_ws_failed, e.getMessage()));
            updateExecutionStatus(getString(R.string.exec_status_start_failed));
            btnStartTask.setEnabled(true);
            return;
        }
        appendExecutionLog(getString(R.string.exec_log_create_vd));
        startAohpVirtualDisplayForQuickTask();
    }

    /** 快捷任务：创建 AOHP 逻辑屏并写入 {@link ExecutionModeManager}，再执行 {@link #executeTask()}。 */
    private void startAohpVirtualDisplayForQuickTask() {
        new Thread(
                () -> {
                    ShellExecutor shell = ShellExecutor.getInstance();
                    shell.bindHostContext(getApplicationContext());
                    if (!shell.isAohpVirtualDisplayServiceAvailable()) {
                        mainHandler.post(
                                () -> {
                                    appendExecutionLog(getString(R.string.exec_log_aohp_unavailable));
                                    updateExecutionStatus(getString(R.string.exec_status_aohp_unavailable));
                                    btnStartTask.setEnabled(true);
                                });
                        return;
                    }
                    if (checkSelfPermission(PERM_MANAGE_AOHP_VD) != PackageManager.PERMISSION_GRANTED) {
                        mainHandler.post(
                                () -> {
                                    appendExecutionLog(getString(R.string.exec_log_missing_vd_permission));
                                    updateExecutionStatus(getString(R.string.exec_status_missing_aohp_perm));
                                    btnStartTask.setEnabled(true);
                                });
                        return;
                    }
                    int[] dm = shell.getBuiltinDisplayRealMetricsPx();
                    if (dm == null) {
                        dm = new int[]{1080, 1920, 320};
                    }
                    MultiVirtualDisplayManager mgr =
                            MultiVirtualDisplayManager.getInstance(MainActivity.this);
                    ShellExecutor.CommandResult r =
                            mgr.createDisplay("quick-start-vd", dm[0], dm[1], dm[2], 0);
                    mainHandler.post(
                            () -> {
                                if (!r.success) {
                                    appendExecutionLog(
                                            getString(
                                                    R.string.exec_log_create_vd_failed,
                                                    r.error != null ? r.error : "unknown"));
                                    updateExecutionStatus(getString(R.string.exec_status_vd_create_failed));
                                    btnStartTask.setEnabled(true);
                                    return;
                                }
                                try {
                                    int id = Integer.parseInt(r.output.trim());
                                    ExecutionModeManager modeManager =
                                            ExecutionModeManager.getInstance(MainActivity.this);
                                    modeManager.setExecutionMode(
                                            ExecutionModeManager.ExecutionMode.BACKGROUND);
                                    modeManager.setVirtualDisplayId(id);
                                    shell.publishVirtualDisplayId(id)
                                            .thenAccept(
                                                    pub -> {
                                                        if (pub != null && !pub.success) {
                                                            Log.w(
                                                                    TAG,
                                                                    "publishVirtualDisplayId: "
                                                                            + pub.error);
                                                        }
                                                    });
                                    appendExecutionLog(getString(R.string.exec_log_vd_created, id));
                                    appendExecutionLog(getString(R.string.exec_log_commands_routed));
                                    executeTask();
                                } catch (NumberFormatException e) {
                                    appendExecutionLog(
                                            getString(R.string.exec_log_parse_display_failed, r.output));
                                    updateExecutionStatus(getString(R.string.exec_status_vd_create_failed));
                                    btnStartTask.setEnabled(true);
                                }
                            });
                },
                "quick-aohp-vd")
                .start();
    }

    /**
     * 执行任务
     */
    private void executeTask() {
        Log.d(TAG, "========== 执行任务（已改为 CLI 能力，不再内嵌 Python Agent）==========");
        mainHandler.post(() -> {
            appendExecutionLog(getString(R.string.exec_log_nl_removed));
            updateExecutionStatus(getString(R.string.exec_status_use_cli));
            btnStartTask.setEnabled(true);
        });
    }
    
    /**
     * 更新执行状态
     */
    private void updateExecutionStatus(String status) {
        if (tvExecutionStatus != null) {
            tvExecutionStatus.setText(getString(R.string.execution_status_format, status));
        }
    }
    
    /**
     * 追加执行日志
     */
    private void appendExecutionLog(String log) {
        if (tvExecutionLog != null) {
            // 过滤 ANSI 转义序列
            String cleanLog = log.replaceAll("\u001B\\[[;\\d]*[a-zA-Z]", "");
            tvExecutionLog.append(cleanLog + "\n");
            
            // 滚动到底部
            View parent = (View) tvExecutionLog.getParent();
            if (parent instanceof ScrollView) {
                ((ScrollView) parent).fullScroll(View.FOCUS_DOWN);
            }
        }
    }
    
    // ==================== 虚拟屏幕测试相关方法 ====================
    
    /**
     * 初始化虚拟屏幕测试视图
     */
    private void checkAndRequestPermissions() {
        Log.d(TAG, "========== checkAndRequestPermissions START ==========");
        try {
            PermissionManager pm = PermissionManager.getInstance();
            Log.d(TAG, "PermissionManager instance: " + pm);
            
            // 检查核心权限
            PermissionManager.PermissionType[] corePermissions = {
                PermissionManager.PermissionType.ACCESSIBILITY,
            };
            
            List<PermissionManager.PermissionType> missingPermissions = new ArrayList<>();
            
            for (PermissionManager.PermissionType type : corePermissions) {
                PermissionManager.PermissionStatus status = pm.checkPermission(type);
                Log.d(TAG, "Permission check: " + type + " = " + status);
                if (status != PermissionManager.PermissionStatus.GRANTED) {
                    missingPermissions.add(type);
                    Log.d(TAG, "  ❌ Permission missing: " + type);
                } else {
                    Log.d(TAG, "  ✅ Permission granted: " + type);
                }
            }
            
            // 如果有缺失的权限，显示引导对话框
            if (!missingPermissions.isEmpty()) {
                Log.d(TAG, "Missing permissions: " + missingPermissions);
                Log.d(TAG, "Showing permission request dialog...");
                showPermissionRequestDialog(missingPermissions);
            } else {
                Log.d(TAG, "All core permissions granted");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking permissions", e);
            e.printStackTrace();
            // 降级到旧方法
            checkAndRequestPermissionsLegacy();
        }
        Log.d(TAG, "========== checkAndRequestPermissions END ==========");
    }
    
    /**
     * 显示权限请求对话框
     */
    private void showPermissionRequestDialog(List<PermissionManager.PermissionType> missingPermissions) {
        StringBuilder message =
                new StringBuilder(getString(R.string.permission_request_intro, getString(R.string.app_name)));
        for (PermissionManager.PermissionType type : missingPermissions) {
            message.append("• ").append(getPermissionName(type)).append("\n");
        }
        message.append(getString(R.string.permission_request_footer));
        
        Log.d(TAG, "Permission dialog message: " + message.toString());
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.permission_request_title)
            .setMessage(message.toString())
            .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                Log.d(TAG, "User clicked OK, starting permission request sequence...");
                requestPermissionsSequentially(missingPermissions);
            })
            .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                Log.d(TAG, "User cancelled permission request");
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * 依次请求权限
     */
    private void requestPermissionsSequentially(List<PermissionManager.PermissionType> permissions) {
        if (permissions.isEmpty()) {
            return;
        }
        
        // 保存待请求的权限列表
        pendingPermissions = new ArrayList<>(permissions);
        
        // 请求第一个权限
        PermissionManager.PermissionType type = permissions.get(0);
        PermissionManager pm = PermissionManager.getInstance();
        Log.d(TAG, "Requesting permission: " + type);
        pm.requestPermission(type, this);
        
        // 注意：实际权限授予需要等待 onActivityResult 或 onRequestPermissionsResult
        // 在那些回调中会继续请求下一个权限
    }
    
    /**
     * 继续请求下一个权限
     */
    private void continuePermissionRequest() {
        if (pendingPermissions.isEmpty()) {
            Log.d(TAG, "✅ All permissions requested");
            return;
        }
        
        // 检查第一个权限是否已经授予
        PermissionManager.PermissionType currentType = pendingPermissions.get(0);
        PermissionManager pm = PermissionManager.getInstance();
        PermissionManager.PermissionStatus currentStatus = pm.checkPermission(currentType);
        
        if (currentStatus == PermissionManager.PermissionStatus.GRANTED) {
            // 当前权限已授予，移除并继续下一个
            Log.d(TAG, "✅ Permission " + currentType + " already granted, skipping");
            pendingPermissions.remove(0);
            continuePermissionRequest(); // 递归调用，处理下一个
            return;
        }
        
        // 移除已完成的权限（第一个）
        pendingPermissions.remove(0);
        
        // 继续请求下一个权限
        if (!pendingPermissions.isEmpty()) {
            PermissionManager.PermissionType nextType = pendingPermissions.get(0);
            Log.d(TAG, "Continuing to request permission: " + nextType);
            pm.requestPermission(nextType, this);
        } else {
            Log.d(TAG, "✅ All permissions requested");
        }
    }
    
    /**
     * 获取权限名称
     */
    private String getPermissionName(PermissionManager.PermissionType type) {
        switch (type) {
            case OVERLAY:
                return getString(R.string.permission_overlay);
            case ACCESSIBILITY:
                return getString(R.string.permission_accessibility);
            case MEDIA_PROJECTION:
                return getString(R.string.permission_screen_capture);
            case NOTIFICATION:
                return getString(R.string.permission_notification);
            case STORAGE:
                return getString(R.string.permission_storage);
            default:
                return getString(R.string.permission_unknown);
        }
    }
    
    /**
     * 处理权限恢复（从通知点击进入）
     */
    private void handlePermissionRestore() {
        String permissionType = getIntent().getStringExtra("permission_type");
        if (permissionType != null) {
            try {
                PermissionManager.PermissionType type = PermissionManager.PermissionType.valueOf(permissionType);
                PermissionManager pm = PermissionManager.getInstance();
                
                PermissionManager.PermissionStatus status = pm.checkPermission(type);
                if (status != PermissionManager.PermissionStatus.GRANTED) {
                    // 显示权限恢复对话框
                    showPermissionRestoreDialog(type);
                } else {
                    notifyPermissionRestored(type);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling permission restore", e);
            }
        }
    }
    
    /**
     * 显示权限恢复对话框
     */
    private void showPermissionRestoreDialog(PermissionManager.PermissionType type) {
        String permissionName = getPermissionName(type);
        new AlertDialog.Builder(this)
            .setTitle(R.string.permission_restore_title)
            .setMessage(getString(R.string.permission_restore_message, permissionName))
            .setPositiveButton(R.string.permission_restore_action, (dialog, which) -> {
                PermissionManager pm = PermissionManager.getInstance();
                pm.requestPermission(type, this);
            })
            .setNegativeButton(R.string.action_cancel, null)
            .setCancelable(false)
            .show();
    }
    
    /**
     * 通知权限已恢复
     */
    private void notifyPermissionRestored(PermissionManager.PermissionType type) {
        Log.d(TAG, "Permission restored: " + type);
        Toast.makeText(
                        this, getString(R.string.permission_restored_toast, getPermissionName(type)), Toast.LENGTH_SHORT)
                .show();
    }
    
    /**
     * 旧版权限检查方法（降级使用）
     */
    private void checkAndRequestPermissionsLegacy() {
        // 检查无障碍服务权限
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }

    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (accessibilityEnabled == 1) {
            String service = getPackageName() + "/" + MyAccessibilityService.class.getCanonicalName();
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // ✅ 优先使用 PermissionManager 处理
        try {
            PermissionManager pm = PermissionManager.getInstance();
            pm.onActivityResult(requestCode, resultCode, data, this);
            
            // ⚠️ 关键：如果是权限请求的结果，继续请求下一个权限
            if (requestCode == PermissionManager.REQUEST_CODE_OVERLAY || 
                requestCode == PermissionManager.REQUEST_CODE_MEDIA_PROJECTION) {
                // 延迟一下，确保权限状态已更新
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    continuePermissionRequest();
                }, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling activity result in PermissionManager", e);
        }
        

        // 兼容旧代码（可选：为 RecordService 单独授予录屏）
        if (requestCode == RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("MainActivity", "========== onActivityResult: Screen Capture ==========");
                Log.d("MainActivity", "✅ Screen capture permission granted by user");
                
                // ✅ 创建 MediaProjection 对象
                Log.d("MainActivity", "Creating MediaProjection from result...");
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                Log.d("MainActivity", "MediaProjection created: " + (mediaProjection != null));
                
                if (mediaProjection == null) {
                    Log.e("MainActivity", "❌ Failed to create MediaProjection!");
                    return;
                }
                
                // ✅ 传递给 PermissionManager（它会自动传递给 ExecutorManager）
                try {
                    PermissionManager pm = PermissionManager.getInstance();
                    pm.setMediaProjection(mediaProjection);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting MediaProjection in PermissionManager", e);
                }
                
                // ✅ 同时传递给 ExecutorManager（向后兼容）
                try {
                    Log.d("MainActivity", "Getting ExecutorManager instance...");
                    ExecutorManager executor = ExecutorManager.getInstance();
                    
                    if (executor != null) {
                        Log.d("MainActivity", "✅ Calling executor.setMediaProjection()...");
                        executor.setMediaProjection(mediaProjection);
                        Log.d("MainActivity", "✅ MediaProjection passed to ExecutorManager successfully");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error passing MediaProjection to ExecutorManager", e);
                }
                
            } else {
                Log.w("MainActivity", "❌ Screen capture permission denied by user (resultCode=" + resultCode + ")");
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // ✅ 使用 PermissionManager 处理
        try {
            PermissionManager pm = PermissionManager.getInstance();
            pm.onRequestPermissionsResult(requestCode, permissions, grantResults);
            
            // ⚠️ 关键：如果是权限请求的结果，继续请求下一个权限
            if (requestCode == PermissionManager.REQUEST_CODE_NOTIFICATION || 
                requestCode == PermissionManager.REQUEST_CODE_STORAGE) {
                // 延迟一下，确保权限状态已更新
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    continuePermissionRequest();
                }, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling permission result in PermissionManager", e);
        }
    }

    // 提供获取MediaProjection的方法
    public MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        this.optionsMenu = menu;

        if (optionsMenu != null) {
            MenuItem settingsItem = optionsMenu.findItem(R.id.action_settings);
            if (settingsItem != null) {
                settingsItem.setVisible(false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Toast.makeText(this, R.string.settings_not_implemented, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 底栏各页需在目的地变化时同步选中态。
     */
    private void syncNavigationBarSelection(int destId) {
        boolean inBottomMenu = destId == R.id.navigation_sandbox
                || destId == R.id.navigation_virtual_display
                || destId == R.id.navigation_file_bridge;
        if (binding.bottomNav.getVisibility() == View.VISIBLE) {
            if (inBottomMenu) {
                if (binding.bottomNav.getSelectedItemId() != destId) {
                    suppressNavigationBarReselectCallback = true;
                    try {
                        binding.bottomNav.setSelectedItemId(destId);
                    } finally {
                        suppressNavigationBarReselectCallback = false;
                    }
                }
            } else {
                Menu menu = binding.bottomNav.getMenu();
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setChecked(false);
                }
            }
        }
        if (binding.navRail.getVisibility() == View.VISIBLE) {
            if (inBottomMenu) {
                if (binding.navRail.getSelectedItemId() != destId) {
                    suppressNavigationBarReselectCallback = true;
                    try {
                        binding.navRail.setSelectedItemId(destId);
                    } finally {
                        suppressNavigationBarReselectCallback = false;
                    }
                }
            } else {
                Menu menu = binding.navRail.getMenu();
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setChecked(false);
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }


    private boolean shouldUseNavigationRail() {
        // 获取屏幕宽度
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int widthDp = (int) (displayMetrics.widthPixels / displayMetrics.density);
        
        // 如果屏幕宽度大于 600dp，使用侧边导航栏
        return widthDp >= 600;
    }


    private void setupBottomNavigation() {
        binding.bottomNav.setVisibility(View.VISIBLE);
        binding.navRail.setVisibility(View.GONE);

        // 设置导航监听器
        binding.bottomNav.setOnItemSelectedListener(item -> {
            if (suppressNavigationBarReselectCallback) {
                return true;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // 监听导航变化，控制导航栏的显示/隐藏
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // ⚠️ 这些 Fragment ID 已删除，暂时不隐藏导航栏
            // TODO: 如果需要隐藏某些页面的导航栏，需要重新定义这些 ID
            boolean shouldHideNav = false; // 暂时不隐藏

            binding.bottomNav.setVisibility(shouldHideNav ? View.GONE : View.VISIBLE);
            binding.navRail.setVisibility(View.GONE);
        });
    }
    
    private void setupNavigationRail() {
        binding.bottomNav.setVisibility(View.GONE);
        binding.navRail.setVisibility(View.VISIBLE);
        
        // 设置导航监听器
        binding.navRail.setOnItemSelectedListener(item -> {
            if (suppressNavigationBarReselectCallback) {
                return true;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // 监听导航变化，控制导航栏的显示/隐藏
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // ⚠️ 这些 Fragment ID 已删除，暂时不隐藏导航栏
            boolean shouldHideNav = false; // 暂时不隐藏

            binding.bottomNav.setVisibility(View.GONE);
            binding.navRail.setVisibility(shouldHideNav ? View.GONE : View.VISIBLE);
        });
    }
    
    
    @Override
    protected void onDestroy() {
        super.onDestroy();

        MainActivity cur = activeInstance.get();
        if (cur == this) {
            activeInstance = new WeakReference<>(null);
        }
        
        // 重置执行模式为前台模式
        try {
            ExecutionModeManager modeManager = ExecutionModeManager.getInstance(this);
            modeManager.setVirtualDisplayId(-1);
            modeManager.setExecutionMode(ExecutionModeManager.ExecutionMode.FOREGROUND);
        } catch (Exception e) {
            Log.e(TAG, "Error resetting ExecutionModeManager", e);
        }
        
        // 清理 MediaProjection
        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaProjection", e);
            }
            mediaProjection = null;
        }
        
        // 清理 Handler 回调
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}