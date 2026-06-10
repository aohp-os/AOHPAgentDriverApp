package org.aohp.agentdriver.executor;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.aohp.agentdriver.MainActivity;

public class RecordManager {
    public static final int RECORD_REQUEST_CODE = 101;
    private final Context context;
    private MediaProjection mediaProjection;
    private static RecordService recordService;
    private ServiceConnection serviceConnection;
    private WebSocketManager webSocketManager;

    public RecordManager(Context context) {
        this.context = context;
        initializeServiceConnection();
    }

    public void initialize() {
        Log.d("RecordManager", "========== initialize() START ==========");
        Log.d("RecordManager", "Current recordService: " + (recordService == null ? "NULL" : "EXISTS"));
        Log.d("RecordManager", "Context type: " + context.getClass().getName());
        
        if (recordService == null) {
            Log.d("RecordManager", "Creating new RecordService instance...");
            recordService = new RecordService();
            
            Intent intent = new Intent(context, RecordService.class);
            Log.d("RecordManager", "Binding RecordService...");
            boolean bindResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d("RecordManager", "Bind result: " + bindResult);
            
            // ⚠️ 注意：如果 context 是 Application，这段代码不会执行
            if (context instanceof MainActivity) {
                Log.d("RecordManager", "Context is MainActivity, trying to get MediaProjection...");
                MediaProjection mediaProjection = ((MainActivity) context).getMediaProjection();
                Log.d("RecordManager", "MediaProjection from MainActivity: " + (mediaProjection == null ? "NULL" : "EXISTS"));
                if (mediaProjection != null) {
                    handleMediaProjection(mediaProjection);
                }
            } else {
                Log.w("RecordManager", "⚠️ Context is NOT MainActivity (" + context.getClass().getName() + ")");
                Log.w("RecordManager", "   MediaProjection cannot be obtained from non-Activity context");
                // ⚠️ 尝试从 PermissionManager 获取 MediaProjection
                Log.d("RecordManager", "   Trying to get MediaProjection from PermissionManager...");
                try {
                    org.aohp.agentdriver.permission.PermissionManager pm = 
                        org.aohp.agentdriver.permission.PermissionManager.getInstance();
                    MediaProjection pmProjection = pm.getMediaProjection();
                    if (pmProjection != null) {
                        Log.d("RecordManager", "✅ Found MediaProjection in PermissionManager, storing...");
                        this.mediaProjection = pmProjection;
                        // 如果 RecordService 已连接，立即传递
                        if (recordService != null) {
                            handleMediaProjection(pmProjection);
                        }
                    } else {
                        Log.w("RecordManager", "   PermissionManager also has no MediaProjection");
                        Log.w("RecordManager", "   User needs to grant permission from MainActivity");
                    }
                } catch (Exception e) {
                    Log.e("RecordManager", "Error getting MediaProjection from PermissionManager", e);
                    Log.w("RecordManager", "   User needs to grant permission from MainActivity");
                }
            }
        } else {
            Log.d("RecordManager", "RecordService already initialized");
        }
        
        Log.d("RecordManager", "========== initialize() COMPLETED ==========");
    }

    private void initializeServiceConnection() {
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d("RecordManager", "========== onServiceConnected ==========");
                Log.d("RecordManager", "Service component: " + className);
                Log.d("RecordManager", "IBinder: " + service);
                
                RecordService.RecordBinder binder = (RecordService.RecordBinder) service;
                recordService = binder.getRecordService();
                
                Log.d("RecordManager", "✅ RecordService connected: " + (recordService != null));
                
                if (mediaProjection != null) {
                    Log.d("RecordManager", "MediaProjection exists, passing to RecordService...");
                    handleMediaProjection(mediaProjection);
                } else {
                    // ⚠️ 尝试从 PermissionManager 获取 MediaProjection
                    Log.w("RecordManager", "⚠️ MediaProjection is null at connection time");
                    Log.d("RecordManager", "   Trying to get MediaProjection from PermissionManager...");
                    try {
                        org.aohp.agentdriver.permission.PermissionManager pm = 
                            org.aohp.agentdriver.permission.PermissionManager.getInstance();
                        MediaProjection pmProjection = pm.getMediaProjection();
                        if (pmProjection != null) {
                            Log.d("RecordManager", "✅ Found MediaProjection in PermissionManager, passing to RecordService...");
                            handleMediaProjection(pmProjection);
                        } else {
                            Log.w("RecordManager", "   PermissionManager also has no MediaProjection");
                            Log.w("RecordManager", "   RecordService is bound but cannot capture screenshots yet");
                            Log.w("RecordManager", "   Waiting for MainActivity to grant MediaProjection permission");
                        }
                    } catch (Exception e) {
                        Log.e("RecordManager", "Error getting MediaProjection from PermissionManager", e);
                        Log.w("RecordManager", "   RecordService is bound but cannot capture screenshots yet");
                        Log.w("RecordManager", "   Waiting for MainActivity to grant MediaProjection permission");
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0) {
                Log.w("RecordManager", "⚠️ RecordService disconnected: " + arg0);
                recordService = null;
            }
        };
    }

    public void handleMediaProjection(MediaProjection mediaProjection) {
        Log.d("RecordManager", "========== handleMediaProjection() called ==========");
        Log.d("RecordManager", "MediaProjection parameter: " + (mediaProjection == null ? "NULL (clearing)" : "EXISTS"));
        Log.d("RecordManager", "Current recordService: " + (recordService == null ? "NULL" : "EXISTS"));
        
        // ⚠️ 保存 MediaProjection 引用（即使是 null 也要保存，表示已清除）
        this.mediaProjection = mediaProjection;
        
        if (recordService != null) {
            Log.d("RecordManager", "✅ RecordService exists, passing MediaProjection...");
            try {
                recordService.setMediaProject(mediaProjection);
                Log.d("RecordManager", "✅ MediaProjection passed to RecordService successfully");
            } catch (Exception e) {
                Log.e("RecordManager", "❌ Failed to set MediaProjection on RecordService", e);
            }
        } else {
            Log.w("RecordManager", "⚠️ RecordService is NULL!");
            if (mediaProjection != null) {
                Log.w("RecordManager", "   MediaProjection will be stored and passed when service connects");
            } else {
                Log.w("RecordManager", "   MediaProjection is null, will be cleared when service connects");
            }
        }
    }

    public void cleanup() {
        if (recordService != null) {
            recordService = null;
        }
        
        try {
            context.unbindService(serviceConnection);
        } catch (Exception e) {
            Log.e("RecordManager", "解绑服务失败", e);
        }
        Log.d("RecordManager", "RecordManager已清理");
//        if (mediaProjection != null) {
//            mediaProjection.stop();
//            mediaProjection = null;
//        }
    }

    public void setWebSocketManager(WebSocketManager manager) {
        this.webSocketManager = manager;
    }

    public static RecordService getRecordService() {
        return recordService;
    }

    public void handleScreenCaptureIntent(int resultCode, Intent data) {
        try {
            if (context != null) {
                MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)
                        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

                // 将在RecordService中使用这些数据创建MediaProjection
                Intent intent = new Intent(context, RecordService.class);
                intent.putExtra("resultCode", resultCode);
                intent.putExtra("resultData", data);

                // 确保服务已经启动，然后发送权限数据
                context.startService(intent);

                Log.d("RecordManager", "Screen capture intent forwarded to service");
            }
        } catch (Exception e) {
            Log.e("RecordManager", "Failed to handle screen capture intent", e);
        }
    }
}