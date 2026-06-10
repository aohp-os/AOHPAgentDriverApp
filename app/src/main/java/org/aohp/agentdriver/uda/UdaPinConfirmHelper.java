package org.aohp.agentdriver.uda;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.aohp.agentdriver.executor.MyAccessibilityService;

import java.util.Arrays;
import java.util.List;

/**
 * Best-effort auto-confirm for Launcher3 {@code AddItemActivity} after {@code requestPinShortcut}.
 * Used when the system image has not yet picked up the Launcher3 AOHP auto-place patch.
 */
public final class UdaPinConfirmHelper {
    private static final String TAG = "UdaPinConfirmHelper";
    private static final String LAUNCHER_PKG = "com.android.launcher3";
    private static final List<String> CONFIRM_LABELS =
            Arrays.asList("ADD TO HOME SCREEN", "添加到主屏幕", "Add to Home screen");
    private static final int MAX_ATTEMPTS = 20;

    private UdaPinConfirmHelper() {}

    public static void scheduleAutoConfirm() {
        Handler handler = new Handler(Looper.getMainLooper());
        attempt(handler, 0);
    }

    private static void attempt(Handler handler, int tryN) {
        if (tryN > MAX_ATTEMPTS) {
            Log.w(TAG, "pin confirm dialog not found after retries");
            return;
        }
        long delayMs = tryN == 0 ? 200 : 400;
        handler.postDelayed(
                () -> {
                    if (tryConfirmOnce()) {
                        Log.i(TAG, "auto-confirmed pin shortcut dialog");
                        return;
                    }
                    attempt(handler, tryN + 1);
                },
                delayMs);
    }

    private static boolean tryConfirmOnce() {
        MyAccessibilityService svc = MyAccessibilityService.getInstance();
        if (svc == null) {
            return false;
        }
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        try {
            if (!LAUNCHER_PKG.contentEquals(String.valueOf(root.getPackageName()))) {
                return false;
            }
            for (String label : CONFIRM_LABELS) {
                AccessibilityNodeInfo btn = findClickableByText(root, label);
                if (btn != null) {
                    boolean ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    btn.recycle();
                    return ok;
                }
            }
            return false;
        } finally {
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo findClickableByText(
            AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return null;
        }
        CharSequence nodeText = node.getText();
        if (nodeText != null
                && text.equalsIgnoreCase(nodeText.toString().trim())
                && node.isClickable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findClickableByText(child, text);
            if (child != null) {
                child.recycle();
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
