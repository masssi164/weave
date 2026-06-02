package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;

@Schema(description = "Evidence-backed implementation maturity for provider candidates. Contract-only providers are never member-available.")
public enum ProviderRealityLevel {
    CONTRACT_ONLY("contract_only", 0),
    CONFIGURED("configured", 10),
    LIVE_READ("live_read", 20),
    LIVE_WRITE("live_write", 30),
    MIGRATION_DRY_RUN("migration_dry_run", 35),
    MIGRATION_APPLY_READY("migration_apply_ready", 40),
    ROLLBACK_READY("rollback_ready", 45),
    RELEASE_READY("release_ready", 50);

    private static final Comparator<ProviderRealityLevel> PRIORITY_COMPARATOR = Comparator.comparingInt(ProviderRealityLevel::priority);

    private final String value;
    private final int priority;

    ProviderRealityLevel(String value, int priority) {
        this.value = value;
        this.priority = priority;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public int priority() {
        return priority;
    }

    public static Comparator<ProviderRealityLevel> priorityComparator() {
        return PRIORITY_COMPARATOR;
    }

    public boolean canBeMemberAvailable() {
        return this == RELEASE_READY;
    }
}
