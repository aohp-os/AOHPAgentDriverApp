package org.aohp.agentdriver.ui.uda;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.uda.UdaInstallInfo;
import org.aohp.agentdriver.uda.UdaInstallStore;
import org.aohp.agentdriver.uda.UdaLauncherSync;
import org.aohp.agentdriver.uda.UdaJobInfo;
import org.aohp.agentdriver.uda.UdaJobStatus;
import org.aohp.agentdriver.uda.UdaPaths;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class UdaJobAdapter extends RecyclerView.Adapter<UdaJobAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull UdaJobInfo job);

        void onPin(@NonNull UdaJobInfo job, boolean installed, boolean pinned);

        void onDelete(@NonNull UdaJobInfo job);
    }

    private final List<UdaJobInfo> mJobs = new ArrayList<>();
    private Listener mListener;
    private UdaInstallStore mInstallStore;

    public void setInstallStore(@NonNull UdaInstallStore installStore) {
        mInstallStore = installStore;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setJobs(@NonNull List<UdaJobInfo> jobs) {
        mJobs.clear();
        mJobs.addAll(jobs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_uda_job, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        UdaJobInfo job = mJobs.get(position);
        h.title.setText(job.appName);
        String when =
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                        .format(new Date(job.createdAtMs));
        android.content.Context ctx = h.itemView.getContext();
        UdaInstallInfo inst = mInstallStore != null ? mInstallStore.get(job.jobId) : null;
        boolean onDesktop = UdaLauncherSync.isVisibleOnLauncher(ctx, job, inst);
        boolean installed =
                job.demo
                        ? UdaPaths.hasAppIndex(ctx, job.jobId)
                        : (inst != null);
        String installTag = "";
        if (installed) {
            installTag =
                    onDesktop
                            ? ctx.getString(R.string.uda_tag_pinned)
                            : ctx.getString(R.string.uda_tag_installed);
        }
        String demoTag = job.demo ? ctx.getString(R.string.uda_tag_demo) : "";
        h.meta.setText(
                job.jobId
                        + demoTag
                        + " · "
                        + job.status.name().toLowerCase(Locale.US)
                        + installTag
                        + " · "
                        + when);
        boolean canOpen = job.status == UdaJobStatus.COMPLETED;
        h.open.setEnabled(canOpen);
        h.open.setAlpha(canOpen ? 1f : 0.4f);
        h.open.setOnClickListener(
                v -> {
                    if (mListener != null) {
                        mListener.onOpen(job);
                    }
                });
        h.pin.setEnabled(canOpen);
        h.pin.setAlpha(canOpen ? 1f : 0.4f);
        h.pin.setText(
                onDesktop
                        ? ctx.getString(R.string.uda_job_remove_desktop)
                        : (installed
                                ? ctx.getString(R.string.uda_job_pin_desktop)
                                : ctx.getString(R.string.uda_job_add_desktop)));
        h.pin.setOnClickListener(
                v -> {
                    if (mListener != null) {
                        mListener.onPin(job, installed, onDesktop);
                    }
                });
        h.delete.setOnClickListener(
                v -> {
                    if (mListener != null) {
                        mListener.onDelete(job);
                    }
                });
    }

    @Override
    public int getItemCount() {
        return mJobs.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final MaterialButton open;
        final MaterialButton pin;
        final MaterialButton delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_job_title);
            meta = itemView.findViewById(R.id.tv_job_meta);
            open = itemView.findViewById(R.id.btn_job_open);
            pin = itemView.findViewById(R.id.btn_job_pin);
            delete = itemView.findViewById(R.id.btn_job_delete);
        }
    }
}
