package com.massimotter.weave.backend.audit;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class AuditPayloadRedactor {

    private static final String REDACTED = "[redacted]";
    private static final String PROVIDER_ERROR_REDACTED = "[redacted:provider-error]";

    private AuditPayloadRedactor() {
    }

    static Map<String, Object> redact(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> redactValue(entry.getKey(), entry.getValue())));
    }

    private static Object redactValue(String key, Object value) {
        if (sensitiveKey(key)) {
            return rawProviderErrorKey(key) ? PROVIDER_ERROR_REDACTED : REDACTED;
        }
        if (value instanceof String stringValue && sensitiveStringValue(stringValue)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return redactNestedMap(mapValue);
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(AuditPayloadRedactor::redactNestedValue).toList();
        }
        return value;
    }

    private static Object redactNestedValue(Object value) {
        if (value instanceof String stringValue && sensitiveStringValue(stringValue)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return redactNestedMap(mapValue);
        }
        return value;
    }

    private static Map<String, Object> redactNestedMap(Map<?, ?> mapValue) {
        return mapValue.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> String.valueOf(entry.getKey()),
                        entry -> redactValue(String.valueOf(entry.getKey()), entry.getValue())));
    }

    private static boolean sensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || rawProviderErrorKey(key);
    }

    private static boolean rawProviderErrorKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("rawprovidererror");
    }

    private static boolean sensitiveStringValue(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).stripLeading();
        return normalized.startsWith("bearer ") || normalized.startsWith("sk-") || normalized.startsWith("xox");
    }
}
