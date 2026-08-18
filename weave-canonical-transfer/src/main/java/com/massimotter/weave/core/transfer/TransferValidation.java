package com.massimotter.weave.core.transfer;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class TransferValidation {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private TransferValidation() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String requireSha256(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    static <T> T require(T value, String field) {
        return Objects.requireNonNull(value, field + " must not be null");
    }
}
