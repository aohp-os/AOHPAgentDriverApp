package org.aohp.agentdriver.overlay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/** Formats tool-call argument JSON for overlay display. */
final class ToolCallFormatter {

    private static final int SKILL_RESULT_PREVIEW_CHARS = 160;

    private ToolCallFormatter() {
    }

    static boolean isSkillReadCall(String toolName, String argsJson) {
        return skillNameFromReadCall(toolName, argsJson) != null;
    }

    static String skillNameFromReadCall(String toolName, String argsJson) {
        if (toolName == null || !"read".equals(toolName.trim().toLowerCase(Locale.US))) {
            return null;
        }
        String path = readPathFromArgs(argsJson);
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        if (!normalized.toLowerCase(Locale.US).endsWith("skill.md")) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        if (slash < 0 || slash >= normalized.length() - 1) {
            return null;
        }
        String parent = normalized.substring(0, slash);
        int parentSlash = parent.lastIndexOf('/');
        String skillDir = parentSlash >= 0 ? parent.substring(parentSlash + 1) : parent;
        return skillDir.isEmpty() ? null : skillDir;
    }

    static String formatSkillReadResult(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        String trimmed = markdown.trim();
        String name = extractYamlField(trimmed, "name");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String description = extractYamlField(trimmed, "description");
        if (description != null && !description.isEmpty()) {
            return truncate(description, SKILL_RESULT_PREVIEW_CHARS);
        }
        return truncate(trimmed, SKILL_RESULT_PREVIEW_CHARS);
    }

    static String formatBody(String toolName, String argsJson) {
        if (argsJson == null || argsJson.trim().isEmpty()) {
            return "";
        }
        String name = toolName != null ? toolName.trim().toLowerCase(Locale.US) : "";
        if (("read".equals(name) || name.contains("read")) && looksLikeSkillMarkdown(argsJson)) {
            return formatSkillReadResult(argsJson);
        }
        try {
            JSONObject args = new JSONObject(argsJson.trim());
            if ("exec".equals(name)) {
                if (args.has("stdout") || args.has("stderr") || args.has("exitCode") || args.has("code")) {
                    return formatExecResult(args);
                }
                return formatExec(args);
            }
            if (name.contains("read") || "read".equals(name)) {
                return formatRead(args);
            }
            if (name.contains("write") || "write".equals(name)) {
                return formatWrite(args);
            }
            return formatGeneric(args);
        } catch (JSONException e) {
            return formatPartialJson(argsJson.trim());
        }
    }

    private static String formatExec(JSONObject args) {
        StringBuilder sb = new StringBuilder();
        String command = args.optString("command", "");
        if (!command.isEmpty()) {
            sb.append("$ ").append(command);
        }
        appendLine(sb, "host", args.optString("host", ""));
        appendLine(sb, "workdir", args.optString("workdir", ""));
        if (args.has("timeout") && !isEmptyValue(args.opt("timeout"))) {
            appendLine(sb, "timeout", args.opt("timeout") + "s");
        }
        if (args.optBoolean("pty", false)) {
            appendLine(sb, "pty", "true");
        }
        if (args.optBoolean("background", false)) {
            appendLine(sb, "background", "true");
        }
        if (sb.length() == 0) {
            return formatGeneric(args);
        }
        return sb.toString().trim();
    }

    private static String formatExecResult(JSONObject result) {
        StringBuilder sb = new StringBuilder();
        if (result.has("exitCode")) {
            appendLine(sb, "exit", String.valueOf(result.opt("exitCode")));
        } else if (result.has("code")) {
            appendLine(sb, "exit", String.valueOf(result.opt("code")));
        }
        appendLine(sb, "stdout", result.optString("stdout", ""));
        appendLine(sb, "stderr", result.optString("stderr", ""));
        appendLine(sb, "error", result.optString("error", ""));
        if (sb.length() == 0) {
            return formatGeneric(result);
        }
        return sb.toString().trim();
    }

