package org.aohp.agentdriver.executor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import org.aohp.agentdriver.MainActivity;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppInfoManager {
    private static final String TAG = "AppInfoManager";
    private static final String APP_DATA_FILENAME = "app_data.json";
    private static final String PREFS_NAME = "app_cache";
    private static final String CACHE_KEY = "app_data";

    private final Context context;
    private ArrayList<String> appNameAll = new ArrayList<>();
    private ArrayList<String> appPkgAll = new ArrayList<>();
    private ArrayList<String> appLauncherAll = new ArrayList<>();
    private Map<String, List<String>> appNameLocalesMap = new HashMap<>();

    public AppInfoManager(Context context) {
        this.context = context;
    }

    public void loadAppInfo() {
        Log.d(TAG, "loadAppInfo");
        
        // 优先从缓存加载
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedData = prefs.getString(CACHE_KEY, null);

        if (cachedData != null) {
            parseAppDataFromJson(cachedData);
            Log.d(TAG, "从缓存加载数据成功");
        } else {
            String fileData = readFromFile();
            if (fileData != null) {
                parseAppDataFromJson(fileData);
                Log.d(TAG, "从文件加载数据成功");
            }
        }

        refreshInBackground();
    }

    /** Re-scan launcher activities (e.g. after UDA pin/unpin). */
    public void refreshInBackground() {
        new Thread(
                        () -> {
                            Log.d(TAG, "正在后台更新应用数据");
                            getAllAppInfo();
                            saveData();
                        })
                .start();
    }

    private void getAllAppInfo() {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(pm));

        synchronized (this) {
            appNameAll.clear();
            appPkgAll.clear();
            appLauncherAll.clear();
            appNameLocalesMap.clear();
        }

        for (ResolveInfo resolveInfo : resolveInfos) {
            if (!resolveInfo.activityInfo.enabled) {
                continue;
            }
            String pkg = resolveInfo.activityInfo.packageName;
            String cls = resolveInfo.activityInfo.name;
            List<String> localizedNames = new ArrayList<>();

            try {
                Resources res = pm.getResourcesForApplication(pkg);
                ApplicationInfo applicationInfo = pm.getPackageInfo(pkg,
                        PackageManager.GET_META_DATA).applicationInfo;

                String defaultName = applicationInfo.loadLabel(pm).toString();
                localizedNames.add(defaultName);

                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
                    if (ai.labelRes != 0) {
                        Configuration conf = new Configuration();
                        String[] locales = res.getAssets().getLocales();
                        for (String localeStr : locales) {
                            try {
                                Locale locale = new Locale(localeStr);
                                conf.setLocale(locale);
                                Resources localizedRes = pm.getResourcesForApplication(pkg);
                                localizedRes.updateConfiguration(conf, res.getDisplayMetrics());
                                String localizedLabel = localizedRes.getString(ai.labelRes);
                                if (!localizedNames.contains(localizedLabel)) {
                                    localizedNames.add(localizedLabel);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "获取本地化标题失败: " + localeStr, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "获取应用元数据失败: " + pkg, e);
                }

                synchronized (this) {
                    appNameAll.add(defaultName);
                    appNameLocalesMap.put(pkg, localizedNames);
                }

            } catch (Exception e) {
                Log.e(TAG, "处理应用信息失败: " + pkg, e);
                synchronized (this) {
                    appNameAll.add("");
                    appNameLocalesMap.put(pkg, new ArrayList<>());
                }
            }

            synchronized (this) {
                appPkgAll.add(pkg);
                appLauncherAll.add(cls);
            }
        }
    }

    private void saveData() {
        Gson gson = new Gson();
        // 创建列表的副本，避免在序列化过程中被其他线程修改导致 ConcurrentModificationException
        synchronized (this) {
            ArrayList<String> namesCopy = new ArrayList<>(appNameAll);
            ArrayList<String> pkgsCopy = new ArrayList<>(appPkgAll);
            ArrayList<String> launchersCopy = new ArrayList<>(appLauncherAll);
            Map<String, List<String>> localesMapCopy = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : appNameLocalesMap.entrySet()) {
                localesMapCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            
            AppData appData = new AppData(namesCopy, pkgsCopy, launchersCopy, localesMapCopy);
            String jsonData = gson.toJson(appData);

            // 保存到缓存
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(CACHE_KEY, jsonData).apply();

            // 保存到文件
            saveToFile(jsonData);
        }
    }

    private void saveToFile(String data) {
        try {
            java.io.FileOutputStream fos = context.openFileOutput(APP_DATA_FILENAME, Context.MODE_PRIVATE);
            fos.write(data.getBytes());
            fos.close();
            Log.d(TAG, "数据已保存到本地文件");
        } catch (Exception e) {
            Log.e(TAG, "保存文件失败", e);
        }
    }

    private String readFromFile() {
        try {
            java.io.FileInputStream fis = context.openFileInput(APP_DATA_FILENAME);
            java.io.InputStreamReader isr = new java.io.InputStreamReader(fis);
            java.io.BufferedReader br = new java.io.BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            fis.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "读取文件失败", e);
            return null;
        }
    }

    private void parseAppDataFromJson(String jsonData) {
        Gson gson = new Gson();
        AppData data = gson.fromJson(jsonData, AppData.class);
        
        synchronized (this) {
            appNameAll = data.appNameAll;
            appPkgAll = data.appPkgAll;
            appLauncherAll = data.appLauncherAll;
            appNameLocalesMap = data.appNameLocalesMap;
        }
        
        // 同步更新MainActivity中的静态变量
        MainActivity.appNameAll = appNameAll;
        MainActivity.appPkgAll = appPkgAll;
        MainActivity.appLauncherAll = appLauncherAll;
        MainActivity.appNameLocalesMap = appNameLocalesMap;
    }

    // Getter methods
    public synchronized ArrayList<String> getAppNameAll() {
        return new ArrayList<>(appNameAll);
    }
    
    public synchronized ArrayList<String> getAppPkgAll() {
        return new ArrayList<>(appPkgAll);
    }
    
    public synchronized ArrayList<String> getAppLauncherAll() {
        return new ArrayList<>(appLauncherAll);
    }
    
    public synchronized Map<String, List<String>> getAppNameLocalesMap() {
        return new HashMap<>(appNameLocalesMap);
    }

    private static class AppData {
        ArrayList<String> appNameAll;
        ArrayList<String> appPkgAll;
        ArrayList<String> appLauncherAll;
        Map<String, List<String>> appNameLocalesMap;

        AppData(ArrayList<String> appNameAll, ArrayList<String> appPkgAll,
                ArrayList<String> appLauncherAll, Map<String, List<String>> appNameLocalesMap) {
            this.appNameAll = appNameAll;
            this.appPkgAll = appPkgAll;
            this.appLauncherAll = appLauncherAll;
            this.appNameLocalesMap = appNameLocalesMap;
        }
    }
}