package org.aohp.agentdriver.overlay;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.aohp.agentdriver.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AgentOverlayEventAdapter extends RecyclerView.Adapter<AgentOverlayEventAdapter.Holder> {

    static final Object PAYLOAD_BODY = new Object();

    private final List<AgentOverlayEvent> events = new ArrayList<>();

    void setEvents(List<AgentOverlayEvent> next) {
        events.clear();
        events.addAll(next);
        notifyDataSetChanged();
    }

    void appendEvents(List<AgentOverlayEvent> more) {
        if (more.isEmpty()) {
            return;
        }
        int start = events.size();
        events.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    void notifyEventChanged(int position) {
        if (position < 0 || position >= events.size()) {
            return;
        }
        notifyItemChanged(position);
    }

    void notifyEventChanged(int position, Object payload) {
        if (position < 0 || position >= events.size()) {
            return;
        }
        notifyItemChanged(position, payload);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            AgentOverlayEvent event = events.get(position);
            String body = bodyFor(event);
            holder.body.setText(body);
            holder.body.setVisibility(body.length() > 0 ? View.VISIBLE : View.GONE);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agent_overlay_event, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AgentOverlayEvent event = events.get(position);
        holder.icon.setText(iconFor(event));
        holder.title.setText(titleFor(event));
        holder.title.setTextColor(colorFor(event));
        String body = bodyFor(event);
        holder.body.setText(body);
        holder.body.setVisibility(body.length() > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private static String iconFor(AgentOverlayEvent event) {
        String type = event.type;
        if (type == null) {
            return "•";
        }
        switch (type) {
            case "thinking":
                return "💭";
            case "tool_call":
                if (ToolCallFormatter.isSkillReadCall(event.name, event.args)) {
                    return "📖";
                }
                return "🔧";
            case "tool_result":
                return event.isError ? "❌" : "✅";
            case "assistant_text":
                return "🤖";
            case "status":
                return "ℹ️";
            case "log":
            default:
                return "│";
        }
    }

    private static int colorFor(AgentOverlayEvent event) {
        if ("tool_result".equals(event.type) && event.isError) {
            return Color.parseColor("#FF6B6B");
        }
        if ("thinking".equals(event.type)) {
            return Color.parseColor("#B388FF");
        }
        if ("tool_call".equals(event.type)) {
            return Color.parseColor("#FFB74D");
        }
        if ("tool_result".equals(event.type)) {
            return Color.parseColor("#81C784");
        }
        return Color.parseColor("#E8EAF6");
    }

    private static String titleFor(AgentOverlayEvent event) {
        if ("thinking".equals(event.type)) {
            return "Thinking";
        }
        if ("tool_call".equals(event.type)) {
            String skillName = ToolCallFormatter.skillNameFromReadCall(event.name, event.args);
            if (skillName != null) {
                return "Reading skill: " + skillName;
            }
            String name = event.name != null && !event.name.isEmpty() ? event.name : "tool";
            return "Tool call: " + name;
        }
        if ("tool_result".equals(event.type)) {
            String name = event.name != null && !event.name.isEmpty() ? event.name : "tool";
            if ("read".equalsIgnoreCase(name)) {
                String skillSummary = ToolCallFormatter.formatSkillReadResult(event.output);
                if (skillSummary != null && !skillSummary.isEmpty()) {
                    String suffix = event.isError ? " (error)" : "";
                    if (event.durationS != null) {
                        return String.format(Locale.US, "Skill loaded%s (%.1fs)", suffix, event.durationS);
                    }
                    return "Skill loaded" + suffix;
                }
            }
            String suffix = event.isError ? " (error)" : "";
            if (event.durationS != null) {
                return String.format(Locale.US, "Result: %s%s (%.1fs)", name, suffix, event.durationS);
            }
            return "Result: " + name + suffix;
        }
        if ("assistant_text".equals(event.type)) {
            return "Assistant";
        }
        if ("status".equals(event.type)) {
            return event.text != null ? event.text : "Status";
        }
        return event.name != null && !event.name.isEmpty() ? event.name : "Log";
    }

    private static String bodyFor(AgentOverlayEvent event) {
        if ("thinking".equals(event.type)) {
            return event.text != null ? event.text.trim() : "";
        }
        if ("assistant_text".equals(event.type) || "log".equals(event.type)) {
            return truncate(event.text, 600);
        }
        if ("tool_call".equals(event.type)) {
            return truncate(ToolCallFormatter.formatBody(event.name, event.args), 1200);
        }
        if ("tool_result".equals(event.type)) {
            if ("read".equalsIgnoreCase(event.name)) {
                String skillSummary = ToolCallFormatter.formatSkillReadResult(event.output);
                if (skillSummary != null && !skillSummary.isEmpty()) {
                    return truncate(skillSummary, 200);
                }
            }
            return truncate(ToolCallFormatter.formatBody(event.name, event.output), 400);
        }
        return truncate(event.text, 300);
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView title;
        final TextView body;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.tv_event_icon);
            title = itemView.findViewById(R.id.tv_event_title);
            body = itemView.findViewById(R.id.tv_event_body);
        }
    }
}
