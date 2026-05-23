package com.massimotter.weave.backend.provider;

import java.util.Map;
import java.util.regex.Pattern;

public final class ProviderRedactor {

    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(authorization|token|access_token|refresh_token|secret|password|app[-_ ]?password|api[-_ ]?key|cookie)=((?:Bearer\\s+)?[^\\s&;,]+)");
    private static final Pattern URL_WITH_QUERY = Pattern.compile("(https?)://([^\\s/?#]+)(/[^\\s?#]*)?[?][^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDENTIAL_URL = Pattern.compile("(https?)://([^:/\\s]+):([^@/\\s]+)@([^\\s]+)", Pattern.CASE_INSENSITIVE);

    private ProviderRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "[redacted-empty]";
        }
        String redacted = CREDENTIAL_URL.matcher(value).replaceAll("$1://[redacted-credentials]@$4");
        redacted = URL_WITH_QUERY.matcher(redacted).replaceAll("$1://$2$3?[redacted]");
        redacted = KEY_VALUE_SECRET.matcher(redacted).replaceAll("$1=[redacted]");
        redacted = BEARER.matcher(redacted).replaceAll("Bearer [redacted]");
        return redacted;
    }

    public static Map<String, Object> supportSafeDetails(String module, String operation, String rawReason) {
        return Map.of(
                "module", module,
                "operation", operation,
                "supportSafe", true,
                "reason", safeReason(rawReason));
    }

    private static String safeReason(String rawReason) {
        return redact(rawReason);
    }
}
