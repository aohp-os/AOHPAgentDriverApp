package org.aohp.agentdriver.executor;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.aohp.IAohpFileBridge;

import org.json.JSONObject;

/** Binder client for the {@code aohp_file_bridge} system service. */
public final class AohpFileBridgeClient {
    private static final String TAG = "AohpFileBridgeClient";
    public static final String SERVICE_NAME = "aohp_file_bridge";

    private volatile IAohpFileBridge mService;

    public AohpFileBridgeClient(Context context) {
    }

    public boolean isServiceAvailable() {
        return getService() != null;
    }

    public JSONObject stat(String path) {
        return call(() -> getServiceOrThrow().stat(path));
    }

    public JSONObject list(String path, JSONObject options) {
        return call(() -> getServiceOrThrow().list(path, json(options)));
    }

    public JSONObject recent(JSONObject options) {
        return call(() -> getServiceOrThrow().recent(json(options)));
    }

    public JSONObject snapshot(JSONObject options) {
        return call(() -> getServiceOrThrow().snapshot(json(options)));
    }

    public JSONObject diff(String beforeSnapshotId, String afterSnapshotId, JSONObject options) {
        return call(() -> getServiceOrThrow().diff(beforeSnapshotId, afterSnapshotId, json(options)));
    }

    private IAohpFileBridge getServiceOrThrow() throws RemoteException {
        IAohpFileBridge svc = getService();
        if (svc == null) {
            throw new RemoteException("aohp_file_bridge unavailable");
        }
        return svc;
    }

    private IAohpFileBridge getService() {
        IAohpFileBridge s = mService;
        if (s != null) return s;
        synchronized (this) {
            s = mService;
            if (s != null) return s;
            try {
                IBinder b = getServiceManagerBinder(SERVICE_NAME);
                if (b == null) return null;
                s = IAohpFileBridge.Stub.asInterface(b);
                mService = s;
            } catch (Throwable t) {
                Log.w(TAG, "getService failed: " + t.getMessage());
                return null;
            }
            return s;
        }
    }

    private static IBinder getServiceManagerBinder(String name) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            return (IBinder) sm.getMethod("getService", String.class).invoke(null, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private interface RemoteJsonCall {
        String run() throws RemoteException;
    }

    private static JSONObject call(RemoteJsonCall c) {
        try {
            String raw = c.run();
            return raw != null && raw.trim().startsWith("{")
                    ? new JSONObject(raw)
                    : error("bad_response", raw != null ? raw : "");
        } catch (Exception e) {
            return error("aohp_file_bridge_unavailable", e.getMessage());
        }
    }

    private static String json(JSONObject o) {
        return o != null ? o.toString() : "{}";
    }

    private static JSONObject error(String code, String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false);
            o.put("error", true);
            o.put("code", code);
            o.put("message", message != null ? message : code);
        } catch (Exception ignored) {
        }
        return o;
    }
}
