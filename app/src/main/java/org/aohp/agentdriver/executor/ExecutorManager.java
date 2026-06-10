package org.aohp.agentdriver.executor;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.aohp.agentdriver.MainActivity;
import org.aohp.agentdriver.R;

public class ExecutorManager {
    private final Context context;
    private final AppInfoManager appInfoManager;
    private final WebSocketManager webSocketManager;
    private final RecordManager recordManager;
    private final AccessibilityManager accessibilityManager;
    
    private boolean isRunning = false;

    // 添加权限相关常量
    public static final int NOTIFICATION_PERMISSION_CODE = 123;
    public static final int STORAGE_PERMISSION_CODE = 124;
    private static final String CHANNEL_ID = "aohp_agent_executor";
    private static final int NOTIFICATION_ID = 1;

    private static ExecutorManager instance;
    
    private static final String TAG = "ExecutorManager";
    
    // ✅ 新构造函数：使用 Application Context（推荐）
    private ExecutorManager(Context context) {
        // ⭐ 关键：使用 ApplicationContext，脱离 Activity 生命周期
        this.context = context.getApplicationContext();
        
        Log.d(TAG, "ExecutorManager constructor called with context: " + context.getClass().getName());
        Log.d(TAG, "Using ApplicationContext: " + this.context.getClass().getName());
        
        this.appInfoManager = new AppInfoManager(this.context);
        this.webSocketManager = new WebSocketManager(this.context);
        this.recordManager = new RecordManager(this.context);
        this.accessibilityManager = (AccessibilityManager) this.context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        
        Log.d(TAG, "✅ ExecutorManager constructed with Application context");
    }
    
    // ✅ 推荐的初始化方法：在 Application.onCreate() 中调用
    public static void initInstance(Context context) {
        if (instance == null) {
            synchronized (ExecutorManager.class) {
                if (instance == null) {
                    Log.d(TAG, "Creating ExecutorManager singleton instance...");
                    instance = new ExecutorManager(context);
                    Log.d(TAG, "✅ ExecutorManager singleton instance created");
                }
            }
        } else {
            Log.d(TAG, "ExecutorManager instance already exists");
        }
    }

    // 获取单例实例
    public static ExecutorManager getInstance() {
        if (instance == null) {
            Log.e(TAG, "❌ ExecutorManager not initialized! Call initInstance(Context) first in Application.onCreate()");
            throw new IllegalStateException(
                "ExecutorManager not initialized. " +
                "Please call ExecutorManager.initInstance(context) in Application.onCreate()"
            );
        }
        return instance;
    }

