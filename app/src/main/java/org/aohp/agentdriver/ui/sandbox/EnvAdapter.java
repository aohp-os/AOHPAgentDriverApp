package org.aohp.agentdriver.ui.sandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.aohp.agentdriver.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class EnvAdapter extends RecyclerView.Adapter<EnvAdapter.EnvViewHolder> {

    public interface EnvActionListener {
        void onSelect(String name);
        void onReset(String name);
        void onDelete(String name);
    }

    private final List<String> envNames = new ArrayList<>();
    private String selectedEnv = null;
    private EnvActionListener listener;

    public void setListener(EnvActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<String> names) {
        envNames.clear();
        envNames.addAll(names);
        notifyDataSetChanged();
    }

    public void setSelectedEnv(String name) {
        selectedEnv = name;
        notifyDataSetChanged();
    }

    public String getSelectedEnv() {
        return selectedEnv;
    }

    @NonNull
    @Override
    public EnvViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_env_card, parent, false);
        return new EnvViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EnvViewHolder holder, int position) {
        String name = envNames.get(position);
        boolean isSelected = name.equals(selectedEnv);

        holder.tvName.setText(name);
        holder.tvTemplate.setText("Alpine Linux");

        // Highlight selected card.
        if (isSelected) {
            holder.statusDot.setBackgroundColor(0xFF00C853);
            holder.itemView.setAlpha(1f);
        } else {
            holder.statusDot.setBackgroundColor(0xFF9E9E9E);
            holder.itemView.setAlpha(0.7f);
        }

        holder.btnSelect.setOnClickListener(v -> {
            if (listener != null) listener.onSelect(name);
        });
        holder.btnReset.setOnClickListener(v -> {
            if (listener != null) listener.onReset(name);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(name);
        });
    }

    @Override
    public int getItemCount() {
        return envNames.size();
    }

    static class EnvViewHolder extends RecyclerView.ViewHolder {
        final View statusDot;
        final TextView tvName;
        final TextView tvTemplate;
        final MaterialButton btnSelect;
        final MaterialButton btnReset;
        final MaterialButton btnDelete;

        EnvViewHolder(@NonNull View itemView) {
            super(itemView);
            statusDot = itemView.findViewById(R.id.view_status_dot);
            tvName = itemView.findViewById(R.id.tv_env_name);
            tvTemplate = itemView.findViewById(R.id.tv_env_template);
            btnSelect = itemView.findViewById(R.id.btn_select_env);
            btnReset = itemView.findViewById(R.id.btn_reset_env);
            btnDelete = itemView.findViewById(R.id.btn_delete_env);
        }
    }
}
