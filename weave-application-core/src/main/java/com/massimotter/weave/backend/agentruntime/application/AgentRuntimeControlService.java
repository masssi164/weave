package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeAuditCorrelation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementReconciler;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class AgentRuntimeControlService implements RuntimeEntitlementReconciler {
  private static final String PROVISION = "PROVISION";
  private static final String REVOKE = "REVOKE";

  private final RuntimeCellRepository cells;
  private final RuntimeCommandRepository commands;
  private final RuntimeProfileRepository profiles;
  private final RuntimeWorkloadIdentityAdmin workloadIdentityAdmin;
  private final RuntimeEntitlementAuthority entitlementAuthority;
  private final RuntimeGovernanceRepository governance;
  private final Clock clock;

  public AgentRuntimeControlService(
      RuntimeCellRepository cells,
      RuntimeCommandRepository commands,
      RuntimeProfileRepository profiles,
      RuntimeWorkloadIdentityAdmin workloadIdentityAdmin,
      RuntimeEntitlementAuthority entitlementAuthority,
      RuntimeGovernanceRepository governance,
      Clock clock) {
    this.cells = Objects.requireNonNull(cells, "cells");
    this.commands = Objects.requireNonNull(commands, "commands");
    this.profiles = Objects.requireNonNull(profiles, "profiles");
    this.workloadIdentityAdmin =
        Objects.requireNonNull(workloadIdentityAdmin, "workloadIdentityAdmin");
    this.entitlementAuthority =
        Objects.requireNonNull(entitlementAuthority, "entitlementAuthority");
    this.governance = Objects.requireNonNull(governance, "governance");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public RuntimeCell provision(ProvisionRuntimeCommand command) {
    Instant now = clock.instant();
    String cellKey = stableCellKey(command.organizationRef(), command.memberBinding());
    String cellRef = "cell:" + cellKey;
    RuntimeCell existing =
        cells.findByPerson(command.organizationRef(), command.personRef()).orElse(null);
    if (existing != null) {
      requireSameBinding(existing, cellRef, command);
    }
    RuntimeAuditCorrelation attemptCorrelation =
        appendCorrelation(
            PROVISION,
            command.organizationRef(),
            command.personRef(),
            cellRef,
            command.idempotencyKey(),
            command.auditRef(),
            now);
    RuntimeCommandReceipt receipt =
        commands.claim(
            command.organizationRef(),
            command.personRef(),
            command.idempotencyKey(),
            provisionCommand(command),
            cellRef,
            attemptCorrelation.correlationRef(),
            now);
    String correlationRef = receipt.auditRef();
    if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
      RuntimeCell replay =
          cells
              .findByPerson(command.organizationRef(), command.personRef())
              .orElseThrow(
                  () ->
                      new RuntimeCommandConflictException(
                          "the completed provisioning receipt has no runtime cell"));
      requireSameBinding(replay, receipt, command);
      return replay;
    }
    RuntimeEntitlementRef entitlement;
    RuntimeEntitlementRef previousEntitlement =
        existing == null
            ? null
            : governance.findCurrent(command.organizationRef(), command.personRef()).orElse(null);
    try {
      RuntimeEntitlementObservation observation =
          entitlementAuthority.observe(
              new RuntimeEntitlementAuthority.ObserveEntitlementCommand(
                  command.organizationRef(),
                  command.personRef(),
                  command.memberBinding(),
                  correlationRef));
      entitlement = governance.activate(observation, command.idempotencyKey(), correlationRef, now);
    } catch (RuntimeException failure) {
      commands.fail(receipt, "runtime-entitlement-unavailable-or-denied", clock.instant());
      throw failure;
    }
    if (existing != null) {
      requireSameBinding(existing, receipt, command);
      try {
        RuntimeCell current = existing;
        if (existing.entitlementState() != RuntimeEntitlementState.ENTITLED
            || !existing.entitlementRevision().equals(entitlement.entitlementRevision())) {
          if (existing.entitlementState() == RuntimeEntitlementState.ENTITLED) {
            if (previousEntitlement == null
                || previousEntitlement.state() != RuntimeEntitlementState.ENTITLED
                || !previousEntitlement
                    .entitlementRevision()
                    .equals(existing.entitlementRevision())) {
              throw new RuntimeCommandConflictException(
                  "the active runtime cell has no matching authoritative entitlement fact");
            }
            revokeEntitlementFact(
                existing,
                previousEntitlement,
                "capability-policy-superseded",
                correlationRef,
                now,
                true);
          }
          profiles.revokeCurrent(existing.cellRef(), "entitlement-superseded", now);
          current =
              cells.bindEntitlement(
                  command.organizationRef(),
                  command.personRef(),
                  existing.version(),
                  entitlement.entitlementRevision(),
                  correlationRef,
                  now);
          RuntimeWorkloadBinding workload =
              workloadIdentityAdmin.ensureBinding(
                  new RuntimeWorkloadIdentityAdmin.EnsureBindingCommand(
                      command.organizationRef(),
                      command.personRef(),
                      cellRef,
                      current.workloadBinding().clientId(),
                      command.authenticationMethod(),
                      correlationRef));
          if (!workload.equals(current.workloadBinding())) {
            throw new RuntimeCommandConflictException(
                "the immutable workload identity changed during re-entitlement");
          }
        }
        commands.complete(receipt, current.version(), clock.instant());
        return current;
      } catch (RuntimeException failure) {
        commands.fail(receipt, "runtime-reentitlement-failed", clock.instant());
        throw failure;
      }
    }

    try {
      RuntimeWorkloadBinding workload =
          workloadIdentityAdmin.ensureBinding(
              new RuntimeWorkloadIdentityAdmin.EnsureBindingCommand(
                  command.organizationRef(),
                  command.personRef(),
                  cellRef,
                  "weaver-cell-" + cellKey,
                  command.authenticationMethod(),
                  correlationRef));
      RuntimeCell proposed =
          RuntimeCell.provisioning(
              command.organizationRef(),
              command.personRef(),
              command.memberBinding(),
              cellRef,
              workload,
              entitlement.entitlementRevision(),
              command.workspaceRevision(),
              command.workspaceManifestRef(),
              command.runtimeStateStoreRef(),
              correlationRef,
              now);
      RuntimeCell persisted;
      try {
        persisted = cells.insert(proposed);
      } catch (RuntimeException concurrentInsert) {
        persisted =
            cells
                .findByPerson(command.organizationRef(), command.personRef())
                .orElseThrow(() -> concurrentInsert);
        requireSameBinding(persisted, receipt, command);
      }
      commands.complete(receipt, persisted.version(), clock.instant());
      return persisted;
    } catch (RuntimeException failure) {
      commands.fail(receipt, "runtime-provisioning-failed", clock.instant());
      throw failure;
    }
  }

  public RuntimeCell revoke(RevokeRuntimeCommand command) {
    RuntimeCell existing =
        cells
            .findByPerson(command.organizationRef(), command.personRef())
            .orElseThrow(() -> new IllegalStateException("runtime cell does not exist"));
    Instant now = clock.instant();
    RuntimeAuditCorrelation attemptCorrelation =
        appendCorrelation(
            REVOKE,
            command.organizationRef(),
            command.personRef(),
            existing.cellRef(),
            command.idempotencyKey(),
            command.auditRef(),
            now);
    RuntimeCommandReceipt receipt =
        commands.claim(
            command.organizationRef(),
            command.personRef(),
            command.idempotencyKey(),
            revokeCommand(command),
            existing.cellRef(),
            attemptCorrelation.correlationRef(),
            now);
    String correlationRef = receipt.auditRef();
    if (receipt.status() == RuntimeCommandReceipt.Status.COMPLETED) {
      return cells
          .findByPerson(command.organizationRef(), command.personRef())
          .filter(cell -> cell.cellRef().equals(receipt.cellRef()))
          .orElseThrow(
              () ->
                  new RuntimeCommandConflictException(
                      "the completed revocation receipt has no matching runtime cell"));
    }
    try {
      RuntimeEntitlementRef entitlement =
          governance
              .findCurrent(command.organizationRef(), command.personRef())
              .orElseThrow(() -> new IllegalStateException("runtime entitlement does not exist"));
      if (!entitlement.entitlementRevision().equals(command.expectedEntitlementRevision())) {
        throw new RuntimeCommandConflictException("the requested entitlement revision is stale");
      }
      if (existing.entitlementState() == RuntimeEntitlementState.REVOKED
          && entitlement.state() == RuntimeEntitlementState.REVOKED) {
        workloadIdentityAdmin.disableBinding(
            new RuntimeWorkloadIdentityAdmin.DisableBindingCommand(
                existing.organizationRef(),
                existing.personRef(),
                existing.cellRef(),
                existing.workloadBinding(),
                correlationRef));
        commands.complete(receipt, existing.version(), clock.instant());
        return existing;
      }
      String revocationRef =
          "revocation:"
              + RuntimeWorkloadOwnership.fingerprint(
                      REVOKE
                          + "\u0000"
                          + command.organizationRef()
                          + "\u0000"
                          + command.personRef()
                          + "\u0000"
                          + command.idempotencyKey())
                  .substring(7);
      String workloadRefHash =
          RuntimeWorkloadOwnership.fingerprint(
              existing.workloadBinding().issuer()
                  + "\u0000"
                  + existing.workloadBinding().subject()
                  + "\u0000"
                  + existing.workloadBinding().clientId()
                  + "\u0000"
                  + existing.workloadBinding().credentialRef());
      governance.revoke(
          entitlement,
          existing.cellRef(),
          existing.runtimeProfileHash(),
          workloadRefHash,
          command.reasonCode(),
          command.reasonRefHash(),
          RuntimeWorkloadOwnership.fingerprint(command.actorRef()),
          revocationRef,
          correlationRef,
          now);
      profiles.revokeCurrent(existing.cellRef(), command.reasonCode(), now);
      RuntimeCell revoked =
          cells.revoke(
              command.organizationRef(),
              command.personRef(),
              entitlement.entitlementRevision(),
              correlationRef,
              now);
      workloadIdentityAdmin.disableBinding(
          new RuntimeWorkloadIdentityAdmin.DisableBindingCommand(
              revoked.organizationRef(),
              revoked.personRef(),
              revoked.cellRef(),
              revoked.workloadBinding(),
              correlationRef));
      commands.complete(receipt, revoked.version(), clock.instant());
      return revoked;
    } catch (RuntimeException failure) {
      commands.fail(receipt, "runtime-revocation-incomplete", clock.instant());
      throw failure;
    }
  }

  @Override
  public RuntimeCell reconcileEntitlement(RuntimeCell expectedCell, String auditRef) {
    Objects.requireNonNull(expectedCell, "expectedCell");
    requireText(auditRef, "auditRef");
    if (expectedCell.entitlementState() != RuntimeEntitlementState.ENTITLED) {
      return expectedCell;
    }
    Instant now = clock.instant();
    RuntimeEntitlementRef previous =
        governance
            .findCurrent(expectedCell.organizationRef(), expectedCell.personRef())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "an active runtime cell has no authoritative entitlement fact"));
    if (!previous.entitlementRevision().equals(expectedCell.entitlementRevision())) {
      return repairOrFenceDivergedEntitlement(expectedCell, previous, auditRef, now);
    }
    try {
      RuntimeEntitlementObservation observation =
          entitlementAuthority.observe(
              new RuntimeEntitlementAuthority.ObserveEntitlementCommand(
                  expectedCell.organizationRef(),
                  expectedCell.personRef(),
                  expectedCell.memberBinding(),
                  auditRef));
      String activationRef =
          "reconcile:"
              + RuntimeWorkloadOwnership.fingerprint(
                      expectedCell.cellRef()
                          + "\u0000"
                          + observation.sourceGroupRef()
                          + "\u0000"
                          + observation.capabilityRevision())
                  .substring(7);
      RuntimeEntitlementRef refreshed =
          governance.activate(observation, activationRef, auditRef, now);
      if (refreshed.entitlementRevision().equals(expectedCell.entitlementRevision())) {
        return expectedCell;
      }

      revokeEntitlementFact(
          expectedCell, previous, "capability-policy-superseded", auditRef, now, false);
      profiles.revokeCurrent(expectedCell.cellRef(), "capability-policy-superseded", now);
      RuntimeCell rebound =
          cells.bindEntitlement(
              expectedCell.organizationRef(),
              expectedCell.personRef(),
              expectedCell.version(),
              refreshed.entitlementRevision(),
              auditRef,
              now);
      RuntimeWorkloadBinding workload =
          workloadIdentityAdmin.ensureBinding(
              new RuntimeWorkloadIdentityAdmin.EnsureBindingCommand(
                  rebound.organizationRef(),
                  rebound.personRef(),
                  rebound.cellRef(),
                  rebound.workloadBinding().clientId(),
                  rebound.workloadBinding().authenticationMethod(),
                  auditRef));
      if (!workload.equals(rebound.workloadBinding())) {
        throw new RuntimeCommandConflictException(
            "the immutable workload identity changed during entitlement reconciliation");
      }
      return rebound;
    } catch (RuntimeEntitlementDeniedException denied) {
      return fenceEntitlement(expectedCell, previous, "idm-entitlement-denied", auditRef, now);
    } catch (RuntimeEntitlementAuthorityException unavailable) {
      if (governance
          .findEffectiveRevision(
              expectedCell.organizationRef(),
              expectedCell.personRef(),
              expectedCell.entitlementRevision(),
              now)
          .isPresent()) {
        throw unavailable;
      }
      return fenceEntitlement(
          expectedCell, previous, "entitlement-observation-expired", auditRef, now);
    }
  }

  private RuntimeCell repairOrFenceDivergedEntitlement(
      RuntimeCell cell, RuntimeEntitlementRef current, String auditRef, Instant now) {
    RuntimeEntitlementRef bound =
        governance
            .findRevision(cell.organizationRef(), cell.personRef(), cell.entitlementRevision())
            .orElse(null);
    if (current.effectiveAt(now) && current.memberBinding().equals(cell.memberBinding())) {
      if (bound != null && bound.state() == RuntimeEntitlementState.ENTITLED) {
        revokeEntitlementFact(cell, bound, "capability-policy-superseded", auditRef, now, false);
      }
      profiles.revokeCurrent(cell.cellRef(), "entitlement-binding-repaired", now);
      RuntimeCell rebound =
          cells.bindEntitlement(
              cell.organizationRef(),
              cell.personRef(),
              cell.version(),
              current.entitlementRevision(),
              auditRef,
              now);
      RuntimeWorkloadBinding workload =
          workloadIdentityAdmin.ensureBinding(
              new RuntimeWorkloadIdentityAdmin.EnsureBindingCommand(
                  rebound.organizationRef(),
                  rebound.personRef(),
                  rebound.cellRef(),
                  rebound.workloadBinding().clientId(),
                  rebound.workloadBinding().authenticationMethod(),
                  auditRef));
      if (!workload.equals(rebound.workloadBinding())) {
        throw new RuntimeCommandConflictException(
            "the immutable workload identity changed while repairing entitlement drift");
      }
      return rebound;
    }
    profiles.revokeCurrent(cell.cellRef(), "entitlement-binding-diverged", now);
    RuntimeCell fenced =
        cells.revoke(
            cell.organizationRef(), cell.personRef(), current.entitlementRevision(), auditRef, now);
    workloadIdentityAdmin.disableBinding(
        new RuntimeWorkloadIdentityAdmin.DisableBindingCommand(
            fenced.organizationRef(),
            fenced.personRef(),
            fenced.cellRef(),
            fenced.workloadBinding(),
            auditRef));
    return fenced;
  }

  private RuntimeCell fenceEntitlement(
      RuntimeCell cell,
      RuntimeEntitlementRef entitlement,
      String reasonCode,
      String auditRef,
      Instant now) {
    RuntimeAuditCorrelation correlation =
        appendCorrelation(
            "RECONCILE_REVOKE",
            cell.organizationRef(),
            cell.personRef(),
            cell.cellRef(),
            entitlement.entitlementRevision() + "\u0000" + reasonCode,
            auditRef,
            now);
    revokeEntitlementFact(cell, entitlement, reasonCode, correlation.correlationRef(), now, true);
    profiles.revokeCurrent(cell.cellRef(), reasonCode, now);
    RuntimeCell revoked =
        cells.revoke(
            cell.organizationRef(),
            cell.personRef(),
            entitlement.entitlementRevision(),
            correlation.correlationRef(),
            now);
    workloadIdentityAdmin.disableBinding(
        new RuntimeWorkloadIdentityAdmin.DisableBindingCommand(
            revoked.organizationRef(),
            revoked.personRef(),
            revoked.cellRef(),
            revoked.workloadBinding(),
            correlation.correlationRef()));
    return revoked;
  }

  private void revokeEntitlementFact(
      RuntimeCell cell,
      RuntimeEntitlementRef entitlement,
      String reasonCode,
      String auditRef,
      Instant now,
      boolean correlationAlreadyPersisted) {
    RuntimeAuditCorrelation correlation =
        correlationAlreadyPersisted
            ? null
            : appendCorrelation(
                "RECONCILE_SUPERSEDE",
                cell.organizationRef(),
                cell.personRef(),
                cell.cellRef(),
                entitlement.entitlementRevision() + "\u0000" + reasonCode,
                auditRef,
                now);
    String correlationRef = correlation == null ? auditRef : correlation.correlationRef();
    String revocationRef =
        "revocation:"
            + RuntimeWorkloadOwnership.fingerprint(
                    cell.organizationRef()
                        + "\u0000"
                        + cell.personRef()
                        + "\u0000"
                        + entitlement.entitlementRevision()
                        + "\u0000"
                        + reasonCode)
                .substring(7);
    String workloadRefHash =
        RuntimeWorkloadOwnership.fingerprint(
            cell.workloadBinding().issuer()
                + "\u0000"
                + cell.workloadBinding().subject()
                + "\u0000"
                + cell.workloadBinding().clientId()
                + "\u0000"
                + cell.workloadBinding().credentialRef());
    governance.revoke(
        entitlement,
        cell.cellRef(),
        cell.runtimeProfileHash(),
        workloadRefHash,
        reasonCode,
        RuntimeWorkloadOwnership.fingerprint(
            "weave.agent-runtime.revocation-reason/v1\u0000" + reasonCode),
        RuntimeWorkloadOwnership.fingerprint("actor:agent-runtime-control"),
        revocationRef,
        correlationRef,
        now);
  }

  private static void requireSameBinding(
      RuntimeCell cell, RuntimeCommandReceipt receipt, ProvisionRuntimeCommand command) {
    requireSameBinding(cell, receipt.cellRef(), command);
  }

  private static void requireSameBinding(
      RuntimeCell cell, String cellRef, ProvisionRuntimeCommand command) {
    if (!cell.cellRef().equals(cellRef)
        || !cell.organizationRef().equals(command.organizationRef())
        || !cell.personRef().equals(command.personRef())
        || !cell.memberBinding().equals(command.memberBinding())) {
      throw new RuntimeCommandConflictException("person is bound to a different runtime cell");
    }
  }

  private static String stableCellKey(String organizationRef, RuntimeMemberBinding memberBinding) {
    String input =
        organizationRef + "\u0000" + memberBinding.issuer() + "\u0000" + memberBinding.subject();
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
    }
  }

  private static String provisionCommand(ProvisionRuntimeCommand command) {
    return semanticCommand(
        PROVISION,
        command.memberBinding().issuer()
            + "\u0000"
            + command.memberBinding().subject()
            + "\u0000"
            + command.workspaceRevision()
            + "\u0000"
            + command.workspaceManifestRef()
            + "\u0000"
            + command.runtimeStateStoreRef()
            + "\u0000"
            + command.authenticationMethod().name());
  }

  private static String revokeCommand(RevokeRuntimeCommand command) {
    return semanticCommand(
        REVOKE,
        command.expectedEntitlementRevision()
            + "\u0000"
            + command.reasonCode()
            + "\u0000"
            + command.reasonRefHash()
            + "\u0000"
            + RuntimeWorkloadOwnership.fingerprint(command.actorRef()));
  }

  private static String semanticCommand(String operation, String semantics) {
    return operation + ":" + RuntimeWorkloadOwnership.fingerprint(semantics).substring(7, 39);
  }

  private RuntimeAuditCorrelation appendCorrelation(
      String operation,
      String organizationRef,
      String personRef,
      String cellRef,
      String idempotencyKey,
      String auditRef,
      Instant now) {
    String correlationRef =
        "correlation:"
            + RuntimeWorkloadOwnership.fingerprint(
                    operation
                        + "\u0000"
                        + organizationRef
                        + "\u0000"
                        + personRef
                        + "\u0000"
                        + idempotencyKey
                        + "\u0000"
                        + auditRef)
                .substring(7);
    return governance.appendCorrelation(
        new RuntimeAuditCorrelation(
            UUID.randomUUID(),
            correlationRef,
            RuntimeWorkloadOwnership.fingerprint(organizationRef),
            RuntimeWorkloadOwnership.fingerprint(personRef),
            null,
            RuntimeWorkloadOwnership.fingerprint(cellRef),
            null,
            null,
            null,
            RuntimeWorkloadOwnership.fingerprint(auditRef),
            now,
            now));
  }

  public record ProvisionRuntimeCommand(
      String organizationRef,
      String personRef,
      RuntimeMemberBinding memberBinding,
      String workspaceRevision,
      String workspaceManifestRef,
      String runtimeStateStoreRef,
      RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod,
      String idempotencyKey,
      String auditRef) {

    public ProvisionRuntimeCommand {
      requireText(organizationRef, "organizationRef");
      requireText(personRef, "personRef");
      if (memberBinding == null || authenticationMethod == null) {
        throw new IllegalArgumentException(
            "member binding and workload authentication method are required");
      }
      requireText(workspaceRevision, "workspaceRevision");
      requireText(workspaceManifestRef, "workspaceManifestRef");
      requireText(runtimeStateStoreRef, "runtimeStateStoreRef");
      if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
        throw new IllegalArgumentException("idempotency key length must be between 16 and 128");
      }
      requireText(auditRef, "auditRef");
    }
  }

  public record RevokeRuntimeCommand(
      String organizationRef,
      String personRef,
      String expectedEntitlementRevision,
      String reasonCode,
      String reasonRefHash,
      String actorRef,
      String idempotencyKey,
      String auditRef) {
    public RevokeRuntimeCommand {
      requireText(organizationRef, "organizationRef");
      requireText(personRef, "personRef");
      if (expectedEntitlementRevision == null
          || !expectedEntitlementRevision.matches("sha256:[a-f0-9]{64}")) {
        throw new IllegalArgumentException(
            "expectedEntitlementRevision must be an authoritative SHA-256 revision");
      }
      if (reasonCode == null || !reasonCode.matches("[a-z0-9][a-z0-9-]{1,98}[a-z0-9]")) {
        throw new IllegalArgumentException("reasonCode must be a bounded machine-readable code");
      }
      if (reasonRefHash == null || !reasonRefHash.matches("sha256:[a-f0-9]{64}")) {
        throw new IllegalArgumentException("reasonRefHash must be a SHA-256 reference");
      }
      requireText(actorRef, "actorRef");
      if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
        throw new IllegalArgumentException("idempotency key length must be between 16 and 128");
      }
      requireText(auditRef, "auditRef");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
