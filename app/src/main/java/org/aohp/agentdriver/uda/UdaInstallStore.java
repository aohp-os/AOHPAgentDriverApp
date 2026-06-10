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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Persists installed UDA apps (separate from generation job registry). */
public final class UdaInstallStore {
    private static final String TAG = "UdaInstallStore";

    private final File mInstallFile;
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();
    private final Map<String, UdaInstallInfo> mInstalls = new LinkedHashMap<>();

    public UdaInstallStore(@NonNull Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "uda");
        // eslint-disable-next-line ResultOfMethodCallIgnored
        dir.mkdirs();
        mInstallFile = new File(dir, "installs.json");
        loadFromDisk();
    }

    private void loadFromDisk() {
        mLock.writeLock().lock();
        try {
            mInstalls.clear();
            if (!mInstallFile.isFile()) {
                return;
            }
            try (FileInputStream in = new FileInputStream(mInstallFile)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    return;
                }
                JSONArray arr = new JSONArray(text);
                for (int i = 0; i < arr.length(); i++) {
                    UdaInstallInfo info = UdaInstallInfo.fromJson(arr.getJSONObject(i));
                    mInstalls.put(info.jobId, info);
                }
            } catch (Exception e) {
                Log.w(TAG, "load installs failed", e);
            }
        } finally {
            mLock.writeLock().unlock();
        }
    }

    private void persistLocked() {
        try {
            JSONArray arr = new JSONArray();
            for (UdaInstallInfo info : mInstalls.values()) {
                arr.put(info.toJson());
            }
            try (FileOutputStream out = new FileOutputStream(mInstallFile)) {
                out.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "persist installs failed", e);
        }
    }

    @Nullable
    public UdaInstallInfo get(@NonNull String jobId) {
        mLock.readLock().lock();
        try {
            return mInstalls.get(jobId);
        } finally {
            mLock.readLock().unlock();
        }
    }

    public boolean isInstalled(@NonNull String jobId) {
        return get(jobId) != null;
    }

    @NonNull
    public List<UdaInstallInfo> list() {
        mLock.readLock().lock();
        try {
            return new ArrayList<>(mInstalls.values());
        } finally {
            mLock.readLock().unlock();
        }
    }

    public void put(@NonNull UdaInstallInfo info) {
        mLock.writeLock().lock();
        try {
            mInstalls.put(info.jobId, info);
            persistLocked();
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public boolean remove(@NonNull String jobId) {
        mLock.writeLock().lock();
        try {
            boolean removed = mInstalls.remove(jobId) != null;
            if (removed) {
                persistLocked();
            }
            return removed;
        } finally {
            mLock.writeLock().unlock();
        }
    }
}
