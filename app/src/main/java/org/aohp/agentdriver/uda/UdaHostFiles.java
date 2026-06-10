package org.aohp.agentdriver.uda;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Host / app-private file helpers for UDA job output trees. */
public final class UdaHostFiles {
    private static final String TAG = "UdaHostFiles";

    private UdaHostFiles() {}

    /** True when the app process can open {@code app/index.html} for this job. */
    public static boolean isJobReadable(@NonNull Context context, @NonNull String jobId) {
        File priv = new File(UdaPaths.privateJobsRoot(context), jobId);
        if (isReadableAppIndex(priv)) {
            return true;
        }
        File host = new File(UdaPaths.HOST_SHARED_ROOT, jobId);
        return isReadableAppIndex(host);
    }

    static boolean isReadableAppIndex(@NonNull File jobRoot) {
        File index = new File(jobRoot, "app/index.html");
        return index.isFile() && index.canRead();
    }

    /**
     * Ensures generated job output is readable by the app UID. UDAGen writes under
     * {@link UdaPaths#HOST_SHARED_ROOT} (root-only); copy into app-private storage when needed.
     *
     * <p>Re-syncs app-private copies when the shared host tree is newer (e.g. udagen refinement
     * rounds after the first {@code app/index.html} was materialized).
     */
    public static boolean ensureJobReadable(
            @NonNull Context context, @NonNull UdaContainerFs fs, @NonNull String jobId) {
        File priv = new File(UdaPaths.privateJobsRoot(context), jobId);
        String containerRoot = UdaPaths.CONTAINER_WORKSPACE + "/" + jobId;
        boolean containerReady = fs.isFile(containerRoot + "/app/index.html");
        if (isReadableAppIndex(priv) && containerReady) {
            long containerMtime = fs.newestMtime(containerRoot);
            long privMtime = newestMtime(priv);
            if (containerMtime > privMtime) {
                Log.i(
                        TAG,
                        "refreshing stale private copy for "
                                + jobId
                                + " (container="
                                + containerMtime
                                + " priv="
                                + privMtime
                                + ")");
                return materializeJobFromContainer(context, fs, jobId);
            }
            return true;
        }
        if (isReadableAppIndex(priv)) {
            return true;
        }
        if (isReadableAppIndex(jobRootOnHost(jobId))) {
            return materializeJobFromHost(context, jobId);
        }
        if (containerReady) {
            return materializeJobFromContainer(context, fs, jobId);
        }
        return false;
    }

