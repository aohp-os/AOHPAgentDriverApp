package org.aohp.agentdriver.uda;

/** Shared intent / URI constants for UDA launcher runtime. */
public final class UdaConstants {
    public static final String EXTRA_JOB_ID = "jobId";
    public static final String SCHEME = "aohp-uda";
    public static final String HOST_APP = "app";
    /** HTTPS virtual host for {@link androidx.webkit.WebViewAssetLoader}. */
    public static final String ASSET_HOST = "app.uda.local";
    public static final String ASSET_BASE = "https://" + ASSET_HOST + "/";
    /** Legacy preview static server (preview mode only). */
    public static final int LEGACY_STATIC_PORT = 8790;
    public static final int LEGACY_MOCK_PORT = 8787;
    /** Public registry for Launcher / system integrations (P3). */
    public static final String PUBLIC_REGISTRY_PATH =
            UdaPaths.HOST_SHARED_ROOT + "/registry.json";

    private UdaConstants() {}
}
