package org.aohp.agentdriver.uda;

/** Shell helpers for container commands. */
public final class UdaShellUtils {
    private UdaShellUtils() {}

    public static String shellQuote(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
