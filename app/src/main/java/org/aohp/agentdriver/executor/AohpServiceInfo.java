package org.aohp.agentdriver.executor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One row from {@code listServices} JSON array.
 */
public final class AohpServiceInfo {
    public String serviceId = "";
    public int pid = -1;
    public boolean alive;
    public long startTime;
    public long uptimeSec;
    public String command = "";

    public static List<AohpServiceInfo> listFromJson(String json) {
        List<AohpServiceInfo> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                AohpServiceInfo e = new AohpServiceInfo();
                e.serviceId = o.optString("serviceId", "");
                e.pid = o.optInt("pid", -1);
                e.alive = o.optBoolean("alive", false);
                e.startTime = o.optLong("startTime", 0);
                e.uptimeSec = o.optLong("uptimeSec", 0);
                e.command = o.optString("command", "");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public String statusLine() {
        String st = alive ? "running" : "stopped";
        return serviceId + " | " + st + " | pid=" + pid + " | up " + uptimeSec + "s";
    }
}
