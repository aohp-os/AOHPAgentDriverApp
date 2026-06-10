package org.aohp.agentdriver.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.aohp.agentdriver.R;
import org.aohp.agentdriver.ui.widget.CoolBreathingView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Benchmark / agent execution overlay: fixed-height task card + timeline at the lower-middle
 * of the screen; optional full-screen breathing border.
 * All layers are click-through ({@link WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE}).
 */
public final class AgentOverlayManager {
    private static final String TAG = "AgentOverlay";
    /** Window title marker for Framework UI-tree exclusion. */
    public static final String OVERLAY_WINDOW_TITLE = "AOHP_AGENT_OVERLAY";
    /** Full-screen border breathing effect; set true to re-enable. */
    private static final boolean SHOW_BREATHING_BORDER = false;
    private static final long TASK_SECTION_ANIM_IN_MS = 380L;
    private static final long TASK_SECTION_ANIM_OUT_MS = 300L;

    private static AgentOverlayManager sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private View mOverlayView;
    private WindowManager.LayoutParams mOverlayParams;
    private CoolBreathingView mBreathingView;
    private TextView mTvTitle;
    private TextView mTvTask;
    private TextView mTvState;
    private TextView mTvSummary;
    private View mTaskSection;
    private RecyclerView mRvEvents;
    private AgentOverlayEventAdapter mAdapter;

    private boolean mShowing;
    private boolean mTaskSectionVisible;
    private String mRunId = "";
    private final List<AgentOverlayEvent> mEvents = new ArrayList<>();
    private final Set<String> mSeenKeys = new HashSet<>();
    private State mState = State.IDLE;

    public enum State {
        IDLE,
        TASK_SHOWN,
        THINKING,
        TOOL_RUNNING,
        FINISHED,
        ERROR
    }

