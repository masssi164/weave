package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.Blocker;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport.Counts;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.ClientObservation;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.ManagementState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure support-safe consistency evaluation separated from provider mutation and scheduling. */
final class RuntimeWorkloadReconciliationEvaluator {
  private final RuntimeWorkloadCredentialStore credentials;

  RuntimeWorkloadReconciliationEvaluator(RuntimeWorkloadCredentialStore credentials) {
    this.credentials = Objects.requireNonNull(credentials, "credentials");
  }

  Evaluation evaluate(
      List<RuntimeCell> authoritative,
      RuntimeWorkloadIdentityInventory.Snapshot provider,
      int reconcileFailures,
      Instant now) {
    Map<String, RuntimeCell> expected = expectedByClient(authoritative);
    Map<String, List<ClientObservation>> observed = group(provider.clients());
    Analysis result = new Analysis();
    result.reconcileFailures = reconcileFailures;
    if (reconcileFailures > 0) {
      result.blockers.add(Blocker.RECONCILE_FAILURE);
    }
    result.providerReservedClients = provider.clients().size();

    for (Map.Entry<String, List<ClientObservation>> entry : observed.entrySet()) {
      RuntimeCell cell = expected.get(entry.getKey());
      List<ClientObservation> candidates = entry.getValue();
      if (cell == null) {
        result.orphanedClients += candidates.size();
        result.blockers.add(Blocker.ORPHANED_CLIENT);
      }
      if (candidates.size() > 1) {
        result.duplicateClientBindings++;
        result.blockers.add(Blocker.DUPLICATE_CLIENT);
      }
      for (ClientObservation observation : candidates) {
        if (observation.managementState() == ManagementState.UNOWNED) {
          result.unownedClients++;
          result.blockers.add(Blocker.UNOWNED_CLIENT);
        } else if (observation.managementState() == ManagementState.MALFORMED) {
          result.malformedClients++;
          result.blockers.add(Blocker.MALFORMED_CLIENT);
        }
      }
    }

    for (RuntimeCell cell : authoritative) {
      boolean active = cell.entitlementState() == RuntimeEntitlementState.ENTITLED;
      if (active) {
        result.authoritativeActiveCells++;
      } else {
        result.authoritativeInactiveCells++;
      }
      List<ClientObservation> candidates =
          observed.getOrDefault(cell.workloadBinding().clientId(), List.of());
      if (candidates.isEmpty()) {
        if (active) {
          result.missingClients++;
          result.blockers.add(Blocker.MISSING_CLIENT);
        } else {
          result.inactiveConverged++;
        }
      } else if (candidates.size() == 1) {
        ClientObservation observation = candidates.getFirst();
        if (!ownershipMatches(observation, cell)) {
          result.crossBoundClients++;
          result.blockers.add(Blocker.CROSS_BOUND_CLIENT);
        } else if (active && observation.enabled()) {
          result.activeConverged++;
        } else if (active) {
          result.disabledActiveClients++;
          result.blockers.add(Blocker.ACTIVE_CLIENT_DISABLED);
        } else if (observation.enabled()) {
          result.enabledInactiveClients++;
          result.blockers.add(Blocker.INACTIVE_CLIENT_ENABLED);
        } else {
          result.inactiveConverged++;
        }
      }
      inspectCredential(cell, candidates, active, now, result);
    }
    return result.evaluation();
  }

  Map<String, RuntimeCell> expectedByClient(List<RuntimeCell> authoritative) {
    Map<String, RuntimeCell> expected = new LinkedHashMap<>();
    for (RuntimeCell cell : authoritative) {
      RuntimeCell prior = expected.put(cell.workloadBinding().clientId(), cell);
      if (prior != null) {
        throw new IllegalStateException("The authoritative workload-client binding is ambiguous");
      }
    }
    return expected;
  }

  Map<String, List<ClientObservation>> group(List<ClientObservation> observations) {
    Map<String, List<ClientObservation>> grouped = new LinkedHashMap<>();
    for (ClientObservation observation : observations) {
      grouped
          .computeIfAbsent(observation.clientId(), ignored -> new ArrayList<>())
          .add(observation);
    }
    return grouped;
  }

  boolean ownershipMatches(ClientObservation observation, RuntimeCell cell) {
    return observation.managementState() == ManagementState.MANAGED
        && observation.serviceAccountsEnabled()
        && Objects.equals(observation.serviceAccountSubject(), cell.workloadBinding().subject())
        && Objects.equals(
            observation.ownerFingerprint(),
            RuntimeWorkloadOwnership.ownerFingerprint(
                cell.organizationRef(),
                cell.personRef(),
                cell.cellRef(),
                cell.workloadBinding().clientId()))
        && Objects.equals(
            observation.organizationFingerprint(),
            RuntimeWorkloadOwnership.fingerprint(cell.organizationRef()))
        && Objects.equals(
            observation.personFingerprint(), RuntimeWorkloadOwnership.fingerprint(cell.personRef()))
        && Objects.equals(
            observation.cellFingerprint(), RuntimeWorkloadOwnership.fingerprint(cell.cellRef()));
  }

