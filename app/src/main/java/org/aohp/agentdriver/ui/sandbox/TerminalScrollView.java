package org.aohp.agentdriver.ui.sandbox;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

/**
 * ScrollView nested inside {@link androidx.core.widget.NestedScrollView}: ensures vertical drags
 * scroll this view instead of the parent stealing the gesture.
 *
 * <p>Also suppresses descendant-initiated auto-scroll (e.g. from a selectable {@link
 * android.widget.TextView} whose cursor/selection repositions when its text is reset), which would
 * otherwise cause visible scroll jumps when callers manage scroll position manually.
 */
public class TerminalScrollView extends ScrollView {

    public TerminalScrollView(Context context) {
        super(context);
        init();
    }

    public TerminalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TerminalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScrollbarFadingEnabled(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        disallowParentIntercept();
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        disallowParentIntercept();
        return super.onTouchEvent(ev);
    }

    /**
     * When a selectable TextView's content is replaced via {@code setText}, its internal
     * cursor/selection change triggers this method on the enclosing ScrollView, causing it to jump
     * to reveal the cursor. We explicitly manage scroll position for terminal/log output, so
     * suppress the auto-scroll entirely.
     */
    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        return false;
    }

    /** Prevent the system from restoring focus to a descendant (and scrolling to it) on layout. */
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    private void disallowParentIntercept() {
        ViewParent p = getParent();
        if (p != null) {
            p.requestDisallowInterceptTouchEvent(true);
        }
    }
}
