package org.aohp.agentdriver.uda;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.aohp.agentdriver.executor.AohpContainerClient;
import org.aohp.agentdriver.executor.ShellExecutor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** File operations inside the UDA container (app cannot read /data/aohp directly). */
public final class UdaContainerFs {
    private final AohpContainerClient mContainer;

    public UdaContainerFs(AohpContainerClient container) {
        mContainer = container;
    }

    public boolean isFile(String containerPath) {
        return execOk("test -f " + UdaShellUtils.shellQuote(containerPath));
    }

    public boolean isDirectory(String containerPath) {
        return execOk("test -d " + UdaShellUtils.shellQuote(containerPath));
    }

    public void ensureDirectory(String containerPath) {
        exec("mkdir -p " + UdaShellUtils.shellQuote(containerPath), 5000);
    }

    /** Write UTF-8 text to a container path (creates parent dirs). */
    public boolean writeTextFile(@NonNull String containerPath, @NonNull String content) {
        String parent = containerPath;
        int slash = containerPath.lastIndexOf('/');
        if (slash > 0) {
            parent = containerPath.substring(0, slash);
            ensureDirectory(parent);
        }
        String b64 = Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String cmd =
                "printf %s "
                        + UdaShellUtils.shellQuote(b64)
                        + " | base64 -d > "
                        + UdaShellUtils.shellQuote(containerPath);
        return execOk(cmd);
    }

    public void deleteRecursive(String containerPath) {
        exec("rm -rf " + UdaShellUtils.shellQuote(containerPath), 30000);
    }

    public String readTextTail(String containerPath, int maxChars) {
        ShellExecutor.CommandResult r =
                exec(
                        "if test -f "
                                + UdaShellUtils.shellQuote(containerPath)
                                + "; then tail -c "
                                + maxChars
                                + " "
                                + UdaShellUtils.shellQuote(containerPath)
                                + "; fi",
                        10000);
        return r.output != null ? r.output.trim() : "";
    }

    public String readSmallText(String containerPath) {
        return readTextTail(containerPath, 64);
    }

    /** Runs a shell command in the UDA container and returns stdout. */
    @androidx.annotation.NonNull
    public UdaAppCompat.ShellResult execScript(@NonNull String command, int timeoutMs) {
        ShellExecutor.CommandResult r = exec(command, timeoutMs);
        UdaAppCompat.ShellResult out = new UdaAppCompat.ShellResult();
        out.success = r.success && r.exitCode == 0;
        out.output = r.output != null ? r.output.trim() : "";
        out.error = r.error;
        return out;
    }

    private boolean execOk(String command) {
        ShellExecutor.CommandResult r = exec(command, 5000);
        return r.success && r.exitCode == 0;
    }

    /** Relative paths under {@code containerRoot} for every file in the job tree. */
    @NonNull
    public List<String> listRelativeFiles(@NonNull String containerRoot) {
        List<String> paths = new ArrayList<>();
        String quoted = UdaShellUtils.shellQuote(containerRoot);
        ShellExecutor.CommandResult r = exec("find " + quoted + " -type f", 60000);
        if (!r.success || r.output == null || r.output.isEmpty()) {
            return paths;
        }
        String prefix = containerRoot.endsWith("/") ? containerRoot : containerRoot + "/";
        for (String line : r.output.split("\n")) {
            String path = line.trim();
            if (path.isEmpty()) {
                continue;
            }
            if (path.startsWith(prefix)) {
                paths.add(path.substring(prefix.length()));
            }
        }
        return paths;
    }

    /** Newest modification time (epoch ms) under a container directory tree. */
    public long newestMtime(@NonNull String containerRoot) {
        String quoted = UdaShellUtils.shellQuote(containerRoot);
        ShellExecutor.CommandResult r =
                exec(
                        "if test -d "
                                + quoted
                                + "; then find "
                                + quoted
                                + " -type f -exec stat -c %Y {} + 2>/dev/null | sort -n | tail -1; fi",
                        30000);
        if (!r.success || r.output == null || r.output.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(r.output.trim()) * 1000L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Read a container file as raw bytes (base64 transport over exec). */
    @Nullable
    public byte[] readFileBytes(@NonNull String containerPath) {
        String cmd = "base64 " + UdaShellUtils.shellQuote(containerPath);
        ShellExecutor.CommandResult r = exec(cmd, 120000);
        if (!r.success || r.output == null || r.output.isEmpty()) {
            return null;
        }
        String b64 = r.output.replaceAll("\\s+", "");
        try {
            return Base64.decode(b64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ShellExecutor.CommandResult exec(String command, int timeoutMs) {
        if (!mContainer.isServiceAvailable()) {
            ShellExecutor.CommandResult r = new ShellExecutor.CommandResult();
            r.success = false;
            r.exitCode = -1;
            r.error = "container service unavailable";
            return r;
        }
        return mContainer.execSync(UdaPaths.CONTAINER_NAME, command, timeoutMs);
    }
}
