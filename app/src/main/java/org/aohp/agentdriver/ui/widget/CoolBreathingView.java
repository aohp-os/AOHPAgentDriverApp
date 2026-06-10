package org.aohp.agentdriver.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Full-screen border breathing-light effect (ported from ai-phone branch). */
public class CoolBreathingView extends View {

    private Paint borderPaint;
    private Paint glowPaint;
    private Paint particlePaint;

    private int themeColor = 0xFF2196F3;
    private float strokeWidth = 10f;

    private ValueAnimator animator;
    private float animationProgress = 0f;

    private Matrix gradientMatrix;
    private RectF rectF;
    private Path borderPath;

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long lastParticleTime = 0;
    private int bottomInset = 0;

    public CoolBreathingView(Context context) {
        this(context, null);
    }

    public CoolBreathingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CoolBreathingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(40f);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        gradientMatrix = new Matrix();
        rectF = new RectF();
        borderPath = new Path();
    }

    public void setThemeColor(int color) {
        this.themeColor = color;
        updatePaint();
        invalidate();
    }

    public void setBottomInset(int inset) {
        this.bottomInset = inset;
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            rectF.set(strokeWidth, strokeWidth, w - strokeWidth, h - strokeWidth - bottomInset);
            borderPath.reset();
            float radius = 40f;
            borderPath.addRoundRect(rectF, radius, radius, Path.Direction.CW);
        }
        invalidate();
    }

    private void updatePaint() {
        borderPaint.setStrokeWidth(strokeWidth);
        particlePaint.setColor(themeColor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rectF.set(strokeWidth, strokeWidth, w - strokeWidth, h - strokeWidth - bottomInset);
        borderPath.reset();
        float radius = 40f;
        borderPath.addRoundRect(rectF, radius, radius, Path.Direction.CW);

        SweepGradient shader = new SweepGradient(w / 2f, h / 2f,
                new int[]{Color.TRANSPARENT, themeColor, Color.WHITE, themeColor, Color.TRANSPARENT},
                null);
        borderPaint.setShader(shader);
        glowPaint.setShader(shader);
        glowPaint.setAlpha(100);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (animator == null || !animator.isRunning()) {
            return;
        }

        gradientMatrix.setRotate(animationProgress * 360, getWidth() / 2f, getHeight() / 2f);
        borderPaint.getShader().setLocalMatrix(gradientMatrix);
        glowPaint.getShader().setLocalMatrix(gradientMatrix);

        float pulse = (float) (Math.sin(animationProgress * Math.PI * 4) + 1) / 2f;
        glowPaint.setStrokeWidth(20f + 30f * pulse);
        glowPaint.setAlpha((int) (50 + 100 * pulse));

        canvas.drawPath(borderPath, glowPaint);
        canvas.drawPath(borderPath, borderPaint);

        updateParticles();
        for (Particle p : particles) {
            particlePaint.setAlpha((int) (255 * p.alpha));
            canvas.drawCircle(p.x, p.y, p.radius, particlePaint);
        }
        invalidate();
    }

    private void updateParticles() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastParticleTime > 50) {
            generateParticle();
            lastParticleTime = currentTime;
        }
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.life <= 0) {
                it.remove();
            }
        }
    }

    private void generateParticle() {
        float perimeter = 2 * (rectF.width() + rectF.height());
        float pos = random.nextFloat() * perimeter;
        float x;
        float y;
        if (pos < rectF.width()) {
            x = rectF.left + pos;
            y = rectF.top;
        } else if (pos < rectF.width() + rectF.height()) {
            x = rectF.right;
            y = rectF.top + (pos - rectF.width());
        } else if (pos < 2 * rectF.width() + rectF.height()) {
            x = rectF.right - (pos - (rectF.width() + rectF.height()));
            y = rectF.bottom;
        } else {
            x = rectF.left;
            y = rectF.bottom - (pos - (2 * rectF.width() + rectF.height()));
        }
        particles.add(new Particle(x, y));
    }

    public void startAnimation() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation ->
                animationProgress = (float) animation.getAnimatedValue());
        animator.start();
        invalidate();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        particles.clear();
        invalidate();
    }

    private static class Particle {
        float x;
        float y;
        float radius;
        float alpha;
        float life;
        float speedX;
        float speedY;

        Particle(float x, float y) {
            this.x = x;
            this.y = y;
            this.radius = 2f + (float) Math.random() * 4f;
            this.alpha = 1.0f;
            this.life = 1.0f;
            this.speedX = (float) (Math.random() - 0.5) * 2f;
            this.speedY = (float) (Math.random() - 0.5) * 2f;
        }

        void update() {
            x += speedX;
            y += speedY;
            life -= 0.02f;
            alpha = life;
        }
    }
}
