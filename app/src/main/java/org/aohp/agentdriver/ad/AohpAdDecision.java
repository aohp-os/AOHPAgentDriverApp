package org.aohp.agentdriver.ad;

import org.json.JSONObject;

public final class AohpAdDecision {
    public final boolean ok;
    public final String decision;
    public final JSONObject raw;

    AohpAdDecision(JSONObject raw) {
        this.raw = raw;
        ok = raw != null && raw.optBoolean("ok", false);
        decision = raw != null ? raw.optString("decision", "ERROR") : "ERROR";
    }

    public boolean shouldRenderInApp() {
        return "RENDER_IN_APP".equals(decision) || "HOSTED_BY_SYSTEM".equals(decision);
    }

    public boolean isSuppressedForAgent() {
        return "SUPPRESS_FOR_AGENT".equals(decision) || "DEFERRED_TO_HUMAN".equals(decision);
    }
}
