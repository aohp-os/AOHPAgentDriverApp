package org.aohp.agentdriver.executor;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks AOHP-privileged virtual displays created via {@link AohpVdClient#createVirtualDisplay}
 * (not MediaProjection).
 */
public final class MultiVirtualDisplayManager {
    private static final String TAG = "MultiVdMgr";

    private static volatile MultiVirtualDisplayManager sInstance;

    private final AohpVdClient mClient;
    private final Set<Integer> mDisplayIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static MultiVirtualDisplayManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (MultiVirtualDisplayManager.class) {
                if (sInstance == null) {
                    sInstance = new MultiVirtualDisplayManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private MultiVirtualDisplayManager(Context appContext) {
        mClient = AohpVdClient.getInstance(appContext);
    }

    public ShellExecutor.CommandResult createDisplay(String name, int w, int h, int d, int flags) {
        ShellExecutor.CommandResult r = mClient.createVirtualDisplay(name, w, h, d, flags);
        if (r.success) {
            try {
                int id = Integer.parseInt(r.output.trim());
                mDisplayIds.add(id);
                Log.i(TAG, "created displayId=" + id);
            } catch (NumberFormatException e) {
                Log.w(TAG, "parse display id: " + r.output);
            }
        }
        return r;
    }

    public ShellExecutor.CommandResult destroyDisplay(int displayId) {
        ShellExecutor.CommandResult r = mClient.destroyVirtualDisplay(displayId);
        if (r.success) {
            mDisplayIds.remove(displayId);
        }
        return r;
    }

    public Set<Integer> getKnownDisplayIds() {
        return Collections.unmodifiableSet(mDisplayIds);
    }
}
