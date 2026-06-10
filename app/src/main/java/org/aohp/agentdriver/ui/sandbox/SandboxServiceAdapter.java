package org.aohp.agentdriver.ui.sandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.executor.AohpServiceInfo;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SandboxServiceAdapter extends RecyclerView.Adapter<SandboxServiceAdapter.VH> {

    public interface Listener {
        void onStop(String serviceId);

        void onLog(String serviceId);
    }

    private final List<AohpServiceInfo> items = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<AohpServiceInfo> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sandbox_service, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AohpServiceInfo e = items.get(position);
        h.tvId.setText(e.serviceId);
        h.tvDetail.setText(
                "pid=" + e.pid + " | up " + e.uptimeSec + "s\n" + e.command);
        h.tvAlive.setText(e.alive ? "●" : "○");
        h.tvAlive.setTextColor(e.alive ? 0xFF4CAF50 : 0xFF9E9E9E);
        h.btnStop.setOnClickListener(
                v -> {
                    if (listener != null) listener.onStop(e.serviceId);
                });
        h.btnLog.setOnClickListener(
                v -> {
                    if (listener != null) listener.onLog(e.serviceId);
                });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvId;
        final TextView tvDetail;
        final TextView tvAlive;
        final MaterialButton btnStop;
        final MaterialButton btnLog;

        VH(@NonNull View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tv_service_id);
            tvDetail = itemView.findViewById(R.id.tv_service_detail);
            tvAlive = itemView.findViewById(R.id.tv_service_alive);
            btnStop = itemView.findViewById(R.id.btn_service_stop);
            btnLog = itemView.findViewById(R.id.btn_service_log);
        }
    }
}
