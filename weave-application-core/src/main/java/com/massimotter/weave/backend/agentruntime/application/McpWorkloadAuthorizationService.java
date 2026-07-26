package com.massimotter.weave.backend.agentruntime.application;

import com.massimotter.weave.backend.agentruntime.domain.ExchangedWorkloadToken;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.WeaverWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadBindingAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Resolves the typed MCP workload principal from server-owned state on every backend call. */
public final class McpWorkloadAuthorizationService {
  private static final Duration MAXIMUM_EXCHANGED_TOKEN_TTL = Duration.ofSeconds(60);
  private static final Set<RuntimeCellState> ACTIVE_STATES =
      EnumSet.of(
          RuntimeCellState.STARTING,
          RuntimeCellState.MATERIALIZING,
          RuntimeCellState.READY,
          RuntimeCellState.BUSY,
          RuntimeCellState.SYNCING,
          RuntimeCellState.DEGRADED);

  private final RuntimeCellRepository cells;
  private final RuntimeProfileRepository profiles;
  private final RuntimeProfileVerifier verifier;
  private final RuntimeGovernanceRepository governance;
  private final RuntimeWorkloadBindingAuthority workloadIdentities;
  private final RuntimeEntitlementAuthority entitlementAuthority;
  private final Clock clock;

  public McpWorkloadAuthorizationService(
      RuntimeCellRepository cells,
      RuntimeProfileRepository profiles,
      RuntimeProfileVerifier verifier,
      RuntimeGovernanceRepository governance,
      RuntimeWorkloadBindingAuthority workloadIdentities,
      RuntimeEntitlementAuthority entitlementAuthority,
      Clock clock) {
    this.cells = Objects.requireNonNull(cells, "cells");
    this.profiles = Objects.requireNonNull(profiles, "profiles");
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    this.governance = Objects.requireNonNull(governance, "governance");
    this.workloadIdentities = Objects.requireNonNull(workloadIdentities, "workloadIdentities");
    this.entitlementAuthority =
        Objects.requireNonNull(entitlementAuthority, "entitlementAuthority");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public WeaverWorkloadPrincipal authorize(ExchangedWorkloadToken token) {
    Objects.requireNonNull(token, "token");
    Instant now = clock.instant();
    if (token.expiresAt().isAfter(token.issuedAt().plus(MAXIMUM_EXCHANGED_TOKEN_TTL))
        || !now.isBefore(token.expiresAt())) {
      throw denied();
    }
    RuntimeCell cell =
        cells
            .findByWorkload(token.issuer(), token.subject())
            .orElseThrow(McpWorkloadAuthorizationService::denied);
    requireActiveCell(cell, token);
    String auditRef =
        "audit:mcp-workload:"
            + RuntimeWorkloadOwnership.fingerprint(
                    token.issuer() + "\u0000" + token.tokenId() + "\u0000" + cell.cellRef())
                .substring(7);
    try {
      workloadIdentities.requireCurrentBinding(
          new RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
              cell.organizationRef(),
              cell.personRef(),
              cell.cellRef(),
              cell.workloadBinding(),
              auditRef));
    } catch (RuntimeWorkloadIdentityException | UnsupportedOperationException failure) {
      throw denied();
    }

    SignedRuntimeProfile envelope =
        profiles
            .findCurrentForWorkload(
                cell.runtimeProfileHash(),
                cell.workloadBinding().issuer(),
                cell.workloadBinding().subject(),
                cell.workloadBinding().clientId(),
                now)
            .orElseThrow(McpWorkloadAuthorizationService::denied);
    RuntimeProfile profile;
    try {
      profile = verifier.verify(envelope, now);
    } catch (InvalidRuntimeProfileException | IllegalArgumentException failure) {
      throw denied();
    }
    requireProfileBinding(cell, profile, envelope, token);

    RuntimeEntitlementRef entitlement =
        governance
            .findEffectiveRevision(
                cell.organizationRef(), cell.personRef(), cell.entitlementRevision(), now)
            .filter(current -> current.memberBinding().equals(cell.memberBinding()))
            .orElseThrow(McpWorkloadAuthorizationService::denied);
    RuntimeEntitlementObservation observation;
    try {
      observation =
          entitlementAuthority.observe(
              new RuntimeEntitlementAuthority.ObserveEntitlementCommand(
                  cell.organizationRef(), cell.personRef(), cell.memberBinding(), auditRef));
    } catch (RuntimeEntitlementAuthorityException unavailable) {
      throw new McpWorkloadAuthorizationException(true);
    } catch (RuntimeException denied) {
      throw denied();
    }
    if (!sameAuthority(entitlement, observation)) {
      throw denied();
    }

    Set<String> visibleToolClasses = grantedToolClasses(profile, token.scopes());
    Instant authorizationExpiry =
        minimum(token.expiresAt(), profile.expiresAt(), observation.expiresAt());
    return new WeaverWorkloadPrincipal(
        token.issuer(),
        token.subject(),
        cell.workloadBinding().clientId(),
        token.edgeClientId(),
        cell.organizationRef(),
        cell.personRef(),
        cell.memberBinding(),
        cell.cellRef(),
        profile.profileId(),
        envelope.profileHash(),
        cell.entitlementRevision(),
        authorizationExpiry,
        token.scopes(),
        visibleToolClasses);
  }

