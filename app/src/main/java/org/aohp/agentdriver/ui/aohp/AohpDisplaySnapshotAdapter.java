package org.aohp.agentdriver.ui.aohp;

import android.content.Context;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.aohp.agentdriver.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 展示 AOHP {@code getDisplayRuntimeSnapshotJson} 解析结果：按屏分组 + RootTask 行。
 */
public final class AohpDisplaySnapshotAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 1;
    public static final int TYPE_TASK = 2;

    public abstract static class Row {
        public abstract int viewType();
    }

    public static final class HeaderRow extends Row {
        public final String title;
        public final String subtitle;

        public HeaderRow(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        @Override
        public int viewType() {
            return TYPE_HEADER;
        }
    }

    public static final class TaskRow extends Row {
        public final String primary;
        public final String secondary;

        public TaskRow(String primary, String secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public int viewType() {
            return TYPE_TASK;
        }
    }

    private final List<Row> mRows = new ArrayList<>();

    public void setRows(List<Row> rows) {
        mRows.clear();
        if (rows != null) {
            mRows.addAll(rows);
        }
        notifyDataSetChanged();
    }

    /**
     * 将服务端 JSON 转为列表项；含 error 字段时抛出 {@link JSONException}。
     */
    @NonNull
    public static List<Row> parseSnapshotJson(@NonNull Context context, String json)
            throws JSONException {
        List<Row> out = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            throw new JSONException("empty json");
        }
        JSONObject root = new JSONObject(json);
        if (root.has("error")) {
            throw new JSONException(root.optString("error", "unknown error"));
        }
        JSONArray displays = root.optJSONArray("displays");
        if (displays == null) {
            return out;
        }
        for (int i = 0; i < displays.length(); i++) {
            JSONObject d = displays.getJSONObject(i);
            int displayId = d.optInt("displayId", -1);
            JSONObject di = d.optJSONObject("display");
            String name = "";
            int lw = 0, lh = 0, state = 0, rot = 0;
            if (di != null) {
                name = di.optString("name", "");
                lw = di.optInt("logicalWidth", 0);
                lh = di.optInt("logicalHeight", 0);
                state = di.optInt("state", 0);
                rot = di.optInt("rotation", 0);
            }
            String sub = lw + "×" + lh + "  ·  rotation=" + rot + "  ·  state=" + state;
            if (!name.isEmpty()) {
                sub = name + "  ·  " + sub;
            }
            boolean canHost = d.optBoolean("canHostTasks", true);
            sub += "\ncanHostTasks=" + canHost;
            if (!d.isNull("topRunningActivity")) {
                sub += "  ·  topRunning=" + d.optString("topRunningActivity");
            }
            if (!d.isNull("focusedActivity")) {
                sub += "  ·  focused=" + d.optString("focusedActivity");
            }
            out.add(new HeaderRow("Display " + displayId, sub));

            JSONArray tasks = d.optJSONArray("rootTasks");
            if (tasks == null) {
                continue;
            }
            for (int t = 0; t < tasks.length(); t++) {
                JSONObject task = tasks.getJSONObject(t);
                int taskId = task.optInt("taskId", -1);
                boolean visible = task.optBoolean("visible", false);
                int pos = task.optInt("position", -1);
                String top = task.isNull("topActivity") ? "(null)" : task.optString("topActivity");
                String base = task.isNull("baseActivity") ? "(null)" : task.optString("baseActivity");
                int nAct = task.optInt("numActivities", 0);
                String visibility =
                        context.getString(
                                visible ? R.string.snapshot_task_visible : R.string.snapshot_task_hidden);
                String primary =
                        context.getString(
                                R.string.snapshot_task_line, taskId, pos, visibility, nAct);
                String secondary = "top: " + top + "\nbase: " + base;
                out.add(new TaskRow(primary, secondary));
            }
        }
        return out;
    }

    /**
     * 从 {@code getDisplayRuntimeSnapshotJson} 返回的 JSON 中取出逻辑屏 id（升序）。
     *
     * @param excludeDefaultDisplay 为 true 时去掉内置主屏 {@link Display#DEFAULT_DISPLAY}，供「虚拟屏」选择器使用。
     */
    @NonNull
    public static List<Integer> listDisplayIdsFromSnapshotJson(String json, boolean excludeDefaultDisplay)
            throws JSONException {
        List<Integer> out = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            throw new JSONException("empty json");
        }
        JSONObject root = new JSONObject(json);
        if (root.has("error")) {
            throw new JSONException(root.optString("error", "unknown error"));
        }
        JSONArray displays = root.optJSONArray("displays");
        if (displays == null) {
            return out;
        }
        for (int i = 0; i < displays.length(); i++) {
            JSONObject d = displays.getJSONObject(i);
            int displayId = d.optInt("displayId", -1);
            if (displayId < 0) {
                continue;
            }
            if (excludeDefaultDisplay && displayId == Display.DEFAULT_DISPLAY) {
                continue;
            }
            out.add(displayId);
        }
        Collections.sort(out);
        return out;
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position).viewType();
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_aohp_snapshot_header, parent, false);
            return new HeaderVH(v);
        }
        View v = inflater.inflate(R.layout.item_aohp_snapshot_task, parent, false);
        return new TaskVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = mRows.get(position);
        if (holder instanceof HeaderVH) {
            HeaderRow h = (HeaderRow) row;
            ((HeaderVH) holder).title.setText(h.title);
            ((HeaderVH) holder).sub.setText(h.subtitle);
        } else if (holder instanceof TaskVH) {
            TaskRow tr = (TaskRow) row;
            ((TaskVH) holder).primary.setText(tr.primary);
            ((TaskVH) holder).secondary.setText(tr.secondary);
        }
    }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView sub;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_snapshot_header_title);
            sub = itemView.findViewById(R.id.tv_snapshot_header_sub);
        }
    }

    static final class TaskVH extends RecyclerView.ViewHolder {
        final TextView primary;
        final TextView secondary;

        TaskVH(@NonNull View itemView) {
            super(itemView);
            primary = itemView.findViewById(R.id.tv_snapshot_task_primary);
            secondary = itemView.findViewById(R.id.tv_snapshot_task_secondary);
        }
    }
}