    /** Copy the bind-mounted host job tree into app-private storage. */
    public static boolean materializeJobFromHost(
            @NonNull Context context, @NonNull String jobId) {
        File srcRoot = jobRootOnHost(jobId);
        if (!isReadableAppIndex(srcRoot)) {
            Log.w(TAG, "materializeFromHost: missing app/index.html for " + jobId);
            return false;
        }
        File destRoot = new File(UdaPaths.privateJobsRoot(context), jobId);
        try {
            if (destRoot.exists()) {
                deleteRecursive(destRoot);
            }
            copyLocalTree(new File(srcRoot, "app"), new File(destRoot, "app"));
            boolean ok = isReadableAppIndex(destRoot);
            if (ok) {
                Log.i(TAG, "materialized UDA job " + jobId + " from host shared storage");
            }
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "materializeFromHost failed for " + jobId, e);
            return false;
        }
    }

    public static boolean materializeJobFromContainer(
            @NonNull Context context, @NonNull UdaContainerFs fs, @NonNull String jobId) {
        String containerRoot = UdaPaths.CONTAINER_WORKSPACE + "/" + jobId;
        if (!fs.isFile(containerRoot + "/app/index.html")) {
            Log.w(TAG, "materialize: container output missing for " + jobId);
            return false;
        }
        File destRoot = new File(UdaPaths.privateJobsRoot(context), jobId);
        try {
            if (destRoot.exists()) {
                deleteRecursive(destRoot);
            }
            // eslint-disable-next-line ResultOfMethodCallIgnored
            destRoot.mkdirs();
            List<String> relPaths = launchBundlePaths(fs.listRelativeFiles(containerRoot));
            if (relPaths.isEmpty()) {
                Log.w(TAG, "materialize: no app bundle files listed for " + jobId);
                return false;
            }
            for (String rel : relPaths) {
                byte[] data = fs.readFileBytes(containerRoot + "/" + rel);
                if (data == null) {
                    Log.w(TAG, "materialize: failed to read " + jobId + "/" + rel);
                    return false;
                }
                File dest = new File(destRoot, rel);
                File parent = dest.getParentFile();
                if (parent != null) {
                    // eslint-disable-next-line ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    out.write(data);
                }
            }
            boolean ok = isReadableAppIndex(destRoot);
            if (ok) {
                Log.i(TAG, "materialized UDA job " + jobId + " to app-private storage");
            }
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "materialize failed for " + jobId, e);
            return false;
        }
    }

    public static boolean hasAppIndexOnHost(@NonNull String jobId) {
        return new File(UdaPaths.HOST_SHARED_ROOT, jobId + "/app/index.html").isFile();
    }

    @NonNull
    public static File jobRootOnHost(@NonNull String jobId) {
        return new File(UdaPaths.HOST_SHARED_ROOT, jobId);
    }

    /**
     * Recursively copy {@code assets/uda_demos/<assetDir>/} into host or app-private storage.
     */
    public static boolean copyDemoAssets(
            @NonNull Context context, @NonNull String assetDir, @NonNull String jobId) {
        if (UdaPaths.hasAppIndex(context, jobId)) {
            return true;
        }
        File hostRoot = jobRootOnHost(jobId);
        if (copyDemoAssetsTo(context, assetDir, hostRoot)
                && UdaPaths.hasAppIndex(context, jobId)) {
            return true;
        }
        File privRoot = new File(UdaPaths.privateJobsRoot(context), jobId);
        return copyDemoAssetsTo(context, assetDir, privRoot)
                && UdaPaths.hasAppIndex(context, jobId);
    }

    private static boolean copyDemoAssetsTo(
            @NonNull Context context, @NonNull String assetDir, @NonNull File destRoot) {
        try {
            if (destRoot.exists()) {
                deleteRecursive(destRoot);
            }
            // eslint-disable-next-line ResultOfMethodCallIgnored
            destRoot.mkdirs();
            String base = "uda_demos/" + assetDir;
            copyAssetTree(context.getAssets(), base, destRoot);
            writeExitCode(destRoot, "0");
            return new File(destRoot, "app/index.html").isFile();
        } catch (Exception e) {
            Log.e(TAG, "copyDemoAssetsTo failed dest=" + destRoot, e);
            return false;
        }
    }

    private static void writeExitCode(@NonNull File destRoot, @NonNull String code)
            throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(destRoot, "exit.code"))) {
            out.write(code.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyAssetTree(
            @NonNull AssetManager assets, @NonNull String assetPath, @NonNull File destDir)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, destDir);
            return;
        }
        // eslint-disable-next-line ResultOfMethodCallIgnored
        destDir.mkdirs();
        for (String child : children) {
            String childAsset = assetPath + "/" + child;
            File childDest = new File(destDir, child);
            String[] grand = assets.list(childAsset);
            if (grand == null || grand.length == 0) {
                copyAssetFile(assets, childAsset, childDest);
            } else {
                copyAssetTree(assets, childAsset, childDest);
            }
        }
    }

    private static void copyAssetFile(
            @NonNull AssetManager assets, @NonNull String assetPath, @NonNull File destFile)
            throws IOException {
        File parent = destFile.getParentFile();
        if (parent != null) {
            // eslint-disable-next-line ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (InputStream in = assets.open(assetPath);
                OutputStream out = new FileOutputStream(destFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) {
                    out.write(buf, 0, n);
                }
            }
        }
    }

    private static void copyLocalTree(@NonNull File src, @NonNull File dest) throws IOException {
        if (src.isFile()) {
            File parent = dest.getParentFile();
            if (parent != null) {
                // eslint-disable-next-line ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (InputStream in = new java.io.FileInputStream(src);
                    OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) {
                        out.write(buf, 0, n);
                    }
                }
            }
            return;
        }
        if (!src.isDirectory()) {
            return;
        }
        // eslint-disable-next-line ResultOfMethodCallIgnored
        dest.mkdirs();
        File[] kids = src.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            copyLocalTree(kid, new File(dest, kid.getName()));
        }
    }

    private static long newestMtime(@NonNull File root) {
        long newest = root.lastModified();
        File[] kids = root.listFiles();
        if (kids == null) {
            return newest;
        }
        for (File kid : kids) {
            newest = Math.max(newest, kid.isDirectory() ? newestMtime(kid) : kid.lastModified());
        }
        return newest;
    }

    /**
     * Paths required to install/launch a UDA web bundle. Skips UDAGen pipeline artifacts
     * (chat logs, PRD, design specs) that can exceed container exec/base64 transport limits.
     */
    @NonNull
    private static List<String> launchBundlePaths(@NonNull List<String> relPaths) {
        List<String> out = new ArrayList<>();
        for (String rel : relPaths) {
            if (rel.startsWith("app/")) {
                out.add(rel);
            }
        }
        return out;
    }

    private static void deleteRecursive(@NonNull File file) {
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) {
                for (File kid : kids) {
                    deleteRecursive(kid);
                }
            }
        }
        // eslint-disable-next-line ResultOfMethodCallIgnored
        file.delete();
    }
}
