package org.aohp.agentdriver.uda;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;

/** Resolves launcher / shortcut icons for UDA apps. */
public final class UdaIconHelper {
    private UdaIconHelper() {}

    @NonNull
    public static Icon loadShortcutIcon(@NonNull Context context, @NonNull UdaJobInfo job) {
        Bitmap fromFile = loadIconBitmap(context, job);
        if (fromFile != null) {
            return Icon.createWithAdaptiveBitmap(fromFile);
        }
        return Icon.createWithAdaptiveBitmap(letterIcon(context, job.appName));
    }

    @Nullable
    private static Bitmap loadIconBitmap(@NonNull Context context, @NonNull UdaJobInfo job) {
        String root = UdaPaths.resolveJobRoot(context, job.jobId).getAbsolutePath();
        String[] candidates = {
            root + "/app/icon.png",
            root + "/app/icons/icon-192.png",
            root + "/app/manifest-icon.png",
        };
        for (String path : candidates) {
            File f = new File(path);
            if (!f.isFile()) {
                continue;
            }
            try (FileInputStream in = new FileInputStream(f)) {
                Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                if (bmp != null) {
                    return bmp;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @NonNull
    private static Bitmap letterIcon(@NonNull Context context, @NonNull String appName) {
        int sizePx =
                (int)
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                108,
                                context.getResources().getDisplayMetrics());
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#4F46E5"));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sizePx * 0.45f);
        String letter = appName.isEmpty() ? "A" : appName.substring(0, 1);
        Rect bounds = new Rect();
        paint.getTextBounds(letter, 0, letter.length(), bounds);
        canvas.drawText(
                letter,
                sizePx / 2f,
                sizePx / 2f - bounds.exactCenterY(),
                paint);
        return bmp;
    }
}
