package org.aohp.agentdriver.uda;

import android.util.Log;

import androidx.annotation.NonNull;

import org.aohp.agentdriver.executor.ShellExecutor;

/**
 * Applies small compatibility fixes to generated HTML apps before preview (e.g. router script
 * order).
 */
public final class UdaAppCompat {
    private static final String TAG = "UdaAppCompat";

    private UdaAppCompat() {}

    /** Patch {@code containerOutputDir/app} in the UDA container. Returns true if any fix applied. */
    public static boolean patchForPreview(
            @NonNull UdaContainerFs fs, @NonNull String containerOutputDir) {
        String appDir = containerOutputDir + "/app";
        if (!fs.isFile(appDir + "/index.html")) {
            return false;
        }
        ShellResult r = fs.execScript(buildPatchScript(appDir), 15000);
        if (!r.success) {
            Log.w(TAG, "patchForPreview failed: " + r.error);
            return false;
        }
        boolean changed = "patched".equals(r.output != null ? r.output.trim() : "");
        if (changed) {
            Log.i(TAG, "patched generated app under " + appDir);
        }
        return changed;
    }

    @NonNull
    private static String buildPatchScript(@NonNull String appDir) {
        return "python3 - <<'PY'\n"
                + "import re\n"
                + "from pathlib import Path\n"
                + "app = Path("
                + UdaShellUtils.shellQuote(appDir)
                + ")\n"
                + "changed = False\n"
                + "index = app / 'index.html'\n"
                + "if index.is_file():\n"
                + "    html = index.read_text(encoding='utf-8')\n"
                + "    router = re.search(r'\\s*<script src=\"\\./js/router\\.js\"></script>\\s*', html, re.I)\n"
                + "    main = re.search(r'\\s*<script src=\"\\./js/main\\.js\"></script>', html, re.I)\n"
                + "    if router and main and router.start() < main.start():\n"
                + "        tag = router.group(0)\n"
                + "        html = re.sub(r'\\s*<script src=\"\\./js/router\\.js\"></script>\\s*', '', html, count=1, flags=re.I)\n"
                + "        main = re.search(r'\\s*<script src=\"\\./js/main\\.js\"></script>', html, re.I)\n"
                + "        if main:\n"
                + "            html = html[:main.start()] + tag + html[main.start():]\n"
                + "            index.write_text(html, encoding='utf-8')\n"
                + "            changed = True\n"
                + "print('patched' if changed else 'ok')\n"
                + "PY";
    }

    /** Result of a container exec with captured stdout. */
    static final class ShellResult {
        boolean success;
        String output;
        String error;
    }
}
