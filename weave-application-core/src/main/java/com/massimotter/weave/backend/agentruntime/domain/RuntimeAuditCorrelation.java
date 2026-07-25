package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Support-safe one-way links across runtime and collaboration authority boundaries. */
public record RuntimeAuditCorrelation(
        UUID recordId,
        String correlationRef,
        String organizationRefHash,
        String personRefHash,
        String keycloakRefHash,
        String orchestratorRefHash,
        String openClawRefHash,
        String matrixRefHash,
        String mcpRefHash,
        String domainAuditRefHash,
        Instant occurredAt,
        Instant createdAt) {

    public RuntimeAuditCorrelation {
        Objects.requireNonNull(recordId, "recordId");
        requirePrefix(correlationRef, "correlation:");
        requireFingerprint(organizationRefHash);
        requireFingerprint(personRefHash);
        Stream.of(keycloakRefHash, orchestratorRefHash, openClawRefHash, matrixRefHash, mcpRefHash,
                        domainAuditRefHash)
                .filter(Objects::nonNull).forEach(RuntimeAuditCorrelation::requireFingerprint);
        if (keycloakRefHash == null && orchestratorRefHash == null && openClawRefHash == null
                && matrixRefHash == null && mcpRefHash == null && domainAuditRefHash == null) {
            throw new IllegalArgumentException("at least one correlated boundary reference is required");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static void requireFingerprint(String value) {
        if (value == null || !value.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("audit correlation values must be SHA-256 references");
        }
    }

    private static void requirePrefix(String value, String prefix) {
        if (value == null || !value.matches(java.util.regex.Pattern.quote(prefix) + "[a-f0-9]{64}")) {
            throw new IllegalArgumentException("audit correlation reference has an invalid format");
        }
    }
}
