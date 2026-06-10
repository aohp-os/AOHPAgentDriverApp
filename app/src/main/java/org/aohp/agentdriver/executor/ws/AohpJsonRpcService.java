package org.aohp.agentdriver.executor.ws;

//import static android.os.Build.VERSION_CODES.R;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.MainActivity;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AohpJsonRpcService extends Service {
    private MyWebSocketServer server;
    private final IBinder binder = new LocalBinder();

    private PowerManager.WakeLock wakeLock;

    NotificationCompat.Builder builder;
    NotificationManager manager;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 添加 Binder 类
    public class LocalBinder extends Binder {
        public AohpJsonRpcService getService() {
            return AohpJsonRpcService.this;
        }
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("AohpJsonRpcService", "AohpJsonRpcService onCreate");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("aohp_json_rpc", "AOHP JSON-RPC", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "aohp_json_rpc")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.app_name) + " WebSocket 服务运行中")
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setTicker("lala");

        Notification notification =  builder.build();

        startForeground(1, notification);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PStreamCollectService wakelock");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        executorService.execute(() -> {
            Log.d("AohpJsonRpcService", "onStartCommand");
            try {
                // 如果存在服务器实例，确保正确清理
                if (server != null) {
                    try {
                        server.stop(1000); // 给予1秒的优雅关闭时间
                        Thread.sleep(500); // 等待一下确保端口释放
                    } catch (Exception e) {
                        Log.e("AohpJsonRpcService", "Error stopping existing server", e);
                    }
                }
                
                // 创建新的服务器实例
                InetSocketAddress myHost = new InetSocketAddress("0.0.0.0", 6666);
                server = new MyWebSocketServer(myHost);
                server.setReuseAddr(true);
                server.setConnectionLostTimeout(60); // 设置连接超时时间为60秒
                server.setContext(getApplicationContext());

                // 添加日志
                Log.d("AohpJsonRpcService", "Starting WebSocket server on port 6666");
                server.start();
                
                // 等待服务器完全启动
                Thread.sleep(1000);
                Log.d("AohpJsonRpcService", "WebSocket server started successfully");
                
            } catch (Exception e) {
                Log.e("AohpJsonRpcService", "Failed to start WebSocket server", e);
            }
        });
        
        return START_STICKY; // 改为 START_STICKY，这样服务被杀死后会尝试重新启动
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("AohpJsonRpcService", "onBind called");
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d("AohpJsonRpcService", "onDestroy called");
        if (server != null) {
            try {
                server.stop(1000); // 给予1秒的优雅关闭时间
            } catch (Exception e) {
                Log.e("AohpJsonRpcService", "Error stopping WebSocket server", e);
            } finally {
                server = null;
            }
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e("AohpJsonRpcService", "Error releasing wakelock", e);
            }
        }
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        super.onDestroy();
    }

    public void start(Context context) {
        Intent serviceIntent = new Intent(context, AohpJsonRpcService.class);
        context.startService(serviceIntent);
    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // 判断是否使用IPv4
                        boolean isIPv4 = ip.indexOf(':') < 0;
                        if (useIPv4) {
                            if (isIPv4)
                                return ip;
                        } else {
                            if (!isIPv4) {
                                int delim = ip.indexOf('%'); // 去掉IPv6地址后面的zone index
                                return delim < 0 ? ip.toUpperCase() : ip.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // 添加获取 WebSocket 的方法
    public MyWebSocketServer getWebSocketServer() {
        Log.d("AohpJsonRpcService", "getWebSocketServer called, server: " + server);
        return server;
    }
}
