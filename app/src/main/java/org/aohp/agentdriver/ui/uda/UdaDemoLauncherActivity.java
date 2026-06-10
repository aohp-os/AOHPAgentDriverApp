package org.aohp.agentdriver.ui.uda;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.uda.UdaDemoCatalog;
import org.aohp.agentdriver.uda.UdaDemoSeeder;
import org.aohp.agentdriver.uda.UdaInstallManager;
import org.aohp.agentdriver.uda.UdaManager;
import org.aohp.agentdriver.uda.UdaPaths;

/** Launcher entry (or activity-alias target) that opens {@link UdaAppActivity}. */
public final class UdaDemoLauncherActivity extends AppCompatActivity {
    public static final String META_JOB_ID = "org.aohp.agentdriver.UDA_DEMO_JOB_ID";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String jobId = resolveJobId();
        if (jobId.isEmpty()) {
            finish();
            return;
        }
        UdaManager manager = UdaManager.getInstance(this);
        UdaDemoSeeder.ensureDemoReady(this, manager, jobId);
        if (!UdaPaths.hasAppIndex(this, jobId)) {
            UdaDemoCatalog.Entry demo = UdaDemoCatalog.find(jobId);
            String label = demo != null ? demo.appName : jobId;
            Toast.makeText(
                            this, getString(R.string.uda_demo_not_ready, label), Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }
        startActivity(UdaInstallManager.buildLaunchIntent(jobId));
        finish();
    }

    @NonNull
    private String resolveJobId() {
        try {
            ActivityInfo info =
                    getPackageManager()
                            .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            if (info.metaData != null) {
                String jobId = info.metaData.getString(META_JOB_ID);
                if (jobId != null && !jobId.isEmpty()) {
                    return jobId;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // fall through
        }
        return "";
    }
}
