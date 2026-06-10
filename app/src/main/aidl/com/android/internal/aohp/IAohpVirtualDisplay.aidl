package com.android.internal.aohp;

interface IAohpVirtualDisplay {
    void registerSession(int displayId, int ownerUid, String ownerPackage);
    void unregisterSession();
    void setFocusPackage(String packageName);
    boolean startLauncherOnDisplay(int displayId, String packageName);
    boolean injectTap(int displayId, int x, int y);
    /** Like {@link #injectTap} but applies tap sensitivity policy ({@code targetResourceId} may be empty). */
    boolean injectTapWithTarget(int displayId, int x, int y, String targetResourceId);
    boolean injectSwipe(int displayId, int x1, int y1, int x2, int y2, int durationMs);
    boolean injectText(int displayId, String text);
    /** Like {@link #injectText} but passes {@code targetResourceId} for sensitivity / consent policy. */
    boolean injectTextWithTarget(int displayId, String targetResourceId, String text);
    boolean injectKeyEvent(int displayId, int keyCode);
    void applyMultiDisplayDeveloperSettings();
    String getDisplayRuntimeSnapshotJson(in int[] extraDisplayIds);

    int createVirtualDisplay(String name, int width, int height, int densityDpi, int flags);
    boolean destroyVirtualDisplay(int displayId);

    String dumpUiTree(int displayId, int flags);
    String setNodeProgress(int displayId, int nodeId, float percent, int flags);
    // nodeId > 0: ui.tree id; nodeId <= 0: focused editable
    String clearEditableText(int displayId, int nodeId, int flags);
    String setEditableText(int displayId, int nodeId, String text, int flags);
}