    private static String formatRead(JSONObject args) {
        String path = firstNonEmpty(args, "path", "file", "filePath");
        if (path != null) {
            String skillName = skillNameFromPath(path);
            if (skillName != null) {
                return skillName;
            }
            return "📄 " + path;
        }
        return formatGeneric(args);
    }

    private static String readPathFromArgs(String argsJson) {
        if (argsJson == null || argsJson.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject args = new JSONObject(argsJson.trim());
            return firstNonEmpty(args, "path", "file", "filePath");
        } catch (JSONException e) {
            return extractJsonStringField(argsJson.trim(), "path");
        }
    }

    private static String skillNameFromPath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        if (!normalized.toLowerCase(Locale.US).endsWith("skill.md")) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        String parent = normalized.substring(0, slash);
        int parentSlash = parent.lastIndexOf('/');
        String skillDir = parentSlash >= 0 ? parent.substring(parentSlash + 1) : parent;
        return skillDir.isEmpty() ? null : skillDir;
    }

    private static boolean looksLikeSkillMarkdown(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("---") && trimmed.toLowerCase(Locale.US).contains("skill");
    }

    private static String extractYamlField(String markdown, String field) {
        String needle = field + ":";
        int start = markdown.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int lineEnd = markdown.indexOf('\n', start);
        String line = lineEnd >= 0 ? markdown.substring(start, lineEnd) : markdown.substring(start);
        int colon = line.indexOf(':');
        if (colon < 0 || colon >= line.length() - 1) {
            return null;
        }
        String value = line.substring(colon + 1).trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    private static String formatWrite(JSONObject args) {
        String path = firstNonEmpty(args, "path", "file", "filePath");
        StringBuilder sb = new StringBuilder();
        if (path != null) {
            sb.append("📝 ").append(path);
        }
        String content = args.optString("content", "");
        if (!content.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(truncate(content, 200));
        }
        if (sb.length() == 0) {
            return formatGeneric(args);
        }
        return sb.toString().trim();
    }

    private static String formatGeneric(JSONObject args) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> keys = args.keys();
        int count = 0;
        while (keys.hasNext() && count < 8) {
            String key = keys.next();
            Object value = args.opt(key);
            if (isEmptyValue(value)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(key).append(": ").append(formatValue(value));
            count++;
        }
        return sb.length() > 0 ? sb.toString() : args.toString();
    }

    private static boolean isEmptyValue(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof JSONObject) {
            return ((JSONObject) value).length() == 0;
        }
        if (value instanceof JSONArray) {
            return ((JSONArray) value).length() == 0;
        }
        return false;
    }

    private static String formatValue(Object value) {
        if (value instanceof String) {
            return truncate((String) value, 160);
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return truncate(value.toString(), 160);
        }
        return String.valueOf(value);
    }

    private static String formatPartialJson(String raw) {
        String command = extractJsonStringField(raw, "command");
        if (command == null || command.isEmpty()) {
            command = extractPartialJsonStringField(raw, "command");
        }
        if (command != null && !command.isEmpty()) {
            return "$ " + truncate(command, 300);
        }
        return truncate(raw, 300);
    }

    private static String extractJsonStringField(String raw, String field) {
        String needle = "\"" + field + "\"";
        int idx = raw.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = raw.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int startQuote = raw.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = raw.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return raw.substring(startQuote + 1);
        }
        return raw.substring(startQuote + 1, endQuote);
    }

    /** Incomplete JSON while tool-call arguments are still streaming. */
    private static String extractPartialJsonStringField(String raw, String field) {
        String needle = "\"" + field + "\"";
        int idx = raw.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = raw.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int startQuote = raw.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        String tail = raw.substring(startQuote + 1);
        int endQuote = tail.indexOf('"');
        if (endQuote >= 0) {
            return tail.substring(0, endQuote);
        }
        String trimmed = tail.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonEmpty(JSONObject args, String... keys) {
        for (String key : keys) {
            String value = args.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(label).append(": ").append(value);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }
}
