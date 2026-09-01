package com.polaris.bpm.service.support;

import java.util.Locale;

/** Common, side-effect free normalization rules for workflow commands. */
public final class WorkflowPayload {
    private WorkflowPayload() {
    }

    public static String nullable(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    public static String text(Object primary, Object secondary, String fallback) {
        String value = nullable(primary);
        if (value != null) {
            return value;
        }
        value = nullable(secondary);
        return value == null ? fallback : value;
    }

    public static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static String normalizedType(Object direct, Object process, String key, String fallback) {
        String value = nullable(direct);
        if (value == null && process instanceof java.util.Map<?, ?> map) {
            value = nullable(map.get(key));
        }
        return value == null ? fallback : value.toUpperCase(Locale.ROOT);
    }

    public static String processCode(Object primary, Object secondary) {
        String raw = text(primary, secondary, "approval_" + System.currentTimeMillis())
                .replaceAll("[^A-Za-z0-9_]", "_");
        return raw.isBlank() || Character.isDigit(raw.charAt(0)) ? "process_" + raw : raw;
    }

    public static String bpmnId(Object value) {
        String raw = nullable(value);
        if (raw == null) {
            return "node_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }
        String normalized = raw.replaceAll("[^A-Za-z0-9_]", "_");
        return normalized.isBlank()
                ? "node_" + java.util.UUID.randomUUID().toString().replace("-", "")
                : normalized;
    }

    public static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
