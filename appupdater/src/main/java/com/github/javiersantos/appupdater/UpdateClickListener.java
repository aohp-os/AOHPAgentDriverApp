package com.github.javiersantos.appupdater;

import android.content.Context;
import android.content.DialogInterface;

import com.github.javiersantos.appupdater.enums.UpdateFrom;

import java.net.URL;

import com.github.javiersantos.appupdater.objects.Update;
import com.king.app.updater.AppUpdater;

/**
 * Click listener for the "Update" button of the update dialog. <br/>
 * Extend this class to add custom actions to the button on top of the default functionality.
 */
public class UpdateClickListener implements DialogInterface.OnClickListener {

    private final Context context;
    private final UpdateFrom updateFrom;
    private final URL apk;
    private final Update update;

    public UpdateClickListener(final Context context, final UpdateFrom updateFrom, final Update update, final URL apk) {
        this.context = context;
        this.updateFrom = updateFrom;
        this.apk = apk;
        this.update = update;
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        if (updateFrom == UpdateFrom.GITHUB) {
            String apkUrl = apk.toString().replace("latest", "download/v" + update.getLatestVersion()) + "/AOHPAgentDriver-release.apk";
            AppUpdater mAppUpdater = new AppUpdater(context, apkUrl);
            mAppUpdater.start();
        } else if (updateFrom == UpdateFrom.JSON) {
            AppUpdater mAppUpdater = new AppUpdater(context, apk.toString());
            mAppUpdater.start();
        }
        else {
            UtilsLibrary.goToUpdate(context, updateFrom, apk);
        }

    }
}
