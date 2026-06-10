package org.aohp.agentdriver.executor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.aohp.agentdriver.R;

public class RecordService extends Service {
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private VirtualDisplay virtualDisplayScreenshot;
    public ImageReader imageReader;
    private boolean running;
    private int width;
    private int height;
    private int dpi;
    private String currentRecordingPath;
    private MediaProjection.Callback mediaProjectionCallback;
    private boolean isForegroundStarted = false;  // 标记前台服务是否已启动

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("RecordService", "========== onBind() called ==========");
        Log.d("RecordService", "Intent: " + intent);
        RecordBinder binder = new RecordBinder();
        Log.d("RecordService", "Returning RecordBinder: " + binder);
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("RecordService", "========== onStartCommand() called ==========");
        Log.d("RecordService", "Intent: " + intent);
        Log.d("RecordService", "Flags: " + flags + ", StartId: " + startId);
        return START_STICKY;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("RecordService", "========== onCreate() START ==========");

        // 获取真实的屏幕尺寸
        android.view.WindowManager windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        android.view.Display display = windowManager.getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getRealSize(size);

        width = size.x;
        height = size.y;
        dpi = getResources().getDisplayMetrics().densityDpi;
        
        Log.d("RecordService", "Screen size: " + width + "x" + height + ", DPI: " + dpi);

        HandlerThread serviceThread = new HandlerThread("service_thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();
        
        running = false;
        
        Log.d("RecordService", "Creating ImageReader...");
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);
        
        Log.d("RecordService", "Creating MediaRecorder...");
        mediaRecorder = new MediaRecorder();
        
        // 初始化MediaProjection回调
        Log.d("RecordService", "Initializing MediaProjection callback...");
        mediaProjectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.w("RecordService", "⚠️ MediaProjection stopped by user or system");
                // ⚠️ 关键：清除 mediaProjection 引用，避免使用已失效的对象
                mediaProjection = null;
                
