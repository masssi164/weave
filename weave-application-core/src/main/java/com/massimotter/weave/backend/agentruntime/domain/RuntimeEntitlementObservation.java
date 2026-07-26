package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.Objects;

/** A short-lived observation made directly against the configured IDM authority. */
public record RuntimeEntitlementObservation(
    String organizationRef,
    String personRef,
    RuntimeMemberBinding memberBinding,
    String sourceProvider,
    String sourceGroupRef,
    String capabilityRevision,
    Instant observedAt,
    Instant expiresAt) {

  public RuntimeEntitlementObservation {
    requireText(organizationRef, "organizationRef");
    requireText(personRef, "personRef");
    Objects.requireNonNull(memberBinding, "memberBinding");
    requireText(sourceProvider, "sourceProvider");
    requireFingerprint(sourceGroupRef, "sourceGroupRef");
    requireFingerprint(capabilityRevision, "capabilityRevision");
    Objects.requireNonNull(observedAt, "observedAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (!expiresAt.isAfter(observedAt)) {
      throw new IllegalArgumentException("entitlement observation expiry must follow observation");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 500) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
  }

  private static void requireFingerprint(String value, String field) {
    if (value == null || !value.matches("sha256:[a-f0-9]{64}")) {
      throw new IllegalArgumentException(field + " must be a SHA-256 reference");
    }
  }
}
