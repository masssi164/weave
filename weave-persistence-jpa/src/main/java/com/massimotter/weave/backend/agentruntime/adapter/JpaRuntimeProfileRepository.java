package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class JpaRuntimeProfileRepository implements RuntimeProfileRepository {
  private final RuntimeProfileJpaRepository profiles;
  private final RuntimeProfileSignatureJpaRepository signatures;
  private final RuntimeCellJpaRepository cells;

  public JpaRuntimeProfileRepository(
      RuntimeProfileJpaRepository p,
      RuntimeProfileSignatureJpaRepository s,
      RuntimeCellJpaRepository c) {
    profiles = Objects.requireNonNull(p);
    signatures = Objects.requireNonNull(s);
    cells = Objects.requireNonNull(c);
  }

  @Override
  @Transactional
  public SignedRuntimeProfile activate(
      RuntimeCell expected, SignedRuntimeProfile profile, Instant now) {
    if (expected == null
        || profile == null
        || now == null
        || !expected.cellRef().equals(profile.cellRef()))
      throw new IllegalArgumentException("profile activation requires its exact runtime cell");
    if (now.isBefore(profile.issuedAt()) || !now.isBefore(profile.expiresAt()))
      throw new IllegalArgumentException("only a currently valid RuntimeProfile can be activated");
    RuntimeCellEntity cell =
        cells
            .findLockedByCellRef(expected.cellRef())
            .orElseThrow(
                () -> new StaleRuntimeCellException("profile activation rejected by missing cell"));
    RuntimeProfileEntity stored =
        profiles
            .findById(profile.profileHash())
            .orElseGet(
                () ->
                    new RuntimeProfileEntity(
                        profile.profileHash(),
                        profile.profileId(),
                        profile.cellRef(),
                        expected.organizationRef(),
                        expected.personRef(),
                        profile.payload(),
                        profile.keyId(),
                        profile.issuedAt(),
                        profile.expiresAt(),
                        now));
    requireSame(stored, expected, profile);
    RuntimeProfileSignatureId sid =
        new RuntimeProfileSignatureId(profile.profileHash(), profile.keyId());
    RuntimeProfileSignatureEntity signature =
        signatures
            .findById(sid)
            .orElseGet(
                () ->
                    new RuntimeProfileSignatureEntity(
                        profile.profileHash(),
                        profile.keyId(),
                        profile.protectedHeader(),
                        profile.signature(),
                        now));
    if (!signature.protectedHeader().equals(profile.protectedHeader())
        || !signature.signature().equals(profile.signature()))
      throw new IllegalStateException(
          "RuntimeProfile key id is already bound to another signature");
    stored.select(profile.keyId());
    profiles.saveAndFlush(stored);
    signatures.saveAndFlush(signature);
    if (!Objects.equals(cell.runtimeProfileId(), profile.profileId())
        || !Objects.equals(cell.runtimeProfileHash(), profile.profileHash())) {
      if (cell.version() != expected.version() || !"ENTITLED".equals(cell.entitlementState()))
        throw new StaleRuntimeCellException(
            "profile activation rejected by stale cell or entitlement");
      cell.activateProfile(profile.profileId(), profile.profileHash(), now);
      cells.saveAndFlush(cell);
    }
    return profile;
  }

  @Override
  public Optional<SignedRuntimeProfile> findCurrentForWorkload(
      String hash, String issuer, String subject, String client, Instant now) {
    return profiles
        .findCurrentForWorkload(hash, issuer, subject, client, now)
        .flatMap(
            p ->
                signatures
                    .findById(new RuntimeProfileSignatureId(p.profileHash(), p.selectedKeyId()))
                    .map(
                        s ->
                            new SignedRuntimeProfile(
                                s.protectedHeader(),
                                p.payload(),
                                s.signature(),
                                p.profileHash(),
                                p.profileId(),
                                p.cellRef(),
                                s.keyId(),
                                p.issuedAt(),
                                p.expiresAt())));
  }

  @Override
  @Transactional
  public void revokeCurrent(String cellRef, String code, Instant now) {
    if (cellRef == null || cellRef.isBlank() || code == null || code.isBlank() || now == null)
      throw new IllegalArgumentException("profile revocation metadata is required");
    cells
        .findLockedByCellRef(cellRef)
        .flatMap(c -> profiles.findByCellRefAndProfileHash(cellRef, c.runtimeProfileHash()))
        .ifPresent(p -> p.revoke(code, now));
  }

  private static void requireSame(RuntimeProfileEntity p, RuntimeCell c, SignedRuntimeProfile s) {
    if (!p.profileHash().equals(s.profileHash())
        || !p.profileId().equals(s.profileId())
        || !p.cellRef().equals(s.cellRef())
        || !p.organizationRef().equals(c.organizationRef())
        || !p.personRef().equals(c.personRef())
        || !p.payload().equals(s.payload())
        || !p.issuedAt().equals(s.issuedAt())
        || !p.expiresAt().equals(s.expiresAt()))
      throw new IllegalStateException(
          "RuntimeProfile hash or id is already bound to other semantics");
    if (p.revokedAt() != null)
      throw new IllegalStateException("revoked RuntimeProfile cannot select a signing key");
  }
}
