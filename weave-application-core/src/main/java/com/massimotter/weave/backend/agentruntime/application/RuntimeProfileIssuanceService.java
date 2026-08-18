package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** Builds, signs, and atomically binds one short-lived server-owned RuntimeProfile. */
public final class RuntimeProfileIssuanceService {
  private final RuntimePolicyAuthority policy;
  private final RuntimeProfileSigner signer;
  private final RuntimeProfileRepository profiles;
  private final Clock clock;

  public RuntimeProfileIssuanceService(
      RuntimePolicyAuthority policy,
      RuntimeProfileSigner signer,
      RuntimeProfileRepository profiles,
      Clock clock) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.signer = Objects.requireNonNull(signer, "signer");
    this.profiles = Objects.requireNonNull(profiles, "profiles");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public SignedRuntimeProfile issue(
      RuntimeCell expectedCell, String issuanceRef, Instant issuedAt) {
    Objects.requireNonNull(expectedCell, "expectedCell");
    requireText(issuanceRef, "issuanceRef");
    Objects.requireNonNull(issuedAt, "issuedAt");
    if (expectedCell.entitlementState() != RuntimeEntitlementState.ENTITLED) {
      throw new IllegalStateException(
          "A RuntimeProfile cannot be issued for an inactive entitlement");
    }
    Instant expiresAt = issuedAt.plus(policy.profileTtl());
    Instant now = clock.instant();
    if (now.isBefore(issuedAt) || !now.isBefore(expiresAt)) {
      throw new IllegalStateException(
          "The idempotent RuntimeProfile issuance window has expired or is not yet active");
    }
    String profileId = profileId(expectedCell, issuanceRef);
    RuntimeProfile profile = policy.runtimeProfile(expectedCell, profileId, issuedAt, expiresAt);
    SignedRuntimeProfile signed = signer.sign(profile);
    return profiles.activate(expectedCell, signed, now);
  }

  private static String profileId(RuntimeCell cell, String issuanceRef) {
    String semantics =
        "weave.runtime-profile-id/v2\u0000"
            + cell.organizationRef()
            + "\u0000"
            + cell.personRef()
            + "\u0000"
            + cell.cellRef()
            + "\u0000"
            + cell.entitlementRevision()
            + "\u0000"
            + cell.workspaceRevision()
            + "\u0000"
            + issuanceRef;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(semantics.getBytes(StandardCharsets.UTF_8));
      return "rp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 512) {
      throw new IllegalArgumentException(field + " must contain between 1 and 512 characters");
    }
  }
}
