package com.massimotter.weave.backend.portability;

public record ProviderReadiness(boolean available, String supportSafeCode) {

    public ProviderReadiness {
        supportSafeCode = supportSafeCode == null || supportSafeCode.isBlank()
                ? available ? "provider-ready" : "provider-degraded"
                : supportSafeCode.trim();
    }

    public static ProviderReadiness ready(String code) {
        return new ProviderReadiness(true, code);
    }

    public static ProviderReadiness degraded(String code) {
        return new ProviderReadiness(false, code);
    }
}
