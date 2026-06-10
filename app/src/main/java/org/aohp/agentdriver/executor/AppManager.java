package org.aohp.agentdriver.executor;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.aohp.agentdriver.MainActivity;
import org.aohp.agentdriver.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.view.Display;

public class AppManager {
    private static final String CHANNEL_ID = "app_launch_channel";

    private final Context context;
    private final MyAccessibilityService accessibilityService;

    public AppManager(Context context, MyAccessibilityService accessibilityService) {
        this.context = context;
        this.accessibilityService = accessibilityService;
    }

    /**
     * 启动指定应用
     * 
     * @param context 上下文
     * @param appName 应用名称
     * @return 是否成功找到app并启动
     */
    public int launchApp(Context context, String appName) {
        try {
            // 使用 AppInfoManager 获取应用列表
            AppInfoManager appInfoManager = ExecutorManager.getInstance().getAppInfoManager();
            if (appInfoManager == null) {
                Log.e("AppLauncher", "AppInfoManager is null");
                return -1;
            }
            
            ArrayList<String> appNameAll = appInfoManager.getAppNameAll();
            ArrayList<String> appPkgAll = appInfoManager.getAppPkgAll();
            Map<String, List<String>> appNameLocalesMap = appInfoManager.getAppNameLocalesMap();
            
            if (appNameAll == null || appPkgAll == null) {
                Log.e("AppLauncher", "App lists are null, may not be initialized yet");
                return -1;
            }
            
            // 首先尝试直接匹配
            int index = appNameAll.indexOf(appName);

            // 如果没找到，遍历所有应用的本地化名称
            if (index == -1) {
                for (int i = 0; i < appPkgAll.size(); i++) {
                    String pkg = appPkgAll.get(i);
                    List<String> names = appNameLocalesMap.get(pkg);
                    if (names != null && names.contains(appName)) {
                        index = i;
                        break;
                    }
                }
            }

            if (index == -1) {
                Log.e("AppLauncher", "找不到应用: " + appName);
                return -1;
            }

            ArrayList<String> appLauncherAll = appInfoManager.getAppLauncherAll();
            if (appLauncherAll == null) {
                Log.e("AppLauncher", "appLauncherAll is null");
                return -1;
            }
            
            String appLauncher = appLauncherAll.get(index);
            String appPkg = appPkgAll.get(index);
            Intent intent = new Intent();
            ComponentName comp = new ComponentName(appPkg, appLauncher);
            intent.setComponent(comp);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);

            return 1;

            // 下面是检测普通启动方式被卡时，使用通知启动的代码，现在用不到了
            // // 等待一段时间，来检查是否跳转了，最多等待 1 s
            // for (int attempt = 0; attempt < 100; attempt++) {
            //     // 等待 0.1 秒
            //     SystemClock.sleep(100);
            //     String currentAppPackageName = accessibilityService.getCurrentAppPackageName();
            //     if (currentAppPackageName.equals(appPkg)) {
            //         return 1;
            //     }
            // }

            // // 如果当前应用不是目标应用，则使用通知启动
            // try {
            //     return launchAppWithNotification(context, appName);
            // } catch (JSONException e1) {
            //     Log.e("AppLauncher", "launchAppWithNotification 启动应用失败: " + appName, e1);
            //     return 0;
            // }

        } catch (Exception e) {
            Log.e("AppLauncher", "launchApp 启动应用失败: " + appName, e);
            return 0;

            // 下面是检测普通启动方式被卡时，使用通知启动的代码，现在用不到了
            // try {
            //     return launchAppWithNotification(context, appName);
            // } catch (JSONException e1) {
            //     Log.e("AppLauncher", "launchAppWithNotification 启动应用失败: " + appName, e1);
            //     return 0;
            // }
        }
    }

    /**
     * 启动指定应用，如果应用在后台则恢复到最后的界面
     * 
     * @param context 上下文
     * @param appName 应用名称
     * @return 启动结果：1=成功，0=失败，-1=未找到应用
     */
    public int launchAppToLastState(Context context, String appName) {
        try {
            // 查找应用包名
            int index = MainActivity.appNameAll.indexOf(appName);
            if (index == -1) {
                for (int i = 0; i < MainActivity.appPkgAll.size(); i++) {
                    String pkg = MainActivity.appPkgAll.get(i);
                    List<String> names = MainActivity.appNameLocalesMap.get(pkg);
                    if (names != null && names.contains(appName)) {
                        index = i;
                        break;
                    }
                }
            }

            if (index == -1) {
                Log.e("AppLauncher", "找不到应用: " + appName);
                return -1;
            }

            String appPkg = MainActivity.appPkgAll.get(index);

            // 使用新的标志组合
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(appPkg);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(intent);
                return 1;
            }

            // 如果获取 launch intent 失败，使用常规方式启动
            String appLauncher = MainActivity.appLauncherAll.get(index);
            intent = new Intent();
            ComponentName comp = new ComponentName(appPkg, appLauncher);
            intent.setComponent(comp);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return 1;

        } catch (Exception e) {
            Log.e("AppLauncher", "启动应用失败: " + appName, e);
            return 0;
        }
    }

    /**
     * 结束指定应用
     * 
     * @param context 上下文
     * @param appName 应用名称
     * @return 是否成功找到app并结束运行
     */
    public int killApp(Context context, String appName) {
        try {
            // 首先尝试直接匹配
            int index = MainActivity.appNameAll.indexOf(appName);

            // 如果没找到，遍历所有应用的本地化名称
            if (index == -1) {
                for (int i = 0; i < MainActivity.appPkgAll.size(); i++) {
                    String pkg = MainActivity.appPkgAll.get(i);
                    List<String> names = MainActivity.appNameLocalesMap.get(pkg);
                    if (names != null && names.contains(appName)) {
                        index = i;
                        break;
                    }
                }
            }

            if (index == -1) {
                Log.e("AppLauncher", "找不到应用: " + appName);
                return -1;
            }

            String packageName = MainActivity.appPkgAll.get(index);
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            // 方法1: 使用 killBackgroundProcesses (需要 KILL_BACKGROUND_PROCESSES 权限)
            am.killBackgroundProcesses(packageName);

            // 方法2: 使用 ShellExecutor / 系统权限策略下的 shell（由调用方决定）

            return 1;

        } catch (Exception e) {
            Log.e("AppLauncher", "结束应用失败: " + appName, e);
            return 0;
        }
    }

    /**
     * 获取指定应用的包名
     * 
     * @param context 上下文
     * @param appName 应用名称
     * @return 应用的包名
     */
    public String getPackageName(Context context, String appName) {
        try {
            // 查找应用包名
            int index = MainActivity.appNameAll.indexOf(appName);

            // 如果没找到，遍历所有应用的本地化名称
            if (index == -1) {
                for (int i = 0; i < MainActivity.appPkgAll.size(); i++) {
                    String pkg = MainActivity.appPkgAll.get(i);
                    List<String> names = MainActivity.appNameLocalesMap.get(pkg);
                    if (names != null && names.contains(appName)) {
                        index = i;
                        break;
                    }
                }
            }
            
            if (index == -1) {
                Log.e("AppLauncher", "找不到应用: " + appName);
                return null;
            }

            return MainActivity.appPkgAll.get(index);
        } catch (Exception e) {
            Log.e("AppLauncher", "getPackageName 失败: " + appName, e);
            return null;
        }
    }

    /**
     * 创建一个用于启动指定应用的 notification
     *
     * @param context 上下文
     * @param appName 应用名称
     */
    public int createLaunchAppNotification(Context context, String appName) {
        try {
            // 查找应用包名
            int index = MainActivity.appNameAll.indexOf(appName);
            if (index == -1) {
                for (int i = 0; i < MainActivity.appPkgAll.size(); i++) {
                    String pkg = MainActivity.appPkgAll.get(i);
                    List<String> names = MainActivity.appNameLocalesMap.get(pkg);
                    if (names != null && names.contains(appName)) {
                        index = i;
                        break;
                    }
                }
            }

            if (index == -1) {
                Log.e("AppLauncher", "createLaunchAppNotification 找不到应用: " + appName);
                return -1;
            }

            String appLauncher = MainActivity.appLauncherAll.get(index);
            String appPkg = MainActivity.appPkgAll.get(index);
            Intent intent = new Intent();
            ComponentName comp = new ComponentName(appPkg, appLauncher);
            intent.setComponent(comp);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 创建 PendingIntent
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // 创建通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "App Launch Channel",
                        NotificationManager.IMPORTANCE_HIGH);
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }

            // 创建通知
            Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("AOHP Agent 正在执行")
                    .setContentText("为您跳转到 " + appName + " app")
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build();

            // 显示通知
            NotificationManager notificationManager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, notification);

            return 1;

        } catch (Exception e) {
            Log.e("AppLauncher", "createLaunchAppNotification 失败: " + appName, e);
            return 0;
        }
    }

    /**
     * 通过 notification 启动指定应用，需进行一次模拟点击，用于系统安全限制无法直接启动应用的情况
     * 
     * @param context 上下文
     * @param appName 应用名称
     */
    public int launchAppWithNotification(Context context, String appName) throws JSONException {
        // 创建启动 app 的 notification
        int createNotificationResult = createLaunchAppNotification(context, appName);
        if (createNotificationResult != 1) {
            return createNotificationResult;
        }

        // 展开通知栏
        accessibilityService.expandNotificationBar();

        // 等待启动 app 的通知显示。最多等待 10s，即最多尝试100次，每次间隔0.1秒
        for (int attempt = 0; attempt < 100; attempt++) {
            // 等待 0.1 秒
            SystemClock.sleep(100);

            AohpVdClient vd = AohpVdClient.getInstance(context);
            if (!vd.useAohpForDisplayOps()) {
                return 0;
            }
            ShellExecutor.CommandResult dr = vd.dumpUiTree(Display.DEFAULT_DISPLAY, 0);
            if (!dr.success || dr.output == null || dr.output.isEmpty()) {
                continue;
            }
            JSONObject root = new JSONObject(dr.output);
            JSONArray vhList = root.getJSONArray("nodes");

            // 查找并点击通知
            for (int i = 0; i < vhList.length(); i++) {
                JSONObject node = vhList.getJSONObject(i);
                if (node.getString("text").equals("AOHP Agent 正在执行")) {
                    JSONArray bounds = node.getJSONArray("bounds");
                    JSONArray point0 = bounds.getJSONArray(0);
                    JSONArray point1 = bounds.getJSONArray(1);
                    int x = (point0.getInt(0) + point1.getInt(0)) / 2;
                    int y = (point0.getInt(1) + point1.getInt(1)) / 2;
                    accessibilityService.performClick(x, y);
                    return 1;
                }
            }
        }

        // 如果超过10秒还没找到通知，返回0，表示失败
        return 0;
    }


    /**
     * 获取手机中的所有 app 的名称及包名
     * 
     * @param context 上下文
     * @return 所有 app 的名称及包名
     */
    public List<Map<String, String>> getAllApps(Context context) {
        List<Map<String, String>> apps = new ArrayList<>();
        for (int i = 0; i < MainActivity.appNameAll.size(); i++) {
            String appName = MainActivity.appNameAll.get(i);
            String appPkg = MainActivity.appPkgAll.get(i);
            String appLauncher = MainActivity.appLauncherAll.get(i);
            apps.add(new HashMap<String, String>() {{
                put("appName", appName);
                put("appPkg", appPkg);
                put("appLauncher", appLauncher);
            }});
        }
        return apps;
    }
}
