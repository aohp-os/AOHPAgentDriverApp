package org.aohp.agentdriver.ad;

import android.content.Context;
import android.view.Display;

import org.aohp.agentdriver.executor.AohpAdClient;
import org.json.JSONObject;

public final class AohpAdManager {
    private final Context mContext;
    private final AohpAdClient mClient;

    private AohpAdManager(Context context) {
        mContext = context.getApplicationContext();
        mClient = new AohpAdClient(mContext);
    }

    public static AohpAdManager get(Context context) {
        return new AohpAdManager(context);
    }

    public boolean isAvailable() {
        return mClient.isServiceAvailable();
    }

    public AohpAdDecision registerSlot(AohpAdSlot slot, int displayId) {
        try {
            return new AohpAdDecision(mClient.registerSlot(slot.toJson(mContext.getPackageName(), displayId)));
        } catch (Exception e) {
            return new AohpAdDecision(error(e));
        }
    }

    public AohpAdDecision requestDecision(AohpAdSlot slot, int displayId) {
        try {
            JSONObject req = slot.toJson(mContext.getPackageName(), displayId);
            return new AohpAdDecision(mClient.requestDecision(slot.slotId, req));
        } catch (Exception e) {
            return new AohpAdDecision(error(e));
        }
    }

    public AohpAdDecision submitOpportunity(AohpAdOpportunity opportunity, int displayId) {
        try {
            return new AohpAdDecision(
                    mClient.submitOpportunity(opportunity.toJson(mContext.getPackageName(), displayId)));
        } catch (Exception e) {
            return new AohpAdDecision(error(e));
        }
    }

    public JSONObject reportEvent(String slotId, String type, int displayId) {
        try {
            JSONObject event = new JSONObject()
                    .put("type", type)
                    .put("displayId", displayId)
                    .put("packageName", mContext.getPackageName());
            return mClient.reportEvent(slotId, event);
        } catch (Exception e) {
            return error(e);
        }
    }

    public static int displayIdFor(Context context) {
        Display display = context.getDisplay();
        return display != null ? display.getDisplayId() : Display.DEFAULT_DISPLAY;
    }

    private static JSONObject error(Exception e) {
        try {
            return new JSONObject()
                    .put("ok", false)
                    .put("error", true)
                    .put("message", e.getMessage());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
