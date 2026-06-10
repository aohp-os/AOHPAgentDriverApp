package org.aohp.agentdriver.executor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.aohp.agentdriver.executor.ws.AohpJsonRpcService;
import org.aohp.agentdriver.executor.ws.MyWebSocketServer;

public class WebSocketManager {
    private final Context context;
    private AohpJsonRpcService service;
    private ServiceConnection serviceConnection;
    private boolean isServiceBound = false;
    private MyWebSocketServer webSocketServer;

    public WebSocketManager(Context context) {
        this.context = context;
        initializeServiceConnection();
    }

    private void initializeServiceConnection() {
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d("WebSocketManager", "Service connected");
                AohpJsonRpcService.LocalBinder binder = (AohpJsonRpcService.LocalBinder) service;
                WebSocketManager.this.service = binder.getService();
                webSocketServer = WebSocketManager.this.service.getWebSocketServer();
                Log.d("WebSocketManager", "Got WebSocketServer: " + webSocketServer);
                isServiceBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0) {
                Log.d("WebSocketManager", "Service disconnected");
                isServiceBound = false;
                webSocketServer = null;
            }
        };
    }

    public void startService() {
        Intent intent = new Intent(context, AohpJsonRpcService.class);
        context.startService(intent);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d("WebSocketManager", "Send intent to AohpJsonRpcService");
        Log.d("WebSocketManager", "context: " + context + ", Intent: " + intent);
        
        // 重新获取并设置 AccessibilityService
//        MyAccessibilityService accessibilityService = MyAccessibilityService.getInstance();
//        if (webSocketServer != null && accessibilityService != null) {
//            webSocketServer.setAccessibilityService(accessibilityService);
//        }
    }

    public void stopService() {
        if (isServiceBound) {
            context.unbindService(serviceConnection);
            isServiceBound = false;
        }
        Intent intent = new Intent(context, AohpJsonRpcService.class);
        context.stopService(intent);
        webSocketServer = null;
    }

    public void cleanup() {
        if (isServiceBound) {
            context.unbindService(serviceConnection);
            isServiceBound = false;
        }
        webSocketServer = null;
    }
}