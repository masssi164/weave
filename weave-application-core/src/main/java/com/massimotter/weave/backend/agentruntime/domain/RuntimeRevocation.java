package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable revocation evidence. Provider convergence may follow, but this fact never disappears.
 */
public record RuntimeRevocation(
    UUID recordId,
    String revocationRef,
    String organizationRef,
    String personRef,
    String reasonCode,
    String reasonRefHash,
    String actorRefHash,
    Instant effectiveAt,
    String entitlementRef,
    String entitlementRevision,
    String cellRef,
    String profileHash,
    String workloadRefHash,
    String auditCorrelationRef,
    Instant createdAt) {

  public RuntimeRevocation {
    Objects.requireNonNull(recordId, "recordId");
    requirePrefix(revocationRef, "revocation:", "revocationRef");
    requireText(organizationRef, "organizationRef", 255);
    requireText(personRef, "personRef", 255);
    if (reasonCode == null || !reasonCode.matches("[a-z0-9][a-z0-9-]{1,98}[a-z0-9]")) {
      throw new IllegalArgumentException("reasonCode must be a bounded machine-readable code");
    }
    requireFingerprint(reasonRefHash, "reasonRefHash");
    requireFingerprint(actorRefHash, "actorRefHash");
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    requirePrefix(entitlementRef, "entitlement:", "entitlementRef");
    requireFingerprint(entitlementRevision, "entitlementRevision");
    if (cellRef == null || !cellRef.startsWith("cell:") || cellRef.length() > 255) {
      throw new IllegalArgumentException("cellRef has an invalid format");
    }
    if (profileHash != null) {
      requireFingerprint(profileHash, "profileHash");
    }
    requireFingerprint(workloadRefHash, "workloadRefHash");
    requirePrefix(auditCorrelationRef, "correlation:", "auditCorrelationRef");
    Objects.requireNonNull(createdAt, "createdAt");
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