    public void initialize() {
        Log.d(TAG, "========== ExecutorManager.initialize() START ==========");
        
        // ✅ 使用 PermissionManager 检查权限（不再直接请求，由 MainActivity 处理）
        Log.d(TAG, "Checking permissions via PermissionManager...");
        try {
            org.aohp.agentdriver.permission.PermissionManager pm = 
                org.aohp.agentdriver.permission.PermissionManager.getInstance();
            boolean allGranted = pm.areAllCorePermissionsGranted();
            if (!allGranted) {
                Log.w(TAG, "⚠️ Not all core permissions granted. User needs to grant from MainActivity.");
            } else {
                Log.d(TAG, "✅ All core permissions granted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions", e);
        }
        
        Log.d(TAG, "Initializing notifications...");
        initializeNotifications();
        
        Log.d(TAG, "Loading app info...");
        appInfoManager.loadAppInfo();
        
        Log.d(TAG, "⭐ Initializing RecordManager...");
        recordManager.initialize();
        Log.d(TAG, "✅ RecordManager.initialize() completed");
        
        Log.d(TAG, "Setting WebSocketManager for RecordManager...");
        recordManager.setWebSocketManager(webSocketManager);
        
        Log.d(TAG, "========== ExecutorManager.initialize() COMPLETED ==========");
    }

    private void initializeNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.app_name) + " 运行状态");

            NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showRunningNotification() {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.app_name) + " 正在运行")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        NotificationManager notificationManager = 
                context.getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void checkAndRequestAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            context.startActivity(intent);
        } else {
            MyAccessibilityService accessibilityService = MyAccessibilityService.getInstance();
            if (accessibilityService != null && webSocketManager != null) {
                webSocketManager.startService();
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String expectedServiceName = context.getPackageName() + "/" + MyAccessibilityService.class.getCanonicalName();
        String enabledServices = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(expectedServiceName);
    }

    public void start() {
        Log.d(TAG, "start() called, isRunning: " + isRunning);
        if (!isRunning) {
            webSocketManager.startService();
            showRunningNotification();
            isRunning = true;
            Log.d(TAG, "Start completed, isRunning: " + isRunning);
        }
    }

    public void stop() {
        if (isRunning) {
            webSocketManager.stopService();
            NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
            notificationManager.cancel(NOTIFICATION_ID);
            isRunning = false;
        }
    }

    public void cleanup() {
        webSocketManager.cleanup();
        recordManager.cleanup();
    }
    
    // Getter methods
    public AppInfoManager getAppInfoManager() {
        return appInfoManager;
    }
    
    public WebSocketManager getWebSocketManager() {
        return webSocketManager;
    }
    
    public RecordManager getRecordManager() {
        return recordManager;
    }

    /**
     * 处理屏幕捕获权限结果
     * ⚠️ 注意：由于 ExecutorManager 使用 Application Context，
     *    无法直接从 context 获取 MediaProjection。
     *    应该由 MainActivity 创建 MediaProjection 后，调用 setMediaProjection()
     */
    @Deprecated
    public void handleScreenCaptureResult(int resultCode, Intent data) {
        Log.d(TAG, "========== handleScreenCaptureResult() called ==========");
        Log.d(TAG, "⚠️ This method is deprecated. Use setMediaProjection() instead.");
        Log.d(TAG, "Result code: " + resultCode + " (RESULT_OK=" + Activity.RESULT_OK + ")");
        Log.d(TAG, "Intent data: " + (data == null ? "NULL" : "EXISTS"));
        Log.d(TAG, "Context type: " + context.getClass().getName());
        
        // ⚠️ 由于使用 Application Context，这个方法无法获取 MediaProjection
        Log.w(TAG, "❌ Cannot obtain MediaProjection from Application Context");
        Log.w(TAG, "   MainActivity should call setMediaProjection() directly");
    }
    
    /**
     * ✅ 新方法：直接设置 MediaProjection
     * MainActivity 应该调用这个方法传递权限
     */
    public void setMediaProjection(MediaProjection mediaProjection) {
        Log.d(TAG, "========== setMediaProjection() called ==========");
        Log.d(TAG, "MediaProjection: " + (mediaProjection == null ? "NULL (expired/cleared)" : "EXISTS"));
        
        if (mediaProjection != null) {
            // ✅ 同时传递给 PermissionManager 和 RecordManager
            try {
                org.aohp.agentdriver.permission.PermissionManager pm = 
                    org.aohp.agentdriver.permission.PermissionManager.getInstance();
                pm.setMediaProjection(mediaProjection);
                Log.d(TAG, "✅ MediaProjection passed to PermissionManager");
            } catch (Exception e) {
                Log.e(TAG, "Error passing MediaProjection to PermissionManager", e);
            }
            
            Log.d(TAG, "✅ Passing MediaProjection to RecordManager...");
            recordManager.handleMediaProjection(mediaProjection);
            Log.d(TAG, "✅ MediaProjection set successfully");
        } else {
            // ⚠️ 允许传递 null，表示 MediaProjection 已失效，需要清除
            Log.w(TAG, "⚠️ MediaProjection is null, clearing from RecordManager...");
            recordManager.handleMediaProjection(null);
            Log.d(TAG, "✅ MediaProjection cleared from RecordManager");
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    // 修改本地模式的方法，使用现有的 WebSocketManager
    public void startServer(int port) {
        Log.d(TAG, "startServer: " + port);
        if (!isRunning) {
            webSocketManager.startService();  // 使用现有的 WebSocket 服务
            isRunning = true;
        }
    }

    public void stopServer() {
        if (isRunning) {
            webSocketManager.stopService();  // 使用现有的 WebSocket 服务
            isRunning = false;
        }
    }

    public boolean isServiceRunning() {
        // 检查服务是否在运行
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("org.aohp.agentdriver.executor.ws.AohpJsonRpcService"
                    .equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    // ✅ 新增：检查 RecordService 是否就绪
    public boolean isRecordServiceReady() {
        try {
            RecordService service = RecordManager.getRecordService();
            return service != null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking RecordService status", e);
            return false;
        }
    }
    
    // ✅ 新增：等待 RecordService 就绪（带超时）
    public boolean waitForRecordServiceReady(int timeoutMs) {
        long startTime = System.currentTimeMillis();
        int attempts = 0;
        
        while (!isRecordServiceReady()) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMs) {
                Log.w(TAG, "⚠️ RecordService not ready after " + timeoutMs + "ms (attempts: " + attempts + ")");
                return false;
            }
            
            try {
                Thread.sleep(500);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        Log.d(TAG, "✅ RecordService ready after " + attempts + " attempts");
        return true;
    }

}