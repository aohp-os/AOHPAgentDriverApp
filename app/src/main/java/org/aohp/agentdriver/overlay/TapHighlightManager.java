package org.aohp.agentdriver.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import org.json.JSONObject;

/**
 * Ephemeral circular tap indicator for demo recordings and scripted walkthroughs.
 * Drawn as a click-through overlay window ({@link WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE}).
 */
public final class TapHighlightManager {
    private static final String TAG = "TapHighlight";
    public static final String ACTION_SHOW = "org.aohp.agentdriver.action.TAP_HIGHLIGHT_SHOW";
    public static final String ACTION_HIDE = "org.aohp.agentdriver.action.TAP_HIGHLIGHT_HIDE";

    private static final int DEFAULT_RADIUS_DP = 16;
    private static final int DEFAULT_DURATION_MS = 500;
    private static final long FADE_IN_MS = 180L;
    private static final long FADE_OUT_MS = 160L;

    private static TapHighlightManager sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private TapHighlightView mHighlightView;
    private WindowManager.LayoutParams mParams;
    private ValueAnimator mAnimator;
    private Runnable mAutoHideRunnable;

    private TapHighlightManager(Context context) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized TapHighlightManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TapHighlightManager(context);
        }
        return sInstance;
    }

    public boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(mContext);
    }

    /** @param radiusDp circle radius in dp; {@code <= 0} uses default */
    public JSONObject show(int x, int y, int radiusDp, int durationMs) {
        int radius = radiusDp > 0 ? radiusDp : DEFAULT_RADIUS_DP;
        int duration = durationMs > 0 ? durationMs : DEFAULT_DURATION_MS;
        mMainHandler.post(() -> internalShow(x, y, radius, duration));
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("x", x);
            o.put("y", y);
            o.put("radius", radius);
            o.put("durationMs", duration);
            o.put("overlayGranted", canDrawOverlays());
        } catch (Exception ignored) {
        }
        return o;
    }

    public JSONObject hide() {
        mMainHandler.post(this::internalHide);
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
        } catch (Exception ignored) {
        }
        return o;
    }

    private void internalShow(int x, int y, int radiusDp, int durationMs) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; tap highlight skipped");
            return;
        }
        cancelAutoHide();
        cancelAnimator();
        removeViewImmediate();

        int radiusPx = dpToPx(radiusDp);
        mHighlightView = new TapHighlightView(mContext);
        mHighlightView.setVisualRadiusPx(radiusPx);
        mHighlightView.setHighlightAlpha(0f);
        mHighlightView.setPulse(0f);
        int insetPx = (int) Math.ceil(mHighlightView.requiredInsetPx());
        int diameter = radiusPx * 2 + insetPx * 2;

        mParams = new WindowManager.LayoutParams(
                diameter,
                diameter,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = x - radiusPx - insetPx;
        mParams.y = y - radiusPx - insetPx;
        mParams.setTitle("AOHP_TAP_HIGHLIGHT");

        try {
            mWindowManager.addView(mHighlightView, mParams);
        } catch (Exception e) {
            Log.e(TAG, "failed to show tap highlight", e);
            removeViewImmediate();
            return;
        }

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(FADE_IN_MS);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        mAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            if (mHighlightView != null) {
                mHighlightView.setPulse(t);
                mHighlightView.setHighlightAlpha(t);
            }
        });
        mAnimator.start();

        mAutoHideRunnable = this::fadeOutAndRemove;
        mMainHandler.postDelayed(mAutoHideRunnable, Math.max(durationMs, (int) FADE_IN_MS));
    }

    private void internalHide() {
        cancelAutoHide();
        if (mHighlightView == null) {
            return;
        }
        fadeOutAndRemove();
    }

    private void fadeOutAndRemove() {
        cancelAutoHide();
        if (mHighlightView == null) {
            return;
        }
        cancelAnimator();
        final TapHighlightView view = mHighlightView;
        final float startAlpha = view != null ? 1f : 0f;
        mAnimator = ValueAnimator.ofFloat(startAlpha, 0f);
        mAnimator.setDuration(FADE_OUT_MS);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            if (mHighlightView != null) {
                float alpha = (float) animation.getAnimatedValue();
                mHighlightView.setHighlightAlpha(alpha);
            }
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeViewImmediate();
            }
        });
        mAnimator.start();
    }

    private void cancelAutoHide() {
        if (mAutoHideRunnable != null) {
            mMainHandler.removeCallbacks(mAutoHideRunnable);
            mAutoHideRunnable = null;
        }
    }

    private void cancelAnimator() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    private void removeViewImmediate() {
        cancelAnimator();
        if (mHighlightView != null) {
            try {
                mWindowManager.removeView(mHighlightView);
            } catch (Exception e) {
                Log.w(TAG, "remove tap highlight failed", e);
            }
            mHighlightView = null;
        }
        mParams = null;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, mContext.getResources().getDisplayMetrics()));
    }
}
