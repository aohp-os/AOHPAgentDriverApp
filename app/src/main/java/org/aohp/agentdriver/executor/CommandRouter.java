package org.aohp.agentdriver.executor;

import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * 命令路由器
 * 根据执行模式将命令路由到前台或后台执行
 */
public class CommandRouter {
    private static final String TAG = "CommandRouter";

    /** Same as {@link InputManager#INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH} (value 2); use literal for SDK stubs. */
    private static final int INJECT_MODE_WAIT_FOR_FINISH = 2;
    
    private final Context context;
    private final ExecutionModeManager modeManager;
    private final ShellExecutor shellExecutor;
    private final MyAccessibilityService accessibilityService;
    private final RecordService recordService;
    private final AohpAgentViewClient aohpAgentViewClient;
    
    // 动作报告回调
    public interface ActionReporter {
        void reportAction(String action);
    }
    
    private ActionReporter actionReporter;
    
    public void setActionReporter(ActionReporter reporter) {
        this.actionReporter = reporter;
    }
    
    private void reportAction(String action) {
        if (actionReporter != null) {
            actionReporter.reportAction(action);
        }
    }
    
    public CommandRouter(Context context) {
        this.context = context;
        this.modeManager = ExecutionModeManager.getInstance(context);
        this.shellExecutor = ShellExecutor.getInstance();
        this.accessibilityService = MyAccessibilityService.getInstance();
        this.recordService = RecordManager.getRecordService();
        this.aohpAgentViewClient = new AohpAgentViewClient(context);
    }

