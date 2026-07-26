package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellEntity;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellPersistenceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.factory.Mappers;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class JpaRuntimeCellRepository implements RuntimeCellRepository {
  private final RuntimeCellJpaRepository cells;
  private final RuntimeCellPersistenceMapper mapper;

  public JpaRuntimeCellRepository(RuntimeCellJpaRepository cells) {
    this(cells, Mappers.getMapper(RuntimeCellPersistenceMapper.class));
  }

  public JpaRuntimeCellRepository(
      RuntimeCellJpaRepository cells, RuntimeCellPersistenceMapper mapper) {
    this.cells = java.util.Objects.requireNonNull(cells);
    this.mapper = java.util.Objects.requireNonNull(mapper);
  }

  @Override
  @Transactional
  public RuntimeCell insert(RuntimeCell cell) {
    return mapper.toDomain(cells.saveAndFlush(mapper.toEntity(cell)));
  }

  @Override
  public Optional<RuntimeCell> findByPerson(String organizationRef, String personRef) {
    return cells
        .findByOrganizationRefAndPersonRef(organizationRef, personRef)
        .map(mapper::toDomain);
  }

  @Override
  public Optional<RuntimeCell> findByCellRef(String cellRef) {
    return cells.findByCellRef(cellRef).map(mapper::toDomain);
  }

  @Override
  public Optional<RuntimeCell> findByWorkload(String issuer, String subject) {
    return cells.findByWorkloadIssuerAndWorkloadSubject(issuer, subject).map(mapper::toDomain);
  }

  @Override
  public List<RuntimeCell> findAll() {
    return cells.findAllByOrderByCellRefAsc().stream().map(mapper::toDomain).toList();
  }

  @Override
  @Transactional
  public RuntimeCell acquireLease(String cellRef, UUID leaseId, Instant now, Instant expiresAt) {
    requireLeaseWindow(now, expiresAt);
    RuntimeCellEntity cell = locked(cellRef);
    if (leaseId.equals(cell.leaseId())
        && cell.leaseExpiresAt() != null
        && cell.leaseExpiresAt().isAfter(now)) {
      return mapper.toDomain(cell);
    }
    if (cell.leaseId() != null && cell.leaseExpiresAt().isAfter(now)) {
      throw new StaleRuntimeCellException("runtime cell already has a current lease");
    }
    cell.acquireLease(leaseId, expiresAt, now);
    return mapper.toDomain(cells.saveAndFlush(cell));
  }

  @Override
  @Transactional
  public RuntimeCell renewLease(
      String cellRef, UUID leaseId, long epoch, Instant now, Instant expiresAt) {
    requireLeaseWindow(now, expiresAt);
    RuntimeCellEntity cell = locked(cellRef);
    if (!leaseId.equals(cell.leaseId())
        || cell.fencingEpoch() != epoch
        || cell.leaseExpiresAt() == null
        || !cell.leaseExpiresAt().isAfter(now)) {
      throw new StaleRuntimeCellException("runtime cell lease is missing, expired, or fenced");
    }
    cell.renewLease(expiresAt, now);
    return mapper.toDomain(cells.saveAndFlush(cell));
  }

  @Override
  @Transactional
  public RuntimeCell observe(
      String cellRef,
      UUID leaseId,
      long epoch,
      RuntimeCellState observedState,
      String auditRef,
      Instant now) {
    RuntimeCellEntity cell = locked(cellRef);
    if (!"ENTITLED".equals(cell.entitlementState())
        || !leaseId.equals(cell.leaseId())
        || cell.fencingEpoch() != epoch
        || cell.leaseExpiresAt() == null
        || !cell.leaseExpiresAt().isAfter(now)) {
      throw new StaleRuntimeCellException(
          "runtime cell observation rejected by lease, fence, or entitlement");
    }
    cell.observe(observedState.name(), auditRef, now);
    return mapper.toDomain(cells.saveAndFlush(cell));
  }

  @Override
  @Transactional
  public RuntimeCell bindEntitlement(
      String organizationRef,
      String personRef,
      long expectedVersion,
      String revision,
      String auditRef,
      Instant now) {
    RuntimeCellEntity cell = locked(organizationRef, personRef);
    if ("ENTITLED".equals(cell.entitlementState()) && revision.equals(cell.entitlementRevision()))
      return mapper.toDomain(cell);
    if (cell.version() != expectedVersion)
      throw new StaleRuntimeCellException("runtime cell entitlement changed concurrently");
    cell.bindEntitlement(revision, auditRef, now);
    return mapper.toDomain(cells.saveAndFlush(cell));
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
    if (allowedFrom == null
        || allowedFrom.isEmpty()
        || desiredState == null
        || auditRef == null
        || auditRef.isBlank()
        || now == null)
      throw new IllegalArgumentException("complete desired-state transition metadata is required");
    RuntimeCellEntity cell = locked(organizationRef, personRef);
    if (cell.desiredState().equals(desiredState.name())) return mapper.toDomain(cell);
    if (cell.version() != expectedVersion
        || !allowedFrom.contains(RuntimeCellState.valueOf(cell.desiredState()))) {
      throw new StaleRuntimeCellException(
          "runtime cell rejects the requested desired-state transition");
    }
    cell.transitionDesiredState(desiredState.name(), auditRef, now);
    return mapper.toDomain(cells.saveAndFlush(cell));
  }

  @Override
  @Transactional
  public RuntimeCell revoke(
      String organizationRef, String personRef, String revision, String auditRef, Instant now) {
    RuntimeCellEntity cell = locked(organizationRef, personRef);
    if ("REVOKED".equals(cell.entitlementState()) && revision.equals(cell.entitlementRevision()))
      return mapper.toDomain(cell);
    if ("REVOKED".equals(cell.entitlementState()))
      throw new StaleRuntimeCellException(
          "runtime cell was revoked at another entitlement revision");
    cell.revoke(revision, auditRef, now);
    return mapper.toDomain(cells.saveAndFlush(cell));
  }

  private RuntimeCellEntity locked(String ref) {
    return cells
        .findLockedByCellRef(ref)
        .orElseThrow(() -> new StaleRuntimeCellException("runtime cell does not exist"));
  }

  private RuntimeCellEntity locked(String org, String person) {
    return cells
        .findLockedByOrganizationRefAndPersonRef(org, person)
        .orElseThrow(() -> new StaleRuntimeCellException("runtime cell does not exist"));
  }

  private static void requireLeaseWindow(Instant now, Instant expiresAt) {
    if (now == null || expiresAt == null || !expiresAt.isAfter(now))
      throw new IllegalArgumentException("lease expiry must be after the current time");
  }
}
