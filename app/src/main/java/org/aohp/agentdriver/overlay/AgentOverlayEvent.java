package org.aohp.agentdriver.overlay;

/** Single timeline entry shown in the agent overlay panel. */
public final class AgentOverlayEvent {
    public final String type;
    public final String id;
    public String name;
    public String text;
    public String args;
    public final String output;
    public final boolean isError;
    public final Double durationS;
    public final long ts;
    /** LLM proxy request id — used to merge streaming thinking deltas. */
    public final String requestId;

    public AgentOverlayEvent(
            String type,
            String id,
            String name,
            String text,
            String args,
            String output,
            boolean isError,
            Double durationS,
            long ts,
            String requestId) {
        this.type = type != null ? type : "log";
        this.id = id;
        this.name = name;
        this.text = text;
        this.args = args;
        this.output = output;
        this.isError = isError;
        this.durationS = durationS;
        this.ts = ts;
        this.requestId = requestId;
    }

    public void appendText(String more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        if (text == null || text.isEmpty()) {
            text = more;
        } else {
            text = text + more;
        }
    }

    /** Replace args with a newer streaming snapshot (proxy sends full cumulative JSON). */
    public void replaceArgs(String updated) {
        if (updated == null || updated.isEmpty()) {
            return;
        }
        args = updated;
    }

    void mergeToolCallUpdate(AgentOverlayEvent incoming) {
        if (incoming == null) {
            return;
        }
        if (incoming.name != null && !incoming.name.isEmpty()) {
            if (name == null || name.isEmpty() || incoming.name.length() >= name.length()) {
                name = incoming.name;
            }
        }
        replaceArgs(incoming.args);
    }

    public String dedupeKey() {
        if ("tool_call".equals(type) && id != null && !id.isEmpty()) {
            return "tool_call:" + id;
        }
        if ("tool_result".equals(type) && id != null && !id.isEmpty()) {
            return "tool_result:" + id;
        }
        if ("thinking".equals(type) && requestId != null && !requestId.isEmpty()) {
            return "thinking:req:" + requestId;
        }
        if ("status".equals(type) && text != null) {
            return "status:" + text + ":" + ts;
        }
        return type + ":" + ts + ":" + (text != null ? text.hashCode() : 0);
    }

    boolean canMergeThinkingFrom(AgentOverlayEvent incoming) {
        if (!"thinking".equals(type) || !"thinking".equals(incoming.type)) {
            return false;
        }
        if (requestId != null && !requestId.isEmpty()
                && requestId.equals(incoming.requestId)) {
            return true;
        }
        if ((requestId == null || requestId.isEmpty())
                && (incoming.requestId == null || incoming.requestId.isEmpty())) {
            return true;
        }
        return false;
    }
}
