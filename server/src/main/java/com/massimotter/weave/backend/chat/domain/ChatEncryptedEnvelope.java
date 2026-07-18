package com.massimotter.weave.backend.chat.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChatEncryptedEnvelope(Map<String, Object> content) {

    public static final String MEGOLM_V1 = "m.megolm.v1.aes-sha2";

    public ChatEncryptedEnvelope {
        content = content == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(content));
        requireText(content, "algorithm", 128);
        if (!MEGOLM_V1.equals(content.get("algorithm"))) {
            throw new IllegalArgumentException("encrypted Chat algorithm is unsupported");
        }
        requireText(content, "ciphertext", 262_144);
        requireText(content, "session_id", 512);
        optionalText(content, "sender_key", 512);
        optionalText(content, "device_id", 128);
        validateJsonValue(content, 0);
    }

    public String algorithm() {
        return (String) content.get("algorithm");
    }

    private static void requireText(Map<String, Object> value, String field, int maxLength) {
        Object raw = value.get(field);
        if (!(raw instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("encrypted Chat " + field + " is invalid");
        }
    }

    private static void optionalText(Map<String, Object> value, String field, int maxLength) {
        if (!value.containsKey(field)) {
            return;
        }
        requireText(value, field, maxLength);
    }

    private static void validateJsonValue(Object value, int depth) {
        if (depth > 12) {
            throw new IllegalArgumentException("encrypted Chat envelope nesting is too deep");
        }
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return;
        }
        if (value instanceof String text) {
            if (text.length() > 262_144) {
                throw new IllegalArgumentException("encrypted Chat envelope value is too large");
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 128) {
                throw new IllegalArgumentException("encrypted Chat envelope has too many fields");
            }
            map.forEach((key, nested) -> {
                if (!(key instanceof String text) || text.isBlank() || text.length() > 256) {
                    throw new IllegalArgumentException("encrypted Chat envelope key is invalid");
                }
                validateJsonValue(nested, depth + 1);
            });
            return;
        }
        if (value instanceof Iterable<?> values) {
            int count = 0;
            for (Object nested : values) {
                if (++count > 256) {
                    throw new IllegalArgumentException("encrypted Chat envelope array is too large");
                }
                validateJsonValue(nested, depth + 1);
            }
            return;
        }
        throw new IllegalArgumentException("encrypted Chat envelope contains an unsupported value");
    }
}
