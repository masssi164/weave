package com.massimotter.weave.backend.cicd;

import java.util.regex.Pattern;

public final class SupportSafePipelineRedactor {
    private static final Pattern[] FORBIDDEN = new Pattern[] {
            Pattern.compile("(?i)bearer\\s+[a-z0-9._-]+"),
            Pattern.compile("(?i)gh[pousr]_[a-z0-9_]{12,}"),
            Pattern.compile("(?i)(token|secret|password|private[_-]?key)\\s*[:=]\\s*[^\\s,}\\\"]+"),
            Pattern.compile("(?i)https?://[^\\s)\\\"]+"),
            Pattern.compile("(?i)ssh://[^\\s)\\\"]+")
    };

    private SupportSafePipelineRedactor() {}

    public static boolean containsUnsafeValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (Pattern pattern : FORBIDDEN) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }
}