    /**
     * Display id for capture ops: virtual display in background mode, else default display.
     */
    public int resolveCaptureDisplayId() {
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            return modeManager.getVirtualDisplayId();
        }
        return Display.DEFAULT_DISPLAY;
    }

    /**
     * Enhanced UI tree (stats + nodes) via {@link EnhancedTreeBuilder}.
     */
    public CompletableFuture<String> routeGetEnhancedUIHierarchy(int flags) {
        int displayId = resolveCaptureDisplayId();
        Log.d(TAG, "路由获取增强 UI 层级 displayId=" + displayId + " flags=0x"
                + Integer.toHexString(flags));
        return CompletableFuture.supplyAsync(() -> {
            AohpVdClient vd = AohpVdClient.getInstance(context);
            if (!vd.useAohpForDisplayOps()) {
                return "FAILED: aohp_virtual_display not available";
            }
            ShellExecutor.CommandResult r = vd.dumpUiTree(displayId, flags);
            return r.success && r.output != null ? r.output
                    : ("FAILED: " + (r.error != null ? r.error : "dumpUiTree"));
        });
    }

    /**
     * Full-display screenshot via {@code aohp_agent_view} (requires AOHP system image).
     */
    public CompletableFuture<String> routeCaptureFullScreenshotAohp(String outputPath, int quality) {
        int displayId = resolveCaptureDisplayId();
        return CompletableFuture.supplyAsync(() -> {
            if (!aohpAgentViewClient.isServiceAvailable()) {
                return "FAILED: aohp_agent_view not available";
            }
            ShellExecutor.CommandResult r =
                    aohpAgentViewClient.captureDisplayToFile(displayId, outputPath, quality);
            return r.success ? outputPath : "FAILED: " + r.error;
        });
    }

    /**
     * Region screenshot via {@code aohp_agent_view}.
     */
    public CompletableFuture<String> routeCaptureRegionScreenshotAohp(int left, int top, int right,
            int bottom, String outputPath, int quality) {
        int displayId = resolveCaptureDisplayId();
        return CompletableFuture.supplyAsync(() -> {
            if (!aohpAgentViewClient.isServiceAvailable()) {
                return "FAILED: aohp_agent_view not available";
            }
            ShellExecutor.CommandResult r = aohpAgentViewClient.captureRegionToFile(displayId,
                    left, top, right, bottom, outputPath, quality);
            return r.success ? outputPath : "FAILED: " + r.error;
        });
    }

    /**
     * Key event: 后台虚拟屏经 AOHP / {@link ShellExecutor}；前台默认屏优先 {@link InputManager}，
     * 失败再回退 {@code input keyevent}。
     */
    public CompletableFuture<String> routeKeyEvent(int keyCode) {
        reportAction("按键: " + keyCode);
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.keyEventOnDisplay(d, keyCode)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        }
        return CompletableFuture.supplyAsync(() -> {
            if (injectKeyOnDefaultDisplay(keyCode)) {
                Log.d(TAG, "routeKeyEvent: InputManager OK keyCode=" + keyCode);
                return "OK";
            }
            ShellExecutor.CommandResult r = shellExecutor.executeSync("input keyevent " + keyCode);
            if (r.success) {
                Log.d(TAG, "routeKeyEvent: shell input OK keyCode=" + keyCode);
                return "OK";
            }
            Log.e(TAG, "routeKeyEvent: failed shell=" + r.error + " exit=" + r.exitCode);
            return "FAILED: InputManager denied and shell failed: " + r.error;
        });
    }

    /**
     * 使用 InputManager 向默认显示注入按键（需 {@code android.permission.INJECT_EVENTS}，platform 签名 priv-app 可用）。
     * {@link InputManager#injectInputEvent(InputEvent, int)} 在公开 SDK 中常为 hide，故用反射调用。
     */
    private boolean injectKeyOnDefaultDisplay(int keyCode) {
        try {
            InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (im == null) {
                return false;
            }
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(now, now + 30, KeyEvent.ACTION_UP, keyCode, 0);
            down.setSource(InputDevice.SOURCE_KEYBOARD);
            up.setSource(InputDevice.SOURCE_KEYBOARD);
            return injectInputEventViaReflection(im, down)
                    && injectInputEventViaReflection(im, up);
        } catch (SecurityException e) {
            Log.w(TAG, "injectKeyOnDefaultDisplay: " + e.getMessage());
            return false;
        }
    }

    private static boolean injectInputEventViaReflection(InputManager im, InputEvent event) {
        try {
            Method m = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class);
            Object r = m.invoke(im, event, INJECT_MODE_WAIT_FOR_FINISH);
            return r instanceof Boolean && (Boolean) r;
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "injectInputEventViaReflection: no method", e);
            return false;
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof SecurityException) {
                Log.w(TAG, "injectInputEventViaReflection: " + c.getMessage());
            } else {
                Log.w(TAG, "injectInputEventViaReflection", c != null ? c : e);
            }
            return false;
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "injectInputEventViaReflection", e);
            return false;
        }
    }

    /**
     * 屏幕尺寸
     */
    public static class ScreenSize {
        public final int width;
        public final int height;

        public ScreenSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * 路由获取屏幕宽高
     *
     * - 后台模式 + 虚拟屏幕就绪：逻辑分辨率与本机内置屏 real 尺寸一致（AOHP 默认创建）
     * - 否则：从 AccessibilityService 获取前台屏幕宽高
     */
    public CompletableFuture<ScreenSize> routeGetWidthHeight() {
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            Log.d(TAG, "路由获取宽高到虚拟屏幕");
            return CompletableFuture.supplyAsync(() -> {
                int[] m = shellExecutor.getBuiltinDisplayRealMetricsPx();
                if (m != null) {
                    return new ScreenSize(m[0], m[1]);
                }
                return new ScreenSize(1080, 1920);
            });
        }

        Log.d(TAG, "路由获取宽高到前台");
        return CompletableFuture.supplyAsync(() -> {
            if (accessibilityService != null) {
                return new ScreenSize(accessibilityService.getWidth(), accessibilityService.getHeight());
            }
            throw new RuntimeException("AccessibilityService not available");
        });
    }
    
    /**
     * 路由点击命令
     */
    public CompletableFuture<String> routeClick(float x, float y, long duration) {
        reportAction(String.format("点击 (%.0f, %.0f)", x, y));
        
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            Log.d(TAG, "路由点击到虚拟屏幕: (" + x + ", " + y + ")");
            int d = modeManager.getVirtualDisplayId();
            if (duration > 200) {
                return shellExecutor.longPressOnDisplay(d, (int) x, (int) y, (int) duration)
                        .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
            }
            return shellExecutor.tapOnDisplay(d, (int) x, (int) y)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        } else {
            // 前台执行：使用 AccessibilityService
            Log.d(TAG, "路由点击到前台: (" + x + ", " + y + ")");
            return CompletableFuture.supplyAsync(() -> {
                if (accessibilityService != null) {
                    accessibilityService.performClick(x, y, duration);
                    return "OK";
                } else {
                    return "FAILED: AccessibilityService not available";
                }
            });
        }
    }
    
    /**
     * 路由滑动命令
     */
    public CompletableFuture<String> routeSwipe(float startX, float startY, 
                                                 float endX, float endY, long duration) {
        reportAction(String.format("滑动 从(%.0f, %.0f) 到(%.0f, %.0f)", startX, startY, endX, endY));
        
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            Log.d(TAG, "路由滑动到虚拟屏幕");
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.swipeOnDisplay(d, (int) startX, (int) startY, (int) endX, (int) endY,
                    (int) duration)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        } else {
            // 前台执行
            Log.d(TAG, "路由滑动到前台");
            return CompletableFuture.supplyAsync(() -> {
                if (accessibilityService != null) {
                    accessibilityService.performScroll(startX, startY, endX, endY, duration);
                    return "OK";
                } else {
                    return "FAILED: AccessibilityService not available";
                }
            });
        }
    }
    
    /**
     * 路由截图命令
     */
    public CompletableFuture<String> routeScreenshot(String outputPath) {
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            // 后台执行：截取虚拟屏幕
            Log.d(TAG, "路由截图到虚拟屏幕");
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.screenshotOnDisplay(d, outputPath)
                    .thenApply(r -> r.success ? outputPath : "FAILED: " + r.error);
        } else {
            // 前台执行：使用 RecordService
            Log.d(TAG, "路由截图到前台");
            if (recordService != null) {
                // RecordService.takeScreenshot() 不接受参数，返回路径
                return CompletableFuture.supplyAsync(() -> {
                    String path = recordService.takeScreenshot();
                    if (path != null && !path.isEmpty()) {
                        return path;
                    } else {
                        return "FAILED: Screenshot failed";
                    }
                });
            } else {
                return CompletableFuture.completedFuture("FAILED: RecordService not available");
            }
        }
    }
    
    /**
     * 路由启动应用命令（通过包名）
     */
    public CompletableFuture<String> routeStartApp(String packageName) {
        reportAction("启动应用: " + packageName);
        
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            // 后台执行：在虚拟屏幕上启动应用
            Log.d(TAG, "路由启动应用到虚拟屏幕: " + packageName);
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.launchAppOnDisplay(d, packageName, null)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        } else {
            // 前台执行：正常启动应用
            Log.d(TAG, "路由启动应用到前台: " + packageName);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Intent intent = context.getPackageManager()
                        .getLaunchIntentForPackage(packageName);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        return "OK";
                    } else {
                        return "FAILED: Package not found";
                    }
                } catch (Exception e) {
                    Log.e(TAG, "启动应用失败", e);
                    return "FAILED: " + e.getMessage();
                }
            });
        }
    }
    
    /**
     * 路由启动应用命令（通过应用名称，使用 AppManager）
     * 返回: 1=成功, 0=失败, -1=未找到
     */
    public CompletableFuture<Integer> routeStartAppByName(String appName, AppManager appManager) {
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            // 后台执行：需要先通过 AppManager 找到包名，然后在虚拟屏幕上启动
            Log.d(TAG, "路由启动应用到虚拟屏幕（通过名称）: " + appName);
            
            return CompletableFuture.supplyAsync(() -> {
                // 使用 AppManager 查找包名
                try {
                    ExecutorManager executorManager = ExecutorManager.getInstance();
                    AppInfoManager appInfoManager = executorManager.getAppInfoManager();
                    if (appInfoManager == null) {
                        Log.e(TAG, "AppInfoManager is null");
                        return -1;
                    }
                    
                    java.util.ArrayList<String> appNameAll = appInfoManager.getAppNameAll();
                    java.util.ArrayList<String> appPkgAll = appInfoManager.getAppPkgAll();
                    java.util.Map<String, java.util.List<String>> appNameLocalesMap = appInfoManager.getAppNameLocalesMap();
                    
                    if (appNameAll == null || appPkgAll == null) {
                        Log.e(TAG, "App lists are null");
                        return -1;
                    }
                    
                    // 查找包名
                    int index = appNameAll.indexOf(appName);
                    if (index == -1) {
                        for (int i = 0; i < appPkgAll.size(); i++) {
                            String pkg = appPkgAll.get(i);
                            java.util.List<String> names = appNameLocalesMap.get(pkg);
                            if (names != null && names.contains(appName)) {
                                index = i;
                                break;
                            }
                        }
                    }
                    
                    if (index == -1) {
                        Log.e(TAG, "找不到应用: " + appName);
                        return -1;
                    }
                    
                    String packageName = appPkgAll.get(index);
                    Log.d(TAG, "找到应用包名: " + packageName);
                    int d = modeManager.getVirtualDisplayId();
                    ShellExecutor.CommandResult result =
                            shellExecutor.launchAppOnDisplay(d, packageName, null).get();
                    return result.success ? 1 : 0;
                } catch (Exception e) {
                    Log.e(TAG, "启动应用失败", e);
                    return 0;
                }
            });
        } else {
            // 前台执行：使用 AppManager
            Log.d(TAG, "路由启动应用到前台（通过名称）: " + appName);
            return CompletableFuture.supplyAsync(() -> {
                if (appManager != null) {
                    return appManager.launchApp(context, appName);
                } else {
                    Log.e(TAG, "AppManager is null");
                    return -1;
                }
            });
        }
    }
    
    /**
     * 路由获取 UI 层级命令（统一：无障碍遍历 + 按 displayId 取根，与前台/虚拟屏一致）
     */
    public CompletableFuture<String> routeGetUIHierarchy() {
        int displayId = resolveCaptureDisplayId();
        Log.d(TAG, "路由获取 UI 层级 displayId=" + displayId);
        return CompletableFuture.supplyAsync(() -> {
            AohpVdClient vd = AohpVdClient.getInstance(context);
            if (!vd.useAohpForDisplayOps()) {
                return "FAILED: aohp_virtual_display not available";
            }
            ShellExecutor.CommandResult r = vd.dumpUiTree(displayId, 0);
            return r.success && r.output != null ? r.output
                    : ("FAILED: " + (r.error != null ? r.error : "dumpUiTree"));
        });
    }
    
    /**
     * 路由返回键
     */
    public CompletableFuture<String> routeBack() {
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.keyEventOnDisplay(d, ShellExecutor.KeyCode.KEYCODE_BACK)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        } else {
            return CompletableFuture.supplyAsync(() -> {
                if (accessibilityService != null) {
                    accessibilityService.performBack();
                    return "OK";
                } else {
                    return "FAILED: AccessibilityService not available";
                }
            });
        }
    }
    
    /**
     * 路由 Home 键
     */
    public CompletableFuture<String> routeHome() {
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.keyEventOnDisplay(d, ShellExecutor.KeyCode.KEYCODE_HOME)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        } else {
            return CompletableFuture.supplyAsync(() -> {
                if (accessibilityService != null) {
                    accessibilityService.performHome();
                    return "OK";
                } else {
                    return "FAILED: AccessibilityService not available";
                }
            });
        }
    }
    
    /**
     * 路由长按命令
     */
    public CompletableFuture<String> routeLongClick(float x, float y) {
        reportAction(String.format("长按 (%.0f, %.0f)", x, y));
        
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            Log.d(TAG, "路由长按到虚拟屏幕: (" + x + ", " + y + ")");
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.longPressOnDisplay(d, (int) x, (int) y, 1000)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        } else {
            Log.d(TAG, "路由长按到前台: (" + x + ", " + y + ")");
            return CompletableFuture.supplyAsync(() -> {
                if (accessibilityService != null) {
                    accessibilityService.performLongClick(x, y);
                    return "OK";
                } else {
                    return "FAILED: AccessibilityService not available";
                }
            });
        }
    }

    /**
     * 路由文本输入（虚拟屏用 shell input text；前台用无障碍）
     */
    public CompletableFuture<String> routeInputText(String text) {
        reportAction("输入文本: " + text);
        if (modeManager.isBackgroundMode() && modeManager.isVirtualDisplayReady()) {
            Log.d(TAG, "路由文本输入到虚拟屏幕");
            int d = modeManager.getVirtualDisplayId();
            return shellExecutor.inputTextOnDisplay(d, text)
                    .thenApply(r -> r.success ? "OK" : "FAILED: " + r.error);
        }
        Log.d(TAG, "路由文本输入到前台");
        return CompletableFuture.supplyAsync(() -> {
            if (accessibilityService != null) {
                accessibilityService.performInput(text);
                return "OK";
            }
            return "FAILED: AccessibilityService not available";
        });
    }
}
