package org.aohp.agentdriver.permission;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.aohp.agentdriver.executor.MyAccessibilityService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 权限管理器
 * 统一管理所有权限的检查、请求和监控
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static PermissionManager instance;
    
    private final Context context;
    private final Handler handler;
    private final Map<PermissionType, PermissionStatus> statusCache;
    private final List<PermissionListener> listeners;
    
    // 监控标志
    private boolean isMonitoring = false;
    private static final long CHECK_INTERVAL = 30 * 1000; // 30秒检查一次
    
    // MediaProjection 相关
    private MediaProjection mediaProjection;
    private MediaProjection.Callback mediaProjectionCallback;
    private int mediaProjectionResultCode = 0;
    private Intent mediaProjectionResultData = null;
    
    // 权限请求码
    public static final int REQUEST_CODE_OVERLAY = 1001;
    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1002;
    public static final int REQUEST_CODE_NOTIFICATION = 1003;
    public static final int REQUEST_CODE_STORAGE = 1004;
    
    /**
     * 权限类型枚举
     */
    public enum PermissionType {
        /** 悬浮窗 */
        OVERLAY,
        /** 无障碍服务 */
        ACCESSIBILITY,
        /** 屏幕录制 / MediaProjection */
        MEDIA_PROJECTION,
        /** 通知（Android 13+） */
        NOTIFICATION,
        /** 存储读写 */
        STORAGE,
    }
    
    /**
     * 权限状态枚举
     */
    public enum PermissionStatus {
        GRANTED,        // 已授予
        DENIED,         // 已拒绝（用户明确拒绝）
        NOT_REQUESTED,  // 未请求
        REVOKED,        // 已撤销（运行中丢失）
        EXPIRED         // 已过期（如 MediaProjection）
    }
    
    /**
     * 权限监听器接口
     */
    public interface PermissionListener {
        /**
         * 权限状态变化
         */
        void onPermissionChanged(PermissionType type, PermissionStatus status);
        
        /**
         * 核心权限丢失
         */
        void onCorePermissionLost(PermissionType type);
        
        /**
         * 所有核心权限已授予
         */
        void onAllCorePermissionsGranted();
    }
    
    private PermissionManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.statusCache = new HashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        
        // 初始化状态缓存
        for (PermissionType type : PermissionType.values()) {
            statusCache.put(type, PermissionStatus.NOT_REQUESTED);
        }
        
        Log.d(TAG, "PermissionManager created");
    }
    
    /**
     * 初始化单例（在 Application.onCreate() 中调用）
     */
    public static void initInstance(Context context) {
        if (instance == null) {
            synchronized (PermissionManager.class) {
                if (instance == null) {
                    Log.d(TAG, "Creating PermissionManager singleton instance...");
                    instance = new PermissionManager(context);
                    Log.d(TAG, "✅ PermissionManager singleton instance created");
                }
            }
        } else {
            Log.d(TAG, "PermissionManager instance already exists");
        }
    }
    
    /**
     * 获取单例实例
     */
    public static PermissionManager getInstance() {
        if (instance == null) {
            Log.e(TAG, "❌ PermissionManager not initialized! Call initInstance(Context) first in Application.onCreate()");
            throw new IllegalStateException(
                "PermissionManager not initialized. " +
                "Please call PermissionManager.initInstance(context) in Application.onCreate()"
            );
        }
        return instance;
    }
    
    /**
     * 检查权限状态
     */
    public PermissionStatus checkPermission(PermissionType type) {
        PermissionStatus status;
        
        switch (type) {
            case OVERLAY:
                status = checkOverlayPermission();
                break;
            case ACCESSIBILITY:
                status = checkAccessibilityPermission();
                break;
            case MEDIA_PROJECTION:
                status = checkMediaProjectionPermission();
                break;
            case NOTIFICATION:
                status = checkNotificationPermission();
                break;
            case STORAGE:
                status = checkStoragePermission();
                break;
            default:
                status = PermissionStatus.NOT_REQUESTED;
        }
        
        return status;
    }
    
    /**
     * 检查悬浮窗权限
     */
    private PermissionStatus checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = Settings.canDrawOverlays(context);
            return granted ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
        }
        return PermissionStatus.GRANTED; // Android 6.0 以下默认授予
    }
    
    /**
     * 检查无障碍服务权限
     */
    private PermissionStatus checkAccessibilityPermission() {
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            );
            
            if (accessibilityEnabled == 1) {
                String serviceName = context.getPackageName() + "/" + 
                    MyAccessibilityService.class.getCanonicalName();
                String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );
                
                if (enabledServices != null && enabledServices.contains(serviceName)) {
                    return PermissionStatus.GRANTED;
                }
            }
            return PermissionStatus.DENIED;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error checking accessibility permission", e);
            return PermissionStatus.NOT_REQUESTED;
        }
    }
    
    /**
     * 检查屏幕录制权限
     */
    private PermissionStatus checkMediaProjectionPermission() {
        Log.d(TAG, "checkMediaProjectionPermission: mediaProjection = " + mediaProjection);
        if (mediaProjection == null) {
            Log.d(TAG, "  → MediaProjection is null, returning NOT_REQUESTED");
            return PermissionStatus.NOT_REQUESTED;
        }
        // MediaProjection 没有直接的 isValid() 方法
        // 我们通过监听 Callback 来检测过期
        Log.d(TAG, "  → MediaProjection exists, returning GRANTED");
        return PermissionStatus.GRANTED;
    }
    
    /**
     * 检查通知权限
     */
    private PermissionStatus checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
            return granted ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
        }
        return PermissionStatus.GRANTED; // Android 13 以下默认授予
    }
    
    /**
     * 检查存储权限
     */
    private PermissionStatus checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
            return granted ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
        }
        return PermissionStatus.GRANTED; // Android 6.0 以下默认授予
    }
    
    /**
     * 请求权限
     */
    public void requestPermission(PermissionType type, Activity activity) {
        if (activity == null) {
            Log.e(TAG, "Cannot request permission: Activity is null");
            return;
        }
        
        switch (type) {
            case OVERLAY:
                requestOverlayPermission(activity);
                break;
            case ACCESSIBILITY:
                requestAccessibilityPermission(activity);
                break;
            case MEDIA_PROJECTION:
                requestMediaProjectionPermission(activity);
                break;
            case NOTIFICATION:
                requestNotificationPermission(activity);
                break;
            case STORAGE:
                requestStoragePermission(activity);
                break;
        }
    }
    
    private void requestOverlayPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY);
        }
    }
    
    private void requestAccessibilityPermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        activity.startActivity(intent);
        // 可以显示引导对话框
    }
    
    private void requestMediaProjectionPermission(Activity activity) {
        android.media.projection.MediaProjectionManager mpm = 
            (android.media.projection.MediaProjectionManager) 
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            activity.startActivityForResult(
                mpm.createScreenCaptureIntent(),
                REQUEST_CODE_MEDIA_PROJECTION
            );
        }
    }
    
    private void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_NOTIFICATION
            );
        }
    }
    
    private void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(
                new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_CODE_STORAGE
            );
        }
    }
    
    /**
     * 设置 MediaProjection（由 MainActivity 调用）
     */
    public void setMediaProjection(MediaProjection projection) {
        Log.d(TAG, "setMediaProjection called: " + (projection != null ? "EXISTS" : "NULL"));
        
        // 移除旧的回调
        if (this.mediaProjection != null && mediaProjectionCallback != null) {
            this.mediaProjection.unregisterCallback(mediaProjectionCallback);
        }
        
        this.mediaProjection = projection;
        
        // 如果是清除 MediaProjection，也清除 resultCode 和 data
        if (projection == null) {
            this.mediaProjectionResultCode = 0;
            this.mediaProjectionResultData = null;
        }
        
        // 注册回调监听过期
        if (projection != null) {
            mediaProjectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.w(TAG, "⚠️ MediaProjection stopped (expired)");
                    handler.post(() -> {
                        // ⚠️ 关键：在设置为 null 之前，先通知所有依赖组件
                        MediaProjection expiredProjection = mediaProjection;
                        mediaProjection = null;
                        
                        // 通知 ExecutorManager/RecordManager/RecordService MediaProjection 已失效
                        try {
                            org.aohp.agentdriver.executor.ExecutorManager executor = 
                                org.aohp.agentdriver.executor.ExecutorManager.getInstance();
                            if (executor != null) {
                                Log.d(TAG, "Notifying ExecutorManager that MediaProjection expired...");
                                executor.setMediaProjection(null); // 传递 null 表示失效
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error notifying ExecutorManager of MediaProjection expiration", e);
                        }
                        
                        PermissionStatus oldStatus = statusCache.get(PermissionType.MEDIA_PROJECTION);
                        statusCache.put(PermissionType.MEDIA_PROJECTION, PermissionStatus.EXPIRED);
                        notifyPermissionChanged(PermissionType.MEDIA_PROJECTION, PermissionStatus.EXPIRED);
                        notifyCorePermissionLost(PermissionType.MEDIA_PROJECTION);
                    });
                }
            };
            projection.registerCallback(mediaProjectionCallback, handler);
            Log.d(TAG, "✅ MediaProjection callback registered");
        }
        
        // 更新状态
        PermissionStatus newStatus = checkMediaProjectionPermission();
        PermissionStatus oldStatus = statusCache.get(PermissionType.MEDIA_PROJECTION);
        statusCache.put(PermissionType.MEDIA_PROJECTION, newStatus);
        
        if (oldStatus != newStatus) {
            notifyPermissionChanged(PermissionType.MEDIA_PROJECTION, newStatus);
        }
    }
    
    /**
     * 获取 MediaProjection
     */
    public MediaProjection getMediaProjection() {
        return mediaProjection;
    }
    
    /**
     * 检查是否所有核心权限已授予
     */
    public boolean areAllCorePermissionsGranted() {
        PermissionType[] corePermissions = {
            PermissionType.ACCESSIBILITY,
        };
        
        for (PermissionType type : corePermissions) {
            PermissionStatus status = checkPermission(type);
            if (status != PermissionStatus.GRANTED) {
                Log.d(TAG, "Core permission not granted: " + type + " = " + status);
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取权限状态
     */
    public PermissionStatus getPermissionStatus(PermissionType type) {
        return statusCache.getOrDefault(type, PermissionStatus.NOT_REQUESTED);
    }
    
    /**
     * 注册权限监听器
     */
    public void registerPermissionListener(PermissionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "PermissionListener registered: " + listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 取消注册权限监听器
     */
    public void unregisterPermissionListener(PermissionListener listener) {
        listeners.remove(listener);
        Log.d(TAG, "PermissionListener unregistered: " + listener.getClass().getSimpleName());
    }
    
    /**
     * Re-run core permission checks and update caches / listeners (e.g. after programmatic
     * Secure settings changes from a privileged build).
     */
    public void syncCachedPermissionStates() {
        checkCorePermissions();
    }

    /**
     * 开始监控权限状态
     */
    public void startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Permission monitoring already started");
            return;
        }
        
        Log.d(TAG, "Starting permission monitoring...");
        isMonitoring = true;
        
        // 立即检查一次
        checkCorePermissions();
        
        // 开始定期检查
        handler.post(checkRunnable);
        Log.d(TAG, "✅ Permission monitoring started");
    }
    
    /**
     * 停止监控权限状态
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        Log.d(TAG, "Stopping permission monitoring...");
        isMonitoring = false;
        handler.removeCallbacks(checkRunnable);
        Log.d(TAG, "✅ Permission monitoring stopped");
    }
    
    /**
     * 定期检查核心权限
     */
    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkCorePermissions();
            if (isMonitoring) {
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        }
    };
    
    /**
     * 检查所有核心权限
     */
    private void checkCorePermissions() {
        PermissionType[] corePermissions = {
            PermissionType.ACCESSIBILITY,
        };
        
        boolean allGranted = true;
        
        for (PermissionType type : corePermissions) {
            PermissionStatus oldStatus = statusCache.get(type);
            PermissionStatus newStatus = checkPermission(type);
            
            if (oldStatus != newStatus) {
                Log.d(TAG, "Permission status changed: " + type + " " + oldStatus + " -> " + newStatus);
                statusCache.put(type, newStatus);
                notifyPermissionChanged(type, newStatus);
                
                if (newStatus == PermissionStatus.REVOKED || 
                    newStatus == PermissionStatus.EXPIRED ||
                    newStatus == PermissionStatus.DENIED) {
                    notifyCorePermissionLost(type);
                    allGranted = false;
                }
            }
            
            if (newStatus != PermissionStatus.GRANTED) {
                allGranted = false;
            }
        }
        
        // 如果所有核心权限都已授予，通知监听器
        if (allGranted) {
            notifyAllCorePermissionsGranted();
        }
    }
    
    /**
     * 通知权限状态变化
     */
    private void notifyPermissionChanged(PermissionType type, PermissionStatus status) {
        for (PermissionListener listener : listeners) {
            try {
                listener.onPermissionChanged(type, status);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying permission listener", e);
            }
        }
    }
    
    /**
     * 通知核心权限丢失
     */
    private void notifyCorePermissionLost(PermissionType type) {
        Log.w(TAG, "⚠️ Core permission lost: " + type);
        for (PermissionListener listener : listeners) {
            try {
                listener.onCorePermissionLost(type);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying core permission lost", e);
            }
        }
    }
    
    /**
     * 通知所有核心权限已授予
     */
    private void notifyAllCorePermissionsGranted() {
        Log.d(TAG, "✅ All core permissions granted");
        for (PermissionListener listener : listeners) {
            try {
                listener.onAllCorePermissionsGranted();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying all permissions granted", e);
            }
        }
    }
    
    /**
     * 处理权限请求结果（由 Activity 调用）
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_NOTIFICATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    statusCache.put(PermissionType.NOTIFICATION, PermissionStatus.GRANTED);
                    notifyPermissionChanged(PermissionType.NOTIFICATION, PermissionStatus.GRANTED);
                } else {
                    statusCache.put(PermissionType.NOTIFICATION, PermissionStatus.DENIED);
                    notifyPermissionChanged(PermissionType.NOTIFICATION, PermissionStatus.DENIED);
                }
                break;
            case REQUEST_CODE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    statusCache.put(PermissionType.STORAGE, PermissionStatus.GRANTED);
                    notifyPermissionChanged(PermissionType.STORAGE, PermissionStatus.GRANTED);
                } else {
                    statusCache.put(PermissionType.STORAGE, PermissionStatus.DENIED);
                    notifyPermissionChanged(PermissionType.STORAGE, PermissionStatus.DENIED);
                }
                break;
        }
    }
    
    /**
     * 处理 Activity 结果（由 Activity 调用）
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data, Activity activity) {
        switch (requestCode) {
            case REQUEST_CODE_OVERLAY:
                PermissionStatus status = checkOverlayPermission();
                statusCache.put(PermissionType.OVERLAY, status);
                notifyPermissionChanged(PermissionType.OVERLAY, status);
                if (status == PermissionStatus.GRANTED) {
                    Log.d(TAG, "✅ Overlay permission granted");
                } else {
                    Log.w(TAG, "⚠️ Overlay permission denied");
                }
                break;
            case REQUEST_CODE_MEDIA_PROJECTION:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // ✅ 保存 resultCode 和 data，用于后续创建虚拟屏幕
                    this.mediaProjectionResultCode = resultCode;
                    this.mediaProjectionResultData = data;
                    Log.d(TAG, "✅ MediaProjection resultCode and data saved");
                    
                    android.media.projection.MediaProjectionManager mpm = 
                        (android.media.projection.MediaProjectionManager) 
                        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    if (mpm != null) {
                        MediaProjection projection = mpm.getMediaProjection(resultCode, data);
                        setMediaProjection(projection);
                        Log.d(TAG, "✅ MediaProjection permission granted");
                    }
                } else {
                    statusCache.put(PermissionType.MEDIA_PROJECTION, PermissionStatus.DENIED);
                    notifyPermissionChanged(PermissionType.MEDIA_PROJECTION, PermissionStatus.DENIED);
                    Log.w(TAG, "⚠️ MediaProjection permission denied");
                }
                break;
        }
    }
    
    /**
     * 获取 MediaProjection resultCode（用于创建虚拟屏幕）
     */
    public int getMediaProjectionResultCode() {
        return mediaProjectionResultCode;
    }
    
    /**
     * 获取 MediaProjection resultData（用于创建虚拟屏幕）
     */
    public Intent getMediaProjectionResultData() {
        return mediaProjectionResultData;
    }
}