                // 释放 VirtualDisplay
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }
                if (virtualDisplayScreenshot != null) {
                    virtualDisplayScreenshot.release();
                    virtualDisplayScreenshot = null;
                }
                
                Log.w("RecordService", "⚠️ MediaProjection cleared, screenshot/recording will fail until re-granted");
            }
        };
        
        Log.d("RecordService", "========== onCreate() COMPLETED ==========");

        // 创建通知渠道（仅在 API 26+ 上需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("aohp_record_channel",
                    getString(R.string.app_name) + " 录屏",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // ⚠️ Android 14+ 重要变更：
        // 不要在 onCreate() 中立即调用 startForeground()！
        // 对于 mediaProjection 类型的前台服务，必须等到 MediaProjection 权限已获取后才能调用。
        // 否则会触发 SecurityException: Starting FGS with type mediaProjection requires permissions
        Log.d("RecordService", "onCreate completed - foreground service will start when MediaProjection is set");
    }

    /**
     * 启动前台服务（仅在 MediaProjection 已设置后调用）
     * 
     * Android 14+ 要求：调用带 mediaProjection 类型的 startForeground() 时，
     * 必须已经拥有有效的 MediaProjection token。
     */
    private void startForegroundServiceIfNeeded() {
        if (isForegroundStarted) {
            Log.d("RecordService", "Foreground service already started");
            return;
        }
        
        Log.d("RecordService", "Starting foreground service...");
        
        // 创建前台服务通知
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, "aohp_record_channel")
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.app_name) + " 录屏/截图服务运行中")
                    .setSmallIcon(R.drawable.ic_notification)
                    .build();
        }

        // 启动前台服务
        try {
            Log.d("RecordService", "startForeground with mediaProjection type");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 需要指定前台服务类型
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1, notification);
            }
            isForegroundStarted = true;
            Log.d("RecordService", "✅ Foreground service started successfully");
        } catch (Exception e) {
            Log.e("RecordService", "❌ Failed to start foreground service: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * 设置 MediaProjection，在 MainActivity 中调用
     * 
     * @param project 要设置的 MediaProjection
     */
    public void setMediaProject(MediaProjection project) {
        Log.d("RecordService", "========== setMediaProject() called ==========");
        Log.d("RecordService", "Current mediaProjection: " + (mediaProjection == null ? "NULL" : "EXISTS"));
        Log.d("RecordService", "New project parameter: " + (project == null ? "NULL (clearing)" : "EXISTS"));
        
        // ⚠️ 如果传递 null，表示 MediaProjection 已失效，需要清除
        if (project == null) {
            Log.w("RecordService", "⚠️ Clearing MediaProjection (expired or revoked)");
            if (mediaProjection != null) {
                // 移除回调
                try {
                    mediaProjection.unregisterCallback(mediaProjectionCallback);
                } catch (Exception e) {
                    Log.w("RecordService", "Error unregistering callback", e);
                }
                mediaProjection = null;
            }
            // 释放 VirtualDisplay
            if (virtualDisplayScreenshot != null) {
                virtualDisplayScreenshot.release();
                virtualDisplayScreenshot = null;
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            Log.d("RecordService", "✅ MediaProjection cleared");
            return;
        }
        
        // 设置新的 MediaProjection
        if (mediaProjection == null) {
            Log.d("RecordService", "✅ Setting MediaProjection...");
            mediaProjection = project;
            
            // 注册回调
            Log.d("RecordService", "Registering MediaProjection callback...");
            mediaProjection.registerCallback(mediaProjectionCallback, null);
            
            Log.d("RecordService", "✅ MediaProjection set successfully: " + (mediaProjection != null));
        } else {
            // 如果已有 MediaProjection，先清理旧的
            Log.w("RecordService", "⚠️ MediaProjection already set, replacing...");
            try {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
            } catch (Exception e) {
                Log.w("RecordService", "Error unregistering old callback", e);
            }
            mediaProjection = project;
            mediaProjection.registerCallback(mediaProjectionCallback, null);
            Log.d("RecordService", "✅ MediaProjection replaced");
        }

        // ⚠️ 关键：在 MediaProjection 设置成功后，才启动前台服务
        // 这样可以确保符合 Android 14+ 的权限要求
        startForegroundServiceIfNeeded();
//        initRecorder();
    }

    /**
     * 初始化录屏服务
     */
    public void initRecorder() {
        try {
            // 确保先初始化 MediaRecorder
            initMediaRecorder();
            // 然后再创建 VirtualDisplay
            createVirtualDisplay();
            Log.d("RecordService", "initRecorder success");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始录屏
     * 
     * @return 是否成功
     */
    public boolean startScreenRecord() {
        if (mediaProjection == null || running) {
            return false;
        }
        // initRecorder();
        // createVirtualDisplay();
        mediaRecorder.start();
        running = true;
        return true;
    }

    /**
     * 停止录屏
     * 
     * @return 是否成功
     */
    public String stopScreenRecord() {
        if (!running) {
            return null;
        }
        running = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        virtualDisplay.release();
        Log.d("RecordService", "stop mediaProjection before");
        mediaProjection.stop();
        Log.d("RecordService", "stop mediaProjection after");

        String recordingPath = currentRecordingPath;
        currentRecordingPath = null;
        return recordingPath;
    }

    /**
     * 创建用于录屏和截屏的 VirtualDisplay
     */
    private void createVirtualDisplay() {
        // 创建用于录屏的 VirtualDisplay
        Log.d("RecordService", "createVirtualDisplay call mediaProjection.createVirtualDisplay, mediaProjection: " + (mediaProjection != null));
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecord", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
        // 创建用于截屏的 VirtualDisplay
        virtualDisplayScreenshot = mediaProjection.createVirtualDisplay("ScreenCapture",
                width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    /**
     * 初始化 MediaRecorder，用于录制屏幕
     */
    private void initMediaRecorder() throws IOException {
        mediaRecorder.reset();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        String saveDir = getSaveDirectory();
        if (saveDir == null) {
            throw new IOException("Cannot create save directory");
        }
        currentRecordingPath = new File(saveDir, System.currentTimeMillis() + ".mp4").getAbsolutePath();
        mediaRecorder.setOutputFile(currentRecordingPath);

        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mediaRecorder.setVideoFrameRate(30);

        mediaRecorder.prepare();
    }

    /**
     * 将Bitmap保存为PNG格式
     * 
     * @param bitmap    要保存的Bitmap
     * @param imagePath 保存路径
     */
    private void saveBitmapAsPNG(Bitmap bitmap, String imagePath) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(imagePath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取保存截屏和录屏的目录
     * 
     * @return 保存目录路径
     */
    public String getSaveDirectory() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // 使用应用专属存储空间
            File mediaDir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Recordings");
            if (!mediaDir.exists()) {
                if (!mediaDir.mkdirs()) {
                    return null;
                }
            }
            return mediaDir.getAbsolutePath();
        }
        return null;
    }

    /**
     * 获取 RecordService 的 Binder
     */
    public class RecordBinder extends Binder {
        public RecordService getRecordService() {
            return RecordService.this;
        }
    }

    /**
     * 进行一次截图
     * 
     * @return 截图路径
     */
    public String takeScreenshot() {
        Log.d("takeScreenshot", "width: " + width + " height: " + height + " mediaProjection: " + (mediaProjection != null));
        if (mediaProjection == null) {
            Log.e("takeScreenshot", "MediaProjection is null");
            return null;
        }

        // 如果还没创建截屏用的VirtualDisplay，先创建它
        if (virtualDisplayScreenshot == null) {
            Log.d("takeScreenshot", "createVirtualDisplay for screenshot");
            virtualDisplayScreenshot = mediaProjection.createVirtualDisplay("ScreenCapture",
                    width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
            Log.d("takeScreenshot", "virtualDisplayScreenshot: " + (virtualDisplayScreenshot != null));
        }

        // 等待一小段时间确保截图准备就绪
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Image image = imageReader.acquireLatestImage();

        if (image == null) {
            return null;
        }

        // 获取图像数据
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        // 创建一个临时的Bitmap，包含padding
        Bitmap tempBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        tempBitmap.copyPixelsFromBuffer(buffer);

        // 创建最终的Bitmap，裁剪掉多余的部分
        Bitmap bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height);
        tempBitmap.recycle(); // 释放临时Bitmap

        // 保存图片
        String imageDir = getSaveDirectory();
        String imagePath = imageDir + "screenshot_" + System.currentTimeMillis() + ".png";
        saveBitmapAsPNG(bitmap, imagePath);
        
        // 打印截屏保存路径,方便验证
        Log.i("RecordService", "📸 截屏已保存到: " + imagePath);
        Log.i("RecordService", "📁 完整路径: " + new File(imagePath).getAbsolutePath());

        // 释放资源
        image.close();

        return imagePath;
    }

}
