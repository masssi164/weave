package com.massimotter.weave.backend.portability;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

public record ProviderCapabilityProbeResult(
        ProviderCapabilityState state,
        String supportSafeCode,
        Duration retryAfter) {

    private static final Pattern SUPPORT_SAFE_CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,79}");

    public ProviderCapabilityProbeResult {
        state = state == null ? ProviderCapabilityState.UNAVAILABLE : state;
        supportSafeCode = sanitizeCode(supportSafeCode, state);
        retryAfter = retryAfter == null || retryAfter.isNegative() ? null : retryAfter;
    }

    public static ProviderCapabilityProbeResult available(String code) {
        return new ProviderCapabilityProbeResult(ProviderCapabilityState.AVAILABLE, code, null);
    }

    public static ProviderCapabilityProbeResult degraded(String code) {
        return degraded(code, null);
    }

    public static ProviderCapabilityProbeResult degraded(String code, Duration retryAfter) {
        return new ProviderCapabilityProbeResult(ProviderCapabilityState.DEGRADED, code, retryAfter);
    }

    public static ProviderCapabilityProbeResult unavailable(String code) {
        return new ProviderCapabilityProbeResult(ProviderCapabilityState.UNAVAILABLE, code, null);
    }

    private static String sanitizeCode(String code, ProviderCapabilityState state) {
        if (code != null) {
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            if (SUPPORT_SAFE_CODE.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return switch (state) {
            case AVAILABLE -> "provider-capability-available";
            case DEGRADED -> "provider-capability-degraded";
            case UNAVAILABLE -> "provider-capability-unavailable";
        };
    }
}