    private AgentOverlayManager(Context context) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized AgentOverlayManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AgentOverlayManager(context);
        }
        return sInstance;
    }

    public boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(mContext);
    }

    public JSONObject taskStart(String runId, String task, String title) {
        mMainHandler.post(() -> internalTaskStart(runId, task, title));
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("runId", runId);
            o.put("overlayGranted", canDrawOverlays());
        } catch (Exception ignored) {
        }
        return o;
    }

    public JSONObject eventPush(String runId, JSONArray events) {
        mMainHandler.post(() -> internalEventPush(runId, events));
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("accepted", events != null ? events.length() : 0);
        } catch (Exception ignored) {
        }
        return o;
    }

    public JSONObject setState(String runId, String stateName) {
        mMainHandler.post(() -> internalSetState(runId, stateName));
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("state", stateName);
        } catch (Exception ignored) {
        }
        return o;
    }

    public JSONObject taskFinish(String runId, String status, String summary) {
        mMainHandler.post(() -> internalTaskFinish(runId, status, summary));
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
        } catch (Exception ignored) {
        }
        return o;
    }

    public JSONObject hide(String runId) {
        mMainHandler.post(() -> internalHide(runId));
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
        } catch (Exception ignored) {
        }
        return o;
    }

    private void internalTaskStart(String runId, String task, String title) {
        if (!ensureOverlayPermission()) {
            return;
        }
        mRunId = runId != null ? runId : "";
        mEvents.clear();
        mSeenKeys.clear();
        ensureViews();
        if (mTvTitle != null) {
            mTvTitle.setText(title != null && !title.isEmpty() ? title : mContext.getString(R.string.overlay_title));
        }
        if (mTvTask != null) {
            mTvTask.setText(task != null ? task : "");
        }
        if (mTvSummary != null) {
            mTvSummary.setVisibility(View.GONE);
        }
        mAdapter.setEvents(mEvents);
        applyState(State.TASK_SHOWN);
        showLayers();
        showTaskSectionAnimated();
    }

    private void internalEventPush(String runId, JSONArray events) {
        if (events == null || events.length() == 0) {
            return;
        }
        if (!mRunId.isEmpty() && runId != null && !runId.isEmpty() && !mRunId.equals(runId)) {
            return;
        }
        if (!mShowing && !ensureOverlayPermission()) {
            return;
        }
        ensureViews();
        List<AgentOverlayEvent> inserted = new ArrayList<>();
        Set<Integer> changedPositions = new LinkedHashSet<>();
        boolean sawThinking = false;
        for (int i = 0; i < events.length(); i++) {
            JSONObject raw = events.optJSONObject(i);
            if (raw == null) {
                continue;
            }
            AgentOverlayEvent event = parseEvent(raw);
            if (tryMergeThinking(event, changedPositions)) {
                applyState(State.THINKING);
                sawThinking = true;
                continue;
            }
            if (tryMergeToolCall(event, changedPositions)) {
                applyState(State.TOOL_RUNNING);
                continue;
            }
            String key = event.dedupeKey();
            if (!mSeenKeys.add(key)) {
                continue;
            }
            mEvents.add(event);
            inserted.add(event);
            if ("thinking".equals(event.type)) {
                applyState(State.THINKING);
                sawThinking = true;
            } else if ("tool_call".equals(event.type)) {
                applyState(State.TOOL_RUNNING);
            }
        }
        for (int position : changedPositions) {
            mAdapter.notifyEventChanged(position);
        }
        if (!inserted.isEmpty()) {
            mAdapter.appendEvents(inserted);
        }
        if (!changedPositions.isEmpty() || !inserted.isEmpty()) {
            if (sawThinking) {
                hideTaskSectionAnimated();
            }
            scrollEventsToBottom();
        }
        if (!mShowing) {
            showLayers();
        }
    }

    private void internalSetState(String runId, String stateName) {
        if (!mRunId.isEmpty() && runId != null && !runId.isEmpty() && !mRunId.equals(runId)) {
            return;
        }
        State next = parseState(stateName);
        if (next != null) {
            applyState(next);
        }
    }

    private void internalTaskFinish(String runId, String status, String summary) {
        if (!mRunId.isEmpty() && runId != null && !runId.isEmpty() && !mRunId.equals(runId)) {
            return;
        }
        boolean error = status != null
                && (status.equalsIgnoreCase("error") || status.equalsIgnoreCase("failed"));
        applyState(error ? State.ERROR : State.FINISHED);
        hideTaskSectionAnimated();
        if (mTvSummary != null && summary != null && !summary.isEmpty()) {
            mTvSummary.setText(summary);
            mTvSummary.setVisibility(View.VISIBLE);
        }
        mMainHandler.postDelayed(this::internalHide, 4500);
    }

    private void internalHide() {
        internalHide(null);
    }

    private void internalHide(String runId) {
        if (runId != null && !runId.isEmpty() && !mRunId.isEmpty() && !mRunId.equals(runId)) {
            return;
        }
        removeLayers();
        mState = State.IDLE;
        mRunId = "";
        mEvents.clear();
        mSeenKeys.clear();
    }

    private boolean ensureOverlayPermission() {
        if (canDrawOverlays()) {
            return true;
        }
        Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; overlay skipped");
        return false;
    }

    private void ensureViews() {
        if (mOverlayView != null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mOverlayView = inflater.inflate(R.layout.layout_agent_overlay_root, null);

        mBreathingView = mOverlayView.findViewById(R.id.view_border_light);
        if (mBreathingView != null) {
            mBreathingView.setVisibility(SHOW_BREATHING_BORDER ? View.VISIBLE : View.GONE);
        }
        mTvTitle = mOverlayView.findViewById(R.id.tv_overlay_title);
        mTaskSection = mOverlayView.findViewById(R.id.layout_overlay_task_section);
        mTvTask = mOverlayView.findViewById(R.id.tv_overlay_task);
        mTvState = mOverlayView.findViewById(R.id.tv_overlay_state);
        mTvSummary = mOverlayView.findViewById(R.id.tv_overlay_summary);
        mRvEvents = mOverlayView.findViewById(R.id.rv_overlay_events);
        mAdapter = new AgentOverlayEventAdapter();
        mRvEvents.setLayoutManager(new LinearLayoutManager(mContext));
        mRvEvents.setAdapter(mAdapter);
        disableTouches(mOverlayView);

        mOverlayParams = buildOverlayParams();
    }

    private WindowManager.LayoutParams buildOverlayParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params.format = PixelFormat.TRANSLUCENT;
        applyPassthroughFlags(params);
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.setTitle(OVERLAY_WINDOW_TITLE);
        return params;
    }

    private static void applyPassthroughFlags(WindowManager.LayoutParams params) {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        applyEmptyTouchableRegion(params);
    }

    private static void applyEmptyTouchableRegion(WindowManager.LayoutParams params) {
        if (Build.VERSION.SDK_INT < 34) {
            return;
        }
        try {
            params.getClass()
                    .getMethod("setTouchableRegion", android.graphics.Region.class)
                    .invoke(params, new android.graphics.Region());
        } catch (ReflectiveOperationException ignored) {
            // Best-effort: FLAG_NOT_TOUCHABLE remains the primary passthrough mechanism.
        }
    }

    private static void disableTouches(View view) {
        if (view == null) {
            return;
        }
        view.setClickable(false);
        view.setLongClickable(false);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        view.setOnTouchListener((v, event) -> false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableTouches(group.getChildAt(i));
            }
        }
    }

    private void showLayers() {
        ensureViews();
        if (mShowing) {
            return;
        }
        try {
            mWindowManager.addView(mOverlayView, mOverlayParams);
            mWindowManager.updateViewLayout(mOverlayView, mOverlayParams);
            mShowing = true;
            if (SHOW_BREATHING_BORDER && mBreathingView != null) {
                mBreathingView.startAnimation();
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to show overlay", e);
            mShowing = false;
        }
    }

    private void removeLayers() {
        if (!mShowing) {
            return;
        }
        if (SHOW_BREATHING_BORDER && mBreathingView != null) {
            mBreathingView.stopAnimation();
        }
        try {
            if (mOverlayView != null) {
                mWindowManager.removeView(mOverlayView);
            }
        } catch (Exception ignored) {
        }
        mShowing = false;
    }

    private void showTaskSectionAnimated() {
        if (mTaskSection == null) {
            return;
        }
        setTaskSectionLayoutWeights(true);
        mTaskSection.animate().cancel();
        mTaskSectionVisible = true;
        mTaskSection.setVisibility(View.VISIBLE);
        mTaskSection.setAlpha(0f);
        mTaskSection.setTranslationY(-mContext.getResources().getDisplayMetrics().density * 20f);
        mTaskSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(TASK_SECTION_ANIM_IN_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void hideTaskSectionAnimated() {
        if (mTaskSection == null || !mTaskSectionVisible) {
            return;
        }
        mTaskSectionVisible = false;
        setTaskSectionLayoutWeights(false);
        mTaskSection.animate().cancel();
        mTaskSection.animate()
                .alpha(0f)
                .translationY(-mContext.getResources().getDisplayMetrics().density * 12f)
                .setDuration(TASK_SECTION_ANIM_OUT_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (mTaskSection != null && !mTaskSectionVisible) {
                        mTaskSection.setVisibility(View.GONE);
                        mTaskSection.setAlpha(1f);
                        mTaskSection.setTranslationY(0f);
                    }
                })
                .start();
    }

    /** Task visible: give description almost the full panel; hidden: timeline expands. */
    private void setTaskSectionLayoutWeights(boolean taskDominant) {
        if (mTaskSection == null || mRvEvents == null) {
            return;
        }
        LinearLayout.LayoutParams taskLp =
                (LinearLayout.LayoutParams) mTaskSection.getLayoutParams();
        LinearLayout.LayoutParams eventsLp =
                (LinearLayout.LayoutParams) mRvEvents.getLayoutParams();
        if (taskDominant) {
            taskLp.height = 0;
            taskLp.weight = 1f;
            eventsLp.height = 0;
            eventsLp.weight = 0f;
        } else {
            taskLp.height = 0;
            taskLp.weight = 0f;
            eventsLp.height = 0;
            eventsLp.weight = 1f;
        }
        mTaskSection.setLayoutParams(taskLp);
        mRvEvents.setLayoutParams(eventsLp);
        if (mTaskSection.getParent() instanceof View) {
            ((View) mTaskSection.getParent()).requestLayout();
        }
    }

    private void applyState(State state) {
        mState = state;
        if (mTvState != null) {
            mTvState.setText(stateLabel(state));
        }
        if (SHOW_BREATHING_BORDER && mBreathingView != null) {
            mBreathingView.setThemeColor(stateColor(state));
            if (mShowing && (mBreathingView.getTag() == null)) {
                mBreathingView.startAnimation();
            }
        }
    }

    private static State parseState(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return State.valueOf(name.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String stateLabel(State state) {
        switch (state) {
            case TASK_SHOWN:
                return mContext.getString(R.string.overlay_state_task);
            case THINKING:
                return mContext.getString(R.string.overlay_state_thinking);
            case TOOL_RUNNING:
                return mContext.getString(R.string.overlay_state_tool);
            case FINISHED:
                return mContext.getString(R.string.overlay_state_done);
            case ERROR:
                return mContext.getString(R.string.overlay_state_error);
            case IDLE:
            default:
                return mContext.getString(R.string.overlay_state_idle);
        }
    }

    private static int stateColor(State state) {
        switch (state) {
            case TASK_SHOWN:
                return 0xFF2196F3;
            case THINKING:
                return 0xFF9C27B0;
            case TOOL_RUNNING:
                return 0xFFFF9800;
            case FINISHED:
                return 0xFF4CAF50;
            case ERROR:
                return 0xFFF44336;
            case IDLE:
            default:
                return 0xFF9E9E9E;
        }
    }

    /**
     * Merge streaming thinking deltas into an existing row. Tool-call events may arrive
     * mid-stream (proxy flushes partial tool_call before reasoning finishes), so when
     * {@code requestId} is present we search backwards — not only at the list tail.
     */
    private boolean tryMergeThinking(AgentOverlayEvent event, Set<Integer> changedPositions) {
        if (!"thinking".equals(event.type) || mEvents.isEmpty()) {
            return false;
        }
        int targetIndex = findThinkingMergeTarget(event);
        if (targetIndex < 0) {
            return false;
        }
        mEvents.get(targetIndex).appendText(event.text);
        changedPositions.add(targetIndex);
        return true;
    }

    private int findThinkingMergeTarget(AgentOverlayEvent incoming) {
        String incomingRequestId = incoming.requestId;
        if (incomingRequestId != null && !incomingRequestId.isEmpty()) {
            for (int j = mEvents.size() - 1; j >= 0; j--) {
                if (mEvents.get(j).canMergeThinkingFrom(incoming)) {
                    return j;
                }
            }
            return -1;
        }
        int lastIndex = mEvents.size() - 1;
        AgentOverlayEvent last = mEvents.get(lastIndex);
        if (last.canMergeThinkingFrom(incoming)) {
            return lastIndex;
        }
        return -1;
    }

    private boolean tryMergeToolCall(AgentOverlayEvent event, Set<Integer> changedPositions) {
        if (!"tool_call".equals(event.type)) {
            return false;
        }
        if (event.id != null && !event.id.isEmpty()) {
            for (int j = mEvents.size() - 1; j >= 0; j--) {
                AgentOverlayEvent existing = mEvents.get(j);
                if ("tool_call".equals(existing.type) && event.id.equals(existing.id)) {
                    existing.mergeToolCallUpdate(event);
                    changedPositions.add(j);
                    return true;
                }
            }
            return false;
        }
        if (mEvents.isEmpty()) {
            return false;
        }
        int lastIndex = mEvents.size() - 1;
        AgentOverlayEvent last = mEvents.get(lastIndex);
        if (!"tool_call".equals(last.type)) {
            return false;
        }
        String incomingName = event.name != null ? event.name : "";
        String lastName = last.name != null ? last.name : "";
        if (incomingName.isEmpty()) {
            return false;
        }
        if (incomingName.equals(lastName) || incomingName.startsWith(lastName)) {
            last.mergeToolCallUpdate(event);
            changedPositions.add(lastIndex);
            return true;
        }
        return false;
    }

    private static AgentOverlayEvent parseEvent(JSONObject raw) {
        String type = raw.optString("type", "log");
        String id = raw.optString("id", null);
        String name = raw.optString("name", null);
        String text = firstNonEmpty(raw, "text", "message", "content");
        String args = stringifyEventField(raw, "args", "arguments");
        String output = firstNonEmpty(raw, "output", "result");
        boolean isError = raw.optBoolean("isError", false);
        double durationS = raw.optDouble("durationS", Double.NaN);
        Double duration = Double.isNaN(durationS) ? null : durationS;
        long ts = raw.optLong("ts", System.currentTimeMillis());
        String requestId = firstNonEmpty(raw, "request_id", "requestId");
        return new AgentOverlayEvent(type, id, name, text, args, output, isError, duration, ts, requestId);
    }

    /** Keep the latest timeline row fully visible (streaming thinking can exceed RV height). */
    private void scrollEventsToBottom() {
        if (mRvEvents == null || mEvents.isEmpty()) {
            return;
        }
        int last = mEvents.size() - 1;
        LinearLayoutManager lm = (LinearLayoutManager) mRvEvents.getLayoutManager();
        if (lm == null) {
            return;
        }
        mRvEvents.post(() -> scrollEventsToBottomInternal(lm, last, 0));
    }

    private void scrollEventsToBottomInternal(LinearLayoutManager lm, int last, int attempt) {
        if (mRvEvents == null) {
            return;
        }
        int rvHeight = mRvEvents.getHeight();
        if (rvHeight <= 0) {
            if (attempt < 4) {
                mRvEvents.post(() -> scrollEventsToBottomInternal(lm, last, attempt + 1));
            } else {
                lm.scrollToPosition(last);
            }
            return;
        }
        View child = lm.findViewByPosition(last);
        if (child == null) {
            lm.scrollToPosition(last);
            if (attempt < 4) {
                mRvEvents.post(() -> scrollEventsToBottomInternal(lm, last, attempt + 1));
            }
            return;
        }
        int scrollNeeded = child.getBottom() - (rvHeight - mRvEvents.getPaddingBottom());
        if (scrollNeeded > 0) {
            mRvEvents.scrollBy(0, scrollNeeded);
        }
    }

    private static String firstNonEmpty(JSONObject raw, String... keys) {
        for (String key : keys) {
            String value = raw.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String stringifyEventField(JSONObject raw, String... keys) {
        for (String key : keys) {
            if (!raw.has(key)) {
                continue;
            }
            Object value = raw.opt(key);
            if (value == null || value == JSONObject.NULL) {
                continue;
            }
            if (value instanceof String) {
                String text = ((String) value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
                continue;
            }
            if (value instanceof JSONObject || value instanceof JSONArray) {
                return value.toString();
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }
}
