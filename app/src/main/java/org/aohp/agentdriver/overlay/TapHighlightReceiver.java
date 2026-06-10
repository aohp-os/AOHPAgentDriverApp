package org.aohp.agentdriver.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * adb-friendly entry point for demo tap highlights:
 * {@code adb shell am broadcast -a org.aohp.agentdriver.action.TAP_HIGHLIGHT_SHOW --ei x 360 --ei y 640}
 */
public final class TapHighlightReceiver extends BroadcastReceiver {
    private static final String TAG = "TapHighlightReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        TapHighlightManager mgr = TapHighlightManager.getInstance(context);
        switch (intent.getAction()) {
            case TapHighlightManager.ACTION_SHOW:
                int x = intent.getIntExtra("x", -1);
                int y = intent.getIntExtra("y", -1);
                if (x < 0 || y < 0) {
                    Log.w(TAG, "TAP_HIGHLIGHT_SHOW missing x/y extras");
                    return;
                }
                int radius = intent.getIntExtra("radius", 0);
                int durationMs = intent.getIntExtra("duration_ms", 0);
                mgr.show(x, y, radius, durationMs);
                break;
            case TapHighlightManager.ACTION_HIDE:
                mgr.hide();
                break;
            default:
                break;
        }
    }
}
