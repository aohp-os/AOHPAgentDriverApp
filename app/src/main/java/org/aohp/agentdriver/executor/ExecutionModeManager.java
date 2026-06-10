package org.aohp.agentdriver.executor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 执行模式管理器：管理前台/后台执行模式的切换。
 */
public class ExecutionModeManager {
    private static final String TAG = "ExecutionModeManager";
    private static final String PREFS_NAME = "execution_mode_prefs";
    private static final String KEY_EXECUTION_MODE = "execution_mode";

    private static ExecutionModeManager instance;
    private final Context context;

    private ExecutionMode currentMode = ExecutionMode.FOREGROUND;
    private int virtualDisplayId = -1;

    public enum ExecutionMode {
        FOREGROUND,
        BACKGROUND
    }

    private ExecutionModeManager(Context context) {
        this.context = context.getApplicationContext();
        loadPreferences();
    }

    public static synchronized ExecutionModeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ExecutionModeManager(context);
        }
        return instance;
    }

    /**
     * 设置执行模式
     */
    public boolean setExecutionMode(ExecutionMode mode) {
        this.currentMode = mode;
        savePreferences();
        Log.d(TAG, "执行模式已切换为: " + mode);
        return true;
    }

    /**
     * 获取当前执行模式
     */
    public ExecutionMode getExecutionMode() {
        return currentMode;
    }

    /**
     * 是否使用后台模式
     */
    public boolean isBackgroundMode() {
        return currentMode == ExecutionMode.BACKGROUND;
    }

    /**
     * 设置虚拟屏幕 ID
     */
    public void setVirtualDisplayId(int displayId) {
        this.virtualDisplayId = displayId;
        Log.d(TAG, "虚拟屏幕 ID 已设置: " + displayId);
    }

    /**
     * 获取虚拟屏幕 ID
     */
    public int getVirtualDisplayId() {
        return virtualDisplayId;
    }

    /**
     * 虚拟屏幕是否就绪
     */
    public boolean isVirtualDisplayReady() {
        return virtualDisplayId > 0;
    }

    private void loadPreferences() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modeName = prefs.getString(KEY_EXECUTION_MODE, ExecutionMode.FOREGROUND.name());
        try {
            currentMode = ExecutionMode.valueOf(modeName);
        } catch (Exception e) {
            currentMode = ExecutionMode.FOREGROUND;
        }
    }

    private void savePreferences() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_EXECUTION_MODE, currentMode.name()).apply();
    }
}
