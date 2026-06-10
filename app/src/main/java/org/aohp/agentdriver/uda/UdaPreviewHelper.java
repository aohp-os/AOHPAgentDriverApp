package org.aohp.agentdriver.uda;

import android.util.Log;

import org.aohp.agentdriver.executor.AohpContainerClient;
import org.aohp.agentdriver.executor.AohpServiceInfo;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/** Starts/stops mock + static HTTP servers inside the UDA container for WebView preview. */
public final class UdaPreviewHelper {
    private static final String TAG = "UdaPreviewHelper";
    private static final Object PREVIEW_LOCK = new Object();
    private static volatile String sActiveJobId;

    private final AohpContainerClient mContainer;
    private final UdaContainerFs mFs;

    public UdaPreviewHelper(AohpContainerClient container) {
        mContainer = container;
        mFs = new UdaContainerFs(container);
    }

    public boolean hasMockServer(@androidx.annotation.NonNull UdaJobInfo job) {
        return mFs.isFile(job.containerOutputDir() + "/mock/server.py");
    }

    /** Start only the mock API server for {@code job} (used by asset-loader runtime). */
    public boolean startMockOnly(@androidx.annotation.NonNull UdaJobInfo job) {
        synchronized (PREVIEW_LOCK) {
            if (!mContainer.isServiceAvailable()) {
                return false;
            }
            String output = job.containerOutputDir();
            if (!mFs.isFile(output + "/mock/server.py")) {
                return true;
            }
            stopOtherMockServices(job.jobId);
            String mockCmd =
                    "cd "
                            + UdaShellUtils.shellQuote(output)
                            + " && python3 mock/server.py";
            long mockPid =
                    mContainer.startService(
                            UdaPaths.CONTAINER_NAME, serviceIdMock(job.jobId), mockCmd);
            Log.d(TAG, "mock-only pid=" + mockPid + " job=" + job.jobId);
            if (mockPid <= 0) {
                return false;
            }
            return waitForPort(UdaPaths.MOCK_PORT, 8000);
        }
    }

    private void stopOtherMockServices(@androidx.annotation.NonNull String exceptJobId) {
        if (!mContainer.isServiceAvailable()) {
            return;
        }
        List<AohpServiceInfo> services =
                AohpServiceInfo.listFromJson(mContainer.listServicesJson(UdaPaths.CONTAINER_NAME));
        String keep = serviceIdMock(exceptJobId);
        for (AohpServiceInfo svc : services) {
            if (svc.serviceId.startsWith("uda-mock-") && !keep.equals(svc.serviceId)) {
                mContainer.stopService(UdaPaths.CONTAINER_NAME, svc.serviceId);
            }
        }
    }

    /** Stops preview services for one job. */
    public void stopPreviewServices(@androidx.annotation.NonNull String jobId) {
        mContainer.stopService(UdaPaths.CONTAINER_NAME, serviceIdMock(jobId));
        mContainer.stopService(UdaPaths.CONTAINER_NAME, serviceIdStatic(jobId));
    }

    /** Stops every uda-mock-* / uda-static-* service (frees shared ports 8787/8790). */
    public void stopAllPreviewServices() {
        if (!mContainer.isServiceAvailable()) {
            return;
        }
        List<AohpServiceInfo> services =
                AohpServiceInfo.listFromJson(mContainer.listServicesJson(UdaPaths.CONTAINER_NAME));
        for (AohpServiceInfo svc : services) {
            if (svc.serviceId.startsWith("uda-mock-") || svc.serviceId.startsWith("uda-static-")) {
                mContainer.stopService(UdaPaths.CONTAINER_NAME, svc.serviceId);
            }
        }
    }

    /**
     * Start mock + static servers for {@code job}. Stops any prior preview first and waits until
     * the static HTTP port accepts connections.
     */
    public boolean startPreviewServices(@androidx.annotation.NonNull UdaJobInfo job) {
        synchronized (PREVIEW_LOCK) {
            stopAllPreviewServices();
            sleepQuiet(500);

            if (!mContainer.isServiceAvailable()) {
                return false;
            }
            String output = job.containerOutputDir();
            if (mFs.isFile(output + "/mock/server.py")) {
                String mockCmd =
                        "cd "
                                + UdaShellUtils.shellQuote(output)
                                + " && python3 mock/server.py";
                long mockPid =
                        mContainer.startService(
                                UdaPaths.CONTAINER_NAME, serviceIdMock(job.jobId), mockCmd);
                Log.d(TAG, "mock server pid=" + mockPid + " job=" + job.jobId);
            }
            if (!mFs.isFile(output + "/app/index.html")) {
                return false;
            }
            UdaAppCompat.patchForPreview(mFs, output);
            String staticCmd =
                    "cd "
                            + UdaShellUtils.shellQuote(output + "/app")
                            + " && python3 -m http.server "
                            + UdaPaths.STATIC_PORT
                            + " --bind 127.0.0.1";
            long staticPid =
                    mContainer.startService(
                            UdaPaths.CONTAINER_NAME, serviceIdStatic(job.jobId), staticCmd);
            Log.d(TAG, "static server pid=" + staticPid + " job=" + job.jobId);
            if (staticPid <= 0) {
                return false;
            }
            if (!waitForPort(UdaPaths.STATIC_PORT, 10000)) {
                Log.e(TAG, "static server not ready on port " + UdaPaths.STATIC_PORT);
                stopPreviewServices(job.jobId);
                return false;
            }
            if (mFs.isFile(output + "/mock/server.py")) {
                waitForPort(UdaPaths.MOCK_PORT, 8000);
            }
            sActiveJobId = job.jobId;
            return true;
        }
    }

    /** Called from {@link org.aohp.agentdriver.ui.uda.UdaPreviewActivity#onDestroy()}. */
    public static void releasePreview(@androidx.annotation.NonNull AohpContainerClient container,
            @androidx.annotation.NonNull String jobId) {
        synchronized (PREVIEW_LOCK) {
            if (!jobId.equals(sActiveJobId)) {
                return;
            }
            new UdaPreviewHelper(container).stopPreviewServices(jobId);
            sActiveJobId = null;
        }
    }

    @androidx.annotation.NonNull
    public static String previewUrl() {
        return "http://127.0.0.1:" + UdaPaths.STATIC_PORT + "/index.html#/";
    }

    /** Probe localhost from Android (container shares network namespace). */
    static boolean waitForPort(int port, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
                return true;
            } catch (Exception ignored) {
                sleepQuiet(250);
            }
        }
        return false;
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String serviceIdMock(String jobId) {
        return "uda-mock-" + sanitize(jobId);
    }

    static String serviceIdStatic(String jobId) {
        return "uda-static-" + sanitize(jobId);
    }

    static String serviceIdGenerate(String jobId) {
        return "udagen-" + sanitize(jobId);
    }

    private static String sanitize(String jobId) {
        return jobId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
