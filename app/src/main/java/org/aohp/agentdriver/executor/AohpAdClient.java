package org.aohp.agentdriver.executor;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.aohp.IAohpAdManager;

import org.json.JSONObject;

/** Binder client for the {@code aohp_ad} system service. */
public final class AohpAdClient {
    private static final String TAG = "AohpAdClient";
    public static final String SERVICE_NAME = "aohp_ad";

    private volatile IAohpAdManager mService;

    public AohpAdClient(Context context) {
    }

    public boolean isServiceAvailable() {
        return getService() != null;
    }

    public JSONObject registerSlot(JSONObject slot) {
        return call(() -> getServiceOrThrow().registerSlot(json(slot)));
    }

    public JSONObject requestDecision(String slotId, JSONObject request) {
        return call(() -> getServiceOrThrow().requestDecision(slotId, json(request)));
    }

    public JSONObject reportEvent(String slotId, JSONObject event) {
        return call(() -> getServiceOrThrow().reportEvent(slotId, json(event)));
    }

    public JSONObject submitOpportunity(JSONObject opportunity) {
        return call(() -> getServiceOrThrow().submitOpportunity(json(opportunity)));
    }

    public JSONObject getHostState(JSONObject query) {
        return call(() -> getServiceOrThrow().getHostState(json(query)));
    }

    public JSONObject recordHostEvent(JSONObject event) {
        return call(() -> getServiceOrThrow().recordHostEvent(json(event)));
    }

    public JSONObject runAdapterTest(JSONObject test) {
        return call(() -> getServiceOrThrow().runAdapterTest(json(test)));
    }

    public JSONObject getState(JSONObject query) {
        return call(() -> getServiceOrThrow().getState(json(query)));
    }

    public JSONObject setPolicy(JSONObject policy) {
        return call(() -> getServiceOrThrow().setPolicy(json(policy)));
    }

    public JSONObject clearEvents() {
        try {
            getServiceOrThrow().clearEvents();
            return new JSONObject().put("ok", true);
        } catch (Exception e) {
            return error("clear_events", e.getMessage());
        }
    }

    public JSONObject drainEvents(JSONObject options) {
        return call(() -> getServiceOrThrow().drainEvents(json(options)));
    }

    public JSONObject runSelfTest(JSONObject options) {
        return call(() -> getServiceOrThrow().runSelfTest(json(options)));
    }

    private IAohpAdManager getServiceOrThrow() throws RemoteException {
        IAohpAdManager svc = getService();
        if (svc == null) {
            throw new RemoteException("aohp_ad unavailable");
        }
        return svc;
    }

    private IAohpAdManager getService() {
        IAohpAdManager s = mService;
        if (s != null) return s;
        synchronized (this) {
            s = mService;
            if (s != null) return s;
            try {
                IBinder b = getServiceManagerBinder(SERVICE_NAME);
                if (b == null) return null;
                s = IAohpAdManager.Stub.asInterface(b);
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

    private JSONObject call(RemoteJsonCall call) {
        try {
            String s = call.run();
            if (s == null || s.isEmpty()) {
                return error("empty", "empty response");
            }
            return new JSONObject(s);
        } catch (Exception e) {
            return error("aohp_ad", e.getMessage());
        }
    }

    private static JSONObject error(String code, String message) {
        try {
            return new JSONObject()
                    .put("ok", false)
                    .put("error", true)
                    .put("code", code)
                    .put("message", message != null ? message : "");
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String json(JSONObject o) {
        return o != null ? o.toString() : "{}";
    }

    private interface RemoteJsonCall {
        String run() throws Exception;
    }
}
