package org.aohp.agentdriver.executor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Bit flags for {@link AohpVdClient#dumpUiTree(int, int)} — semantics match
 * {@code AohpUiTreeDumper} in system_server.
 */
public final class EnhancedTreeBuilder {

    public static final int FLAG_FILTER_DECORATIVE = 0x1;
    /** Maps to server {@code INCLUDE_OFFSCREEN_MARKS}. */
    public static final int FLAG_INCLUDE_OFFSCREEN = 0x2;
    public static final int FLAG_MARK_VISUAL = 0x4;
    /** Only APPLICATION windows (was {@code FLAG_EXCLUDE_SYSTEM_WINDOWS}). */
    public static final int FLAG_APPLICATION_ONLY = 0x8;

    private EnhancedTreeBuilder() {
    }

    /**
     * @deprecated UI trees are built in system_server via {@link AohpVdClient#dumpUiTree}.
     */
    @Deprecated
    public static JSONObject enhance(JSONArray rawNodes, int displayId, int flags)
            throws JSONException {
        throw new UnsupportedOperationException(
                "Use AohpVdClient.dumpUiTree — tree is built in system_server");
    }
}
