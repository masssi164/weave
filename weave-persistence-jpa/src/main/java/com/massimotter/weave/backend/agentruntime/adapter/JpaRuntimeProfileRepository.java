package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** JPA adapter for immutable profile payloads, signatures, and versioned activation state. */
@Repository
@Transactional(readOnly = true)
public class JpaRuntimeProfileRepository implements RuntimeProfileRepository {

    private static final RuntimeCellPersistenceMapper CELL_MAPPER =
            RuntimeCellPersistenceMapper.INSTANCE;

    private final RuntimeProfileJpaRepository profiles;
    private final RuntimeProfileSignatureJpaRepository signatures;
    private final RuntimeCellJpaRepository cells;

    public JpaRuntimeProfileRepository(
            RuntimeProfileJpaRepository profiles,
            RuntimeProfileSignatureJpaRepository signatures,
            RuntimeCellJpaRepository cells) {
        this.profiles = requireNonNull(profiles, "profiles");
        this.signatures = requireNonNull(signatures, "signatures");
        this.cells = requireNonNull(cells, "cells");
    }

    @Override
    @Transactional
    public SignedRuntimeProfile activate(
            RuntimeCell expectedCell,
            SignedRuntimeProfile profile,
            Instant now) {
        if (expectedCell == null || profile == null || now == null
                || !expectedCell.cellRef().equals(profile.cellRef())) {
            throw new IllegalArgumentException(
                    "profile activation requires its exact runtime cell");
        }
        if (now.isBefore(profile.issuedAt()) || !now.isBefore(profile.expiresAt())) {
            throw new IllegalArgumentException(
                    "only a currently valid RuntimeProfile can be activated");
        }
        RuntimeCellJpaEntity cell = cells.lockByRecordId(expectedCell.recordId())
                .orElseThrow(() -> new StaleRuntimeCellException(
                        "profile activation rejected by missing cell"));
        RuntimeProfileJpaEntity stored = profiles.findById(profile.profileHash())
                .orElseGet(() -> RuntimeProfileJpaEntity.create(
                        expectedCell,
                        profile,
                        now));
        stored.requireSamePayload(expectedCell, profile);
        RuntimeProfileSignatureId signatureId = new RuntimeProfileSignatureId(
                profile.profileHash(),
                profile.keyId());
        RuntimeProfileSignatureJpaEntity signature = signatures.findById(signatureId)
                .orElseGet(() -> RuntimeProfileSignatureJpaEntity.create(
                        signatureId,
                        profile,
                        now));
        signature.requireEquivalent(profile);
        stored.select(profile.keyId());
        profiles.saveAndFlush(stored);
        signatures.saveAndFlush(signature);
        if (!cell.hasProfile(profile.profileId(), profile.profileHash())) {
            cell.bindProfile(
                    expectedCell.version(),
                    profile.profileId(),
                    profile.profileHash(),
                    now);
        }
        cells.flush();
        return profile;
    }

    @Override
    public Optional<SignedRuntimeProfile> findCurrentForWorkload(
            String profileHash,
            String workloadIssuer,
            String workloadSubject,
            String workloadClientId,
            Instant now) {
        return profiles.findCurrentForWorkload(
                        profileHash,
                        workloadIssuer,
                        workloadSubject,
                        workloadClientId,
                        RuntimePersistenceTime.utc(now),
                        RuntimeEntitlementState.ENTITLED)
                .flatMap(profile -> signatures.findById(
                                new RuntimeProfileSignatureId(
                                        profile.profileHash(),
                                        profile.selectedKeyId()))
                        .map(signature -> profile.toDomain(signature)));
    }

    @Override
    @Transactional
    public void revokeCurrent(
            String cellRef,
            String revocationCode,
            Instant now) {
        if (cellRef == null || cellRef.isBlank()
                || revocationCode == null || revocationCode.isBlank()
                || now == null) {
            throw new IllegalArgumentException(
                    "profile revocation metadata is required");
        }
        cells.lockByCellRef(cellRef)
                .flatMap(cell -> profiles.findByCellRefAndProfileHash(
                        cellRef,
                        CELL_MAPPER.toDomain(cell).runtimeProfileHash()))
                .ifPresent(profile -> profile.revoke(revocationCode, now));
    }
}
