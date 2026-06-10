package org.aohp.agentdriver.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Minimal circular tap indicator: soft inner fill + crisp outer ring with a short pulse.
 */
public final class TapHighlightView extends View {
    private static final Interpolator PULSE = new DecelerateInterpolator(1.8f);

    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();

    private float mVisualRadiusPx = 0f;
    private float mPulse = 0f;
    private float mAlpha = 0f;

    public TapHighlightView(Context context) {
        super(context);
        init();
    }

    public TapHighlightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(0xFFFF5252);

        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setColor(0xFFE53935);
        mRingPaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
        mRingPaint.setStrokeCap(Paint.Cap.ROUND);
        mRingPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Logical tap radius in px; window padding should match {@link #requiredInsetPx()}. */
    public void setVisualRadiusPx(float radiusPx) {
        mVisualRadiusPx = Math.max(0f, radiusPx);
        invalidate();
    }

    /** Extra space needed around the circle so stroke + anti-alias are not clipped. */
    public float requiredInsetPx() {
        return mRingPaint.getStrokeWidth() * 0.5f + getResources().getDisplayMetrics().density * 1.5f;
    }

    /** @param pulse 0..1 scale-in progress */
    public void setPulse(float pulse) {
        mPulse = Math.max(0f, Math.min(1f, pulse));
        invalidate();
    }

    /** @param alpha 0..1 overall opacity */
    public void setHighlightAlpha(float alpha) {
        mAlpha = Math.max(0f, Math.min(1f, alpha));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mAlpha <= 0f) {
            return;
        }
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float inset = requiredInsetPx();
        float maxRadius = Math.min(cx, cy) - inset;
        if (mVisualRadiusPx > 0f) {
            maxRadius = Math.min(mVisualRadiusPx, maxRadius);
        }
        if (maxRadius <= 0f) {
            return;
        }
        float eased = PULSE.getInterpolation(mPulse);
        float scale = 0.9f + 0.1f * eased;
        float radius = maxRadius * scale;

        mOval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        int fillAlpha = (int) (0x55 * mAlpha);
        int ringAlpha = (int) (0xE8 * mAlpha);
        mFillPaint.setAlpha(fillAlpha);
        mRingPaint.setAlpha(ringAlpha);

        canvas.drawOval(mOval, mFillPaint);
        canvas.drawOval(mOval, mRingPaint);
    }
}
