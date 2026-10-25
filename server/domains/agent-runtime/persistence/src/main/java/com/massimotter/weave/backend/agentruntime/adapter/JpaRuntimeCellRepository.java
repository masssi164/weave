package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** JPA control-store adapter for runtime-cell state, lease, and fencing authority. */
@Repository
@Transactional(readOnly = true)
public class JpaRuntimeCellRepository implements RuntimeCellRepository {

    private static final RuntimeCellPersistenceMapper MAPPER =
            RuntimeCellPersistenceMapper.INSTANCE;

    private final RuntimeCellJpaRepository cells;

    public JpaRuntimeCellRepository(RuntimeCellJpaRepository cells) {
        this.cells = requireNonNull(cells, "cells");
    }

    @Override
    @Transactional
    public RuntimeCell insert(RuntimeCell cell) {
        return MAPPER.toDomain(cells.saveAndFlush(MAPPER.toEntity(cell)));
    }

    @Override
    public Optional<RuntimeCell> findByPerson(
            String organizationRef,
            String personRef) {
        return cells.findByOrganizationRefAndPersonRef(organizationRef, personRef)
                .map(MAPPER::toDomain);
    }

    @Override
    public Optional<RuntimeCell> findByCellRef(String cellRef) {
        return cells.findByCellRef(cellRef).map(MAPPER::toDomain);
    }

    @Override
    public Optional<RuntimeCell> findByWorkload(
            String workloadIssuer,
            String workloadSubject) {
        return cells
                .findByWorkloadIssuerAndWorkloadSubject(
                        workloadIssuer,
                        workloadSubject)
                .map(MAPPER::toDomain);
    }

    @Override
    public List<RuntimeCell> findAll() {
        return cells.findAllByOrderByCellRef().stream()
                .map(MAPPER::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public RuntimeCell acquireLease(
            String cellRef,
            UUID leaseId,
            Instant now,
            Instant expiresAt) {
        requireLeaseWindow(now, expiresAt);
        RuntimeCellJpaEntity cell = lock(cellRef);
        if (cell.sameCurrentLease(leaseId, now)) {
            return MAPPER.toDomain(cell);
        }
        if (!cell.leaseAvailable(now)) {
            throw new StaleRuntimeCellException(
                    "runtime cell already has a current lease");
        }
        cell.acquireLease(leaseId, now, expiresAt);
        return MAPPER.toDomain(cells.saveAndFlush(cell));
    }

    @Override
    @Transactional
    public RuntimeCell renewLease(
            String cellRef,
            UUID leaseId,
            long fencingEpoch,
            Instant now,
            Instant expiresAt) {
        requireLeaseWindow(now, expiresAt);
        RuntimeCellJpaEntity cell = lock(cellRef);
        if (!cell.renewLease(leaseId, fencingEpoch, now, expiresAt)) {
            throw new StaleRuntimeCellException(
                    "runtime cell lease is missing, expired, or fenced");
        }
        return MAPPER.toDomain(cells.saveAndFlush(cell));
    }

    @Override
    @Transactional
    public RuntimeCell observe(
            String cellRef,
            UUID leaseId,
            long fencingEpoch,
            RuntimeCellState observedState,
            String auditRef,
            Instant now) {
        RuntimeCellJpaEntity cell = lock(cellRef);
        if (!cell.observe(
                leaseId,
                fencingEpoch,
                observedState,
                auditRef,
                now)) {
            throw new StaleRuntimeCellException(
                    "runtime cell observation rejected by lease, fence, or entitlement");
        }
        return MAPPER.toDomain(cells.saveAndFlush(cell));
    }

    @Override
    @Transactional
    public RuntimeCell bindEntitlement(
            String organizationRef,
            String personRef,
            long expectedVersion,
            String entitlementRevision,
            String auditRef,
            Instant now) {
        RuntimeCellJpaEntity cell = lockByPerson(organizationRef, personRef);
        if (cell.sameEntitlement(entitlementRevision)) {
            return MAPPER.toDomain(cell);
        }
        if (cell.version() != expectedVersion) {
            throw new StaleRuntimeCellException(
                    "runtime cell entitlement changed concurrently");
        }
        cell.bindEntitlement(entitlementRevision, auditRef, now);
        return MAPPER.toDomain(cells.saveAndFlush(cell));
    }

    @Override
    @Transactional
    public RuntimeCell transitionDesiredState(
            String organizationRef,
            String personRef,
            long expectedVersion,
            Set<RuntimeCellState> allowedFrom,
            RuntimeCellState desiredState,
            String auditRef,
            Instant now) {
        if (allowedFrom == null || allowedFrom.isEmpty() || desiredState == null
                || auditRef == null || auditRef.isBlank() || now == null) {
            throw new IllegalArgumentException(
                    "complete desired-state transition metadata is required");
        }
        RuntimeCellJpaEntity cell = lockByPerson(organizationRef, personRef);
        if (cell.desiredState() == desiredState) {
            return MAPPER.toDomain(cell);
        }
        if (cell.version() != expectedVersion
                || !allowedFrom.contains(cell.desiredState())) {
            throw new StaleRuntimeCellException(
                    "runtime cell rejects the requested desired-state transition");
        }
        cell.transitionDesiredState(desiredState, auditRef, now);
        return MAPPER.toDomain(cells.saveAndFlush(cell));
    }

    @Override
    @Transactional
    public RuntimeCell revoke(
            String organizationRef,
            String personRef,
            String entitlementRevision,
            String auditRef,
            Instant now) {
        RuntimeCellJpaEntity cell = lockByPerson(organizationRef, personRef);
        if (cell.sameRevocation(entitlementRevision)) {
            return MAPPER.toDomain(cell);
        }
        if (cell.entitlementState() == RuntimeEntitlementState.REVOKED) {
            throw new StaleRuntimeCellException(
                    "runtime cell was revoked at another entitlement revision");
        }
        cell.revoke(entitlementRevision, auditRef, now);
        return MAPPER.toDomain(cells.saveAndFlush(cell));
    }

    private RuntimeCellJpaEntity lock(String cellRef) {
        return cells.lockByCellRef(cellRef)
                .orElseThrow(() -> new StaleRuntimeCellException(
                        "runtime cell does not exist"));
    }

    private RuntimeCellJpaEntity lockByPerson(
            String organizationRef,
            String personRef) {
        return cells.lockByPerson(organizationRef, personRef)
                .orElseThrow(() -> new StaleRuntimeCellException(
                        "runtime cell does not exist"));
    }

    private static void requireLeaseWindow(Instant now, Instant expiresAt) {
        if (now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "lease expiry must be after the current time");
        }
    }
}
