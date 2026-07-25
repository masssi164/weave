package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persisted Keycloak-derived authority fact; request payloads can never mint this object. */
public record RuntimeEntitlementRef(
        UUID recordId,
        String entitlementRef,
        String entitlementRevision,
        String organizationRef,
        String personRef,
        RuntimeMemberBinding memberBinding,
        String sourceProvider,
        String sourceGroupRef,
        String capabilityRevision,
        RuntimeEntitlementState state,
        Instant effectiveAt,
        Instant lastObservedAt,
        Instant expiresAt,
        String revocationRef,
        Instant revokedAt,
        String auditRef,
        Instant createdAt,
        Instant updatedAt) {

    public RuntimeEntitlementRef {
        Objects.requireNonNull(recordId, "recordId");
        requirePrefix(entitlementRef, "entitlement:", "entitlementRef");
        requireFingerprint(entitlementRevision, "entitlementRevision");
        requireText(organizationRef, "organizationRef", 255);
        requireText(personRef, "personRef", 255);
        Objects.requireNonNull(memberBinding, "memberBinding");
        requireText(sourceProvider, "sourceProvider", 64);
        requireFingerprint(sourceGroupRef, "sourceGroupRef");
        requireFingerprint(capabilityRevision, "capabilityRevision");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (lastObservedAt.isBefore(effectiveAt) || !expiresAt.isAfter(lastObservedAt)) {
            throw new IllegalArgumentException("entitlement observation times are inconsistent");
        }
        if (state == RuntimeEntitlementState.ENTITLED && (revocationRef != null || revokedAt != null)) {
            throw new IllegalArgumentException("an entitled fact cannot carry revocation metadata");
        }
        if (state == RuntimeEntitlementState.REVOKED) {
            requirePrefix(revocationRef, "revocation:", "revocationRef");
            Objects.requireNonNull(revokedAt, "revokedAt");
            if (revokedAt.isBefore(effectiveAt)) {
                throw new IllegalArgumentException("revocation cannot predate entitlement");
            }
        } else if (state != RuntimeEntitlementState.ENTITLED) {
            throw new IllegalArgumentException("persisted entitlement facts are entitled or revoked");
        }
        requireText(auditRef, "auditRef", 255);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    public boolean effectiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return state == RuntimeEntitlementState.ENTITLED
                && !now.isBefore(effectiveAt)
                && now.isBefore(expiresAt);
    }

    private static void requireFingerprint(String value, String field) {
        if (value == null || !value.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 reference");
        }
    }

    private static void requirePrefix(String value, String prefix, String field) {
        if (value == null || !value.matches(java.util.regex.Pattern.quote(prefix) + "[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
    }

    private static void requireText(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is required and bounded");
        }
    }
}