  private static void requireActiveCell(RuntimeCell cell, ExchangedWorkloadToken token) {
    RuntimeWorkloadBinding workload = cell.workloadBinding();
    if (cell.entitlementState() != RuntimeEntitlementState.ENTITLED
        || !ACTIVE_STATES.contains(cell.desiredState())
        || cell.runtimeProfileId() == null
        || cell.runtimeProfileHash() == null
        || !workload.issuer().equals(token.issuer())
        || !workload.subject().equals(token.subject())) {
      throw denied();
    }
  }

  private static void requireProfileBinding(
      RuntimeCell cell,
      RuntimeProfile profile,
      SignedRuntimeProfile envelope,
      ExchangedWorkloadToken token) {
    RuntimeProfile.WorkloadIdentity workload = profile.workloadIdentity();
    RuntimeProfile.AuthenticationMethod expectedMethod =
        switch (cell.workloadBinding().authenticationMethod()) {
          case PRIVATE_KEY_JWT -> RuntimeProfile.AuthenticationMethod.PRIVATE_KEY_JWT;
          case CLIENT_SECRET_BASIC -> RuntimeProfile.AuthenticationMethod.CLIENT_SECRET_BASIC;
        };
    if (!envelope.profileId().equals(cell.runtimeProfileId())
        || !envelope.profileHash().equals(cell.runtimeProfileHash())
        || !profile.organizationRef().equals(cell.organizationRef())
        || !profile.personRef().equals(cell.personRef())
        || !profile.memberBinding().equals(cell.memberBinding())
        || !profile.cellRef().equals(cell.cellRef())
        || !profile.entitlementRevision().equals(cell.entitlementRevision())
        || !workload.issuer().equals(token.issuer())
        || !workload.subject().equals(token.subject())
        || !workload.clientId().equals(cell.workloadBinding().clientId())
        || workload.authenticationMethod() != expectedMethod
        || token.expiresAt().isAfter(profile.expiresAt())) {
      throw denied();
    }
  }

  private static Set<String> grantedToolClasses(RuntimeProfile profile, Set<String> tokenScopes) {
    LinkedHashSet<String> granted = new LinkedHashSet<>();
    for (RuntimeProfile.McpServer server : profile.mcp().servers()) {
      if (!server.requiredScopes().containsAll(tokenScopes)) {
        continue;
      }
      Set<String> serverClasses =
          server.allowedToolClasses() == null
              ? Set.copyOf(profile.mcp().visibleToolClasses())
              : Set.copyOf(server.allowedToolClasses());
      for (String toolClass : profile.mcp().visibleToolClasses()) {
        if (serverClasses.contains(toolClass) && tokenScopes.contains(toolClass)) {
          granted.add(toolClass);
        }
      }
    }
    if (granted.isEmpty()) {
      throw denied();
    }
    return Set.copyOf(granted);
  }

  private static boolean sameAuthority(
      RuntimeEntitlementRef entitlement, RuntimeEntitlementObservation observation) {
    return entitlement.organizationRef().equals(observation.organizationRef())
        && entitlement.personRef().equals(observation.personRef())
        && entitlement.memberBinding().equals(observation.memberBinding())
        && entitlement.sourceProvider().equals(observation.sourceProvider())
        && entitlement.sourceGroupRef().equals(observation.sourceGroupRef())
        && entitlement.capabilityRevision().equals(observation.capabilityRevision());
  }

  private static Instant minimum(Instant first, Instant second, Instant third) {
    Instant result = first.isBefore(second) ? first : second;
    return result.isBefore(third) ? result : third;
  }

  private static McpWorkloadAuthorizationException denied() {
    return new McpWorkloadAuthorizationException(false);
  }
}