  String authoritativeRevision(List<RuntimeCell> authoritative) {
    StringBuilder material = new StringBuilder("weave.agent-runtime.authority/v1");
    for (RuntimeCell cell : authoritative) {
      append(material, cell.cellRef());
      append(material, Long.toString(cell.version()));
      append(material, cell.entitlementState().name());
      append(material, cell.entitlementRevision());
      append(material, cell.workloadBinding().issuer());
      append(material, cell.workloadBinding().subject());
      append(material, cell.workloadBinding().clientId());
      append(material, cell.workloadBinding().authenticationMethod().name());
      append(material, cell.workloadBinding().credentialRef());
      append(material, cell.runtimeProfileHash() == null ? "none" : cell.runtimeProfileHash());
    }
    return RuntimeWorkloadOwnership.fingerprint(material.toString());
  }

  private void inspectCredential(
      RuntimeCell cell,
      List<ClientObservation> candidates,
      boolean active,
      Instant now,
      Analysis result) {
    RuntimeWorkloadCredentialState credential;
    try {
      credential = credentials.find(cell.workloadBinding().clientId()).orElse(null);
    } catch (RuntimeException invalidSecretState) {
      result.invalidCredentialStates++;
      result.blockers.add(Blocker.CREDENTIAL_STATE_INVALID);
      return;
    }
    if (credential == null) {
      if (active) {
        result.missingCredentials++;
        result.blockers.add(Blocker.MISSING_CREDENTIAL);
      }
      return;
    }
    String expectedOwner =
        RuntimeWorkloadOwnership.ownerFingerprint(
            cell.organizationRef(),
            cell.personRef(),
            cell.cellRef(),
            cell.workloadBinding().clientId());
    boolean inconsistent =
        !active
            || !expectedOwner.equals(credential.ownerFingerprint())
            || !cell.workloadBinding().credentialRef().equals(credential.credentialRef())
            || cell.workloadBinding().authenticationMethod() != credential.authenticationMethod();
    if (active
        && candidates.size() == 1
        && ownershipMatches(candidates.getFirst(), cell)
        && !candidates.getFirst().acceptedKeyIds().equals(credential.acceptedKeyIds())) {
      inconsistent = true;
    }
    if (inconsistent) {
      result.invalidCredentialStates++;
      result.blockers.add(Blocker.CREDENTIAL_STATE_INVALID);
    }
    if (credential.rotationPhase() != RuntimeWorkloadCredentialState.RotationPhase.NONE) {
      result.credentialRotationOverlaps++;
    }
    if (credential.activeCreatedAt().isAfter(now)) {
      result.invalidCredentialStates++;
      result.blockers.add(Blocker.CREDENTIAL_STATE_INVALID);
    } else {
      long age = Duration.between(credential.activeCreatedAt(), now).toSeconds();
      result.oldestCredentialAgeSeconds =
          result.oldestCredentialAgeSeconds == null
              ? age
              : Math.max(result.oldestCredentialAgeSeconds, age);
    }
  }

  private static void append(StringBuilder target, String value) {
    target.append('\u0000').append(value.length()).append(':').append(value);
  }

  record Evaluation(Counts counts, Long oldestCredentialAgeSeconds, Set<Blocker> blockers) {
    Evaluation {
      blockers = Set.copyOf(blockers);
    }
  }

  private static final class Analysis {
    private int authoritativeActiveCells;
    private int authoritativeInactiveCells;
    private int providerReservedClients;
    private int activeConverged;
    private int inactiveConverged;
    private int missingClients;
    private int disabledActiveClients;
    private int enabledInactiveClients;
    private int orphanedClients;
    private int duplicateClientBindings;
    private int crossBoundClients;
    private int unownedClients;
    private int malformedClients;
    private int missingCredentials;
    private int invalidCredentialStates;
    private int credentialRotationOverlaps;
    private int reconcileFailures;
    private Long oldestCredentialAgeSeconds;
    private final EnumSet<Blocker> blockers = EnumSet.noneOf(Blocker.class);

    private Evaluation evaluation() {
      return new Evaluation(
          new Counts(
              authoritativeActiveCells,
              authoritativeInactiveCells,
              providerReservedClients,
              activeConverged,
              inactiveConverged,
              missingClients,
              disabledActiveClients,
              enabledInactiveClients,
              orphanedClients,
              duplicateClientBindings,
              crossBoundClients,
              unownedClients,
              malformedClients,
              missingCredentials,
              invalidCredentialStates,
              credentialRotationOverlaps,
              reconcileFailures),
          oldestCredentialAgeSeconds,
          blockers);
    }
  }
}
