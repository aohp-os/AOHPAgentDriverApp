package org.aohp.agentdriver.uda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.aohp.agentdriver.MainActivity;
import org.aohp.agentdriver.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Foreground service that runs UDAGen generation in a background thread. */
public final class UdaGenerationService extends Service {
    private static final String TAG = "UdaGenerationService";
    private static final String CHANNEL_ID = "uda_generation";
    private static final int NOTIFICATION_ID = 42;

    public static final String ACTION_START = "org.aohp.agentdriver.uda.START";
    public static final String EXTRA_JOB_ID = "jobId";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger mActiveJobs = new AtomicInteger(0);

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            getString(R.string.uda_notification_channel),
                            NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        String jobId = intent.getStringExtra(EXTRA_JOB_ID);
        if (jobId == null || jobId.isEmpty()) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        mActiveJobs.incrementAndGet();
        startForeground(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.uda_notification_generating), jobId));

        mExecutor.execute(
                () -> {
                    try {
                        UdaManager.getInstance(this).runGenerationBlocking(jobId);
                        UdaJobInfo job = UdaManager.getInstance(this).registry().getJob(jobId);
                        String title =
                                job != null && job.status == UdaJobStatus.COMPLETED
                                        ? getString(R.string.uda_notification_complete)
                                        : getString(R.string.uda_notification_finished);
                        updateNotification(title, jobId);
                    } catch (Exception e) {
                        Log.e(TAG, "generation failed", e);
                    } finally {
                        if (mActiveJobs.decrementAndGet() <= 0) {
                            stopForeground(STOP_FOREGROUND_REMOVE);
                            stopSelf();
                        }
                    }
                });
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String text, String jobId) {
        Intent open =
                new Intent(this, MainActivity.class)
                        .putExtra("uda_job_id", jobId)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi =
                PendingIntent.getActivity(
                        this,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text, String jobId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text, jobId));
        }
    }

    private void stopIfIdle() {
        if (mActiveJobs.get() <= 0) {
            stopSelf();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }
}
