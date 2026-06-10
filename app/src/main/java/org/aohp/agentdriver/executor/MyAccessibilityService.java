package org.aohp.agentdriver.executor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.Path;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.atomic.AtomicReference;

public class MyAccessibilityService extends AccessibilityService {
    private static MyAccessibilityService instance;
    public int width;
    public int height;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d("Accessibility Service", "Created");
        // 获取真实的屏幕尺寸
        android.view.WindowManager windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        android.view.Display display = windowManager.getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getRealSize(size);
        width = size.x;
        height = size.y;
    }

    public static MyAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        // Log.i("Accessibility Event", accessibilityEvent.toString());
        // performClick(100, 100);
        // performLongClick(1000, 1000);
        // performScroll(300, 300, 300, 600);
        // performInput("Hello world");
        // Log.d("Accessibility Service VH", captureViewHierarchy());

    }

    @Override
    public void onInterrupt() {
        // 处理中断

    }

    /**
     * 模拟点击动作（不带持续时间）
     * 
     * @param x 点击的x坐标
     * @param y 点击的y坐标
     */
    public void performClick(float x, float y) {
        int defaultDuration = 50; // 默认持续时间为50毫秒
        performClick(x, y, defaultDuration);
    }

    /**
     * 模拟点击动作
     *
     * @param x 点击的x坐标
     * @param y 点击的y坐标
     */
    public void performClick(float x, float y, long duration) {
        logToRemoteMode("执行点击操作: " + x + "," + y);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 创建一个路径，表示点击的位置
            Path path = new Path();
            path.moveTo(x, y); // 设置点击的坐标位置

            // 构建手势描述，设置手势持续时间
            GestureDescription gestureDescription = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)) // 延迟0毫秒，持续duration毫秒
                    .build();

            // 执行手势
            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    logToRemoteMode("点击操作完成: " + x + "," + y);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    logToRemoteMode("点击操作取消");
                }
            }, null);
        }, 100); // 延迟100毫秒
    }

    /**
     * 模拟长按动作
     * 
     * @param x 点击的x坐标
     * @param y 点击的y坐标
     */
    public void performLongClick(float x, float y) {
        logToRemoteMode("执行长按操作: " + x + "," + y);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 创建一个路径，表示点击的位置
            Path path = new Path();
            path.moveTo(x, y); // 设置点击的坐标位置

            // 构建手势描述，设置手势持续时间
            GestureDescription gestureDescription = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 1000)) // 延迟0毫秒，持续1000毫秒
                    .build();

            // 执行手势
            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    logToRemoteMode("长按操作完成: " + x + "," + y);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    logToRemoteMode("长按操作取消");
                }
            }, null);
        }, 100); // 延迟100毫秒
    }

    /**
     * 模拟滑动动作（不带持续时间）
     * 
     * @param startX 滑动的起点x坐标
     * @param startY 滑动的起点y坐标
     * @param endX   滑动的终点x坐标
     * @param endY   滑动的终点y坐标
     */
    public void performScroll(float startX, float startY, float endX, float endY) {
        int defaultDuration = 500; // 默认持续时间为500毫秒
        performScroll(startX, startY, endX, endY, defaultDuration);
    }

    /**
     * 模拟滑动动作
     * 
     * @param startX 滑动的起点x坐标
     * @param startY 滑动的起点y坐标
     * @param endX   滑动的终点x坐标
     * @param endY   滑动的终点y坐标
     */
    public void performScroll(float startX, float startY, float endX, float endY, long duration) {
        logToRemoteMode("执行滑动操作: " + startX + "," + startY + " -> " + endX + "," + endY);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY); // 定义从起点到终点的滑动路径

            GestureDescription gestureDescription = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)) // 延迟0毫秒，持续duration毫秒
                    .build();

            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d("Gesture", "Scroll completed");
                    logToRemoteMode("滑动操作完成");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d("Gesture", "Scroll cancelled");
                    logToRemoteMode("滑动操作取消");
                }
            }, null);
        }, 100); // 延迟100毫秒
    }

    /**
     * 模拟输入: 输入到当前聚焦到的组件
     * 
     * @param text 输入的文本
     */
    public void performInput(String text) {
        // 将文本复制到剪贴板
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("input", text);
        clipboard.setPrimaryClip(clip);

        // 模拟粘贴操作（需要焦点在输入框上）
        // 获取当前活动窗口的根节点
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            // 查找具有焦点的可编辑文本框节点
            AccessibilityNodeInfo focusedNode = findFocusedEditableNode(rootNode);
            if (focusedNode != null) {
                // 如果找到具有焦点的可编辑文本框，执行粘贴动作
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                logToRemoteMode("正在输入文本");
            }
        }
    }

    /**
     * 获取当前聚焦的输入框中的文本内容
     * 
     * @return 输入框中的文本，如果没有找到聚焦的输入框则返回null
     */
    public String getFocusedInputText() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            AccessibilityNodeInfo focusedNode = findFocusedEditableNode(rootNode);
            if (focusedNode != null) {
                CharSequence text = focusedNode.getText();
                focusedNode.recycle();
                rootNode.recycle();
                return text != null ? text.toString() : null;
            }
            rootNode.recycle();
        }
        return null;
    }

    public String getClipboardText(){
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0){
            String text = clip.getItemAt(0).getText().toString();
            logToRemoteMode("剪贴板内容: " + text);
            return text;
        }else{
            logToRemoteMode("剪贴板为空");
            return null;
        }
    }

    public void setClipboardText(String text){
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("input", text);
        clipboard.setPrimaryClip(clip);
        logToRemoteMode("已复制到剪贴板: " + text);
    }

    /**
     * 清空当前聚焦的输入框内容
     */
    public void performClearInput() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            AccessibilityNodeInfo focusedNode = findFocusedEditableNode(rootNode);
            if (focusedNode != null) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                focusedNode.recycle();
                logToRemoteMode("正在清空输入框");
            }
            rootNode.recycle();
        }
    }

    /**
     * 遍历节点树以查找具有焦点的可编辑节点
     * 
     * @param rootNode 根节点
     * @return 具有焦点的可编辑节点
     */
    private AccessibilityNodeInfo findFocusedEditableNode(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            return null;
        }

        // 如果节点是可编辑的并且具有焦点，返回该节点
        if (rootNode.isEditable() && rootNode.isFocused()) {
            return rootNode;
        }

        // 遍历子节点递归查找
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = rootNode.getChild(i);
            AccessibilityNodeInfo focusedNode = findFocusedEditableNode(childNode);
            if (focusedNode != null) {
                return focusedNode;
            }
        }

        return null;
    }

    /**
     * 模拟 Back 动作
     */
    public void performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
        logToRemoteMode("返回");
    }

    /**
     * 模拟 Home 动作
     */
    public void performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
        logToRemoteMode("返回到桌面");
    }

    /**
     * 模拟系统截图, 无法自定义截图名称, 因此无法直接获取到截图内容
     */
    public void performScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            logToRemoteMode("视觉模型正在扫描屏幕...");
        }
    }

    /**
     * 获取屏幕实际宽度
     * 
     * @return 屏幕宽度
     */
    public int getWidth() {
        return width;
    }

    /**
     * 获取屏幕实际高度
     * 
     * @return 屏幕高度
     */
    public int getHeight() {
        return height;
    }

    /**
     * 展开通知栏
     */
    public void expandNotificationBar() {
        try {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            logToRemoteMode("下拉通知栏");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前前台 app 的包名
     * 
     * @return 当前前台 app 包名
     */
    public String getCurrentAppPackageName() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            return rootNode.getPackageName() != null ? rootNode.getPackageName().toString() : null;
        }
        return null;
    }

    private void logToRemoteMode(String message) {
        Log.d("AccessibilityService", message);
    }
}
