package org.aohp.agentdriver;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.aohp.agentdriver.executor.ExecutorManager;
import org.aohp.agentdriver.executor.ShellExecutor;
import org.aohp.agentdriver.permission.PermissionManager;
import org.aohp.agentdriver.system.SystemPrivilegeBootstrap;
import org.aohp.agentdriver.uda.UdaDemoSeeder;
import org.aohp.agentdriver.uda.UdaManager;

public class MyApp extends Application {
    private static final String TAG = "MyApp";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // ✅ 检测当前进程名
        String processName = getCurrentProcessName();
        Log.d(TAG, "========== MyApp onCreate START ==========");
        Log.d(TAG, "Process Name: " + processName);
        Log.d(TAG, "Application Context: " + getApplicationContext());
        long startTime = System.currentTimeMillis();

        ShellExecutor.getInstance().bindHostContext(this);

        // ✅ 在 Application 层初始化 PermissionManager（仅主进程）
        Log.d(TAG, "Initializing PermissionManager...");
        try {
            PermissionManager.initInstance(this);
            PermissionManager pm = PermissionManager.getInstance();
            // 启动权限监控
            pm.startMonitoring();
            Log.d(TAG, "✅ PermissionManager initialized and monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize PermissionManager", e);
        }

        // ✅ 在 Application 层初始化 ExecutorManager（仅主进程）
        // 使用 Application Context，不依赖任何 Activity
        Log.d(TAG, "Initializing ExecutorManager with Application context...");
        try {
            ExecutorManager.initInstance(this);
            Log.d(TAG, "✅ ExecutorManager initialized successfully");
            
            // ⭐ 验证初始化是否成功
            ExecutorManager executor = ExecutorManager.getInstance();
            Log.d(TAG, "ExecutorManager instance verification: " + (executor != null ? "OK" : "NULL"));
            
            // ⭐ 重要：立即调用 initialize() 来初始化所有组件
            Log.d(TAG, "Calling ExecutorManager.initialize()...");
            executor.initialize();
            Log.d(TAG, "✅ ExecutorManager.initialize() completed");

            // Priv-app / platform builds: enable accessibility via Secure settings, then refresh caches
            try {
                if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                        == PackageManager.PERMISSION_GRANTED) {
                    SystemPrivilegeBootstrap.ensureAccessibilityServiceEnabledInSecureSettings(this);
                    PermissionManager.getInstance().syncCachedPermissionStates();
                }
                SystemPrivilegeBootstrap.ensureSystemAlertWindowAllowed(this);
            } catch (Exception e) {
                Log.e(TAG, "Secure-settings accessibility bootstrap failed", e);
            }

            // Start WebSocket (AohpJsonRpcService) as soon as the app process is up
            final ExecutorManager execForWs = executor;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (!execForWs.isRunning()) {
                        execForWs.startServer(0);
                        Log.d(TAG, "✅ WebSocket server auto-started from Application");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Auto-start WebSocket server failed", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize ExecutorManager", e);
            e.printStackTrace();
        }

        UdaManager udaManager = UdaManager.getInstance(this);
        udaManager.submitIo(() -> UdaDemoSeeder.seedIfNeeded(this, udaManager));

        long totalTime = System.currentTimeMillis() - startTime;
        Log.d(TAG, "========== MyApp onCreate COMPLETED in " + totalTime + "ms ==========");
    }
    
    /**
     * 获取当前进程名
     */
    private String getCurrentProcessName() {
        try {
            int pid = android.os.Process.myPid();
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo processInfo : am.getRunningAppProcesses()) {
                    if (processInfo.pid == pid) {
                        return processInfo.processName;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get process name", e);
        }
        return null;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "MyApp onTerminate");
        
        // 清理 ExecutorManager（注意：onTerminate 只在模拟器中调用）
        try {
            ExecutorManager executor = ExecutorManager.getInstance();
            if (executor != null) {
                executor.cleanup();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up ExecutorManager", e);
        }
    }
}
