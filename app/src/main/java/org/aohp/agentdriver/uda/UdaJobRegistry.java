package org.aohp.agentdriver.uda;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Job registry in app-private storage (not /data/aohp — SELinux blocks platform_app). */
public final class UdaJobRegistry {
    private static final String TAG = "UdaJobRegistry";

    private final File mRegistryFile;
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();
    private final List<UdaJobInfo> mJobs = new ArrayList<>();

    public UdaJobRegistry(@NonNull Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "uda");
        // eslint-disable-next-line ResultOfMethodCallIgnored
        dir.mkdirs();
        mRegistryFile = new File(dir, "jobs.json");
        loadFromDisk();
    }

    private void loadFromDisk() {
        mLock.writeLock().lock();
        try {
            mJobs.clear();
            if (!mRegistryFile.isFile()) {
                return;
            }
            try (FileInputStream in = new FileInputStream(mRegistryFile)) {
                byte[] bytes = in.readAllBytes();
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    return;
                }
                JSONArray arr = new JSONArray(text);
                for (int i = 0; i < arr.length(); i++) {
                    UdaJobInfo job = fromJson(arr.getJSONObject(i));
                    if (job != null) {
                        mJobs.add(job);
                    }
                }
                Collections.sort(mJobs, Comparator.comparingLong(j -> -j.createdAtMs));
            } catch (Exception e) {
                Log.w(TAG, "load registry failed", e);
            }
        } finally {
            mLock.writeLock().unlock();
        }
    }

    private void persistLocked() {
        try {
            JSONArray arr = new JSONArray();
            for (UdaJobInfo job : mJobs) {
                arr.put(job.toJson(false));
            }
            try (FileOutputStream out = new FileOutputStream(mRegistryFile)) {
                out.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "persist registry failed", e);
        }
    }

    @Nullable
    private static UdaJobInfo fromJson(JSONObject o) {
        try {
            String jobId = o.getString("jobId");
            String appName = o.optString("appName", jobId);
            String idea = o.optString("idea", "");
            long createdAt = o.optLong("createdAtMs", System.currentTimeMillis());
            UdaJobStatus status =
                    UdaJobStatus.valueOf(o.optString("status", "pending").toUpperCase());
            UdaJobInfo info = new UdaJobInfo(jobId, appName, idea, createdAt, status);
            info.stage = o.optString("stage", null);
            info.logTail = o.optString("logTail", null);
            info.errorMessage = o.optString("errorMessage", null);
            info.demo = o.optBoolean("demo", UdaDemoCatalog.isDemoJobId(jobId));
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    public List<UdaJobInfo> listJobs() {
        mLock.readLock().lock();
        try {
            List<UdaJobInfo> copy = new ArrayList<>(mJobs);
            Collections.sort(
                    copy,
                    (a, b) -> {
                        if (a.demo != b.demo) {
                            return a.demo ? -1 : 1;
                        }
                        return Long.compare(b.createdAtMs, a.createdAtMs);
                    });
            return copy;
        } finally {
            mLock.readLock().unlock();
        }
    }

    @Nullable
    public UdaJobInfo getJob(@NonNull String jobId) {
        mLock.readLock().lock();
        try {
            for (UdaJobInfo j : mJobs) {
                if (jobId.equals(j.jobId)) {
                    return j;
                }
            }
            return null;
        } finally {
            mLock.readLock().unlock();
        }
    }

    public void addJob(@NonNull UdaJobInfo job) {
        mLock.writeLock().lock();
        try {
            mJobs.add(0, job);
            persistLocked();
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public void updateJob(@NonNull UdaJobInfo job) {
        mLock.writeLock().lock();
        try {
            for (int i = 0; i < mJobs.size(); i++) {
                if (job.jobId.equals(mJobs.get(i).jobId)) {
                    mJobs.set(i, job);
                    persistLocked();
                    return;
                }
            }
            mJobs.add(0, job);
            persistLocked();
        } finally {
            mLock.writeLock().unlock();
        }
    }

    /** Removes job metadata only; container output is deleted separately. */
    public boolean removeJob(@NonNull String jobId) {
        mLock.writeLock().lock();
        try {
            boolean removed = false;
            for (int i = mJobs.size() - 1; i >= 0; i--) {
                if (jobId.equals(mJobs.get(i).jobId)) {
                    mJobs.remove(i);
                    removed = true;
                }
            }
            if (removed) {
                persistLocked();
            }
            return removed;
        } finally {
            mLock.writeLock().unlock();
        }
    }
}
