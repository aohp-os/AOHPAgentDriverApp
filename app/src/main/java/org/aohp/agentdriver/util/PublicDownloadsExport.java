package org.aohp.agentdriver.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes text files under {@code Download/AOHP/} so {@code adb pull /sdcard/Download/AOHP/...} works
 * without root (unlike app-private {@code /data/data/.../cache}).
 */
public final class PublicDownloadsExport {
    private static final String TAG = "PublicDownloadsExport";
    public static final String SUBDIR = "AOHP";

    private PublicDownloadsExport() {
    }

    /**
     * @return absolute path hint for UI (best-effort; MediaStore may not expose a raw path)
     */
    public static String saveUtf8(Context context, String fileName, String content) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveViaMediaStore(context, fileName, content);
        }
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("mkdirs: " + dir);
        }
        File f = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f.getAbsolutePath();
    }

    private static String saveViaMediaStore(Context context, String fileName, String content)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore.insert returned null");
        }
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                throw new IOException("openOutputStream null");
            }
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignore) {
            }
            throw e;
        }
        Log.i(TAG, "saved MediaStore uri=" + uri);
        return "/sdcard/Download/" + SUBDIR + "/" + fileName;
    }
}
