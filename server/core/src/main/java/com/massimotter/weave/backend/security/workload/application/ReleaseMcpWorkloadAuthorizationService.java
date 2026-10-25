package com.massimotter.weave.backend.security.workload.application;

import com.massimotter.weave.backend.security.workload.domain.ExchangedWorkloadToken;
import com.massimotter.weave.backend.security.workload.domain.ReleaseMcpBinding;
import com.massimotter.weave.backend.security.workload.domain.WeaverWorkloadPrincipal;
import com.massimotter.weave.backend.security.workload.domain.WorkloadEntitlementObservation;
import com.massimotter.weave.backend.security.workload.domain.WorkloadReferences;
import com.massimotter.weave.backend.security.workload.port.McpWorkloadAuthorization;
import com.massimotter.weave.backend.security.workload.port.McpWorkloadAuthorizationException;
import com.massimotter.weave.backend.security.workload.port.ReleaseMcpBindingRepository;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthority;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthorityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Bounded release MCP authorization, independent of future runtime cells and profiles. */
public final class ReleaseMcpWorkloadAuthorizationService implements McpWorkloadAuthorization {
    private static final Duration MAXIMUM_EXCHANGED_TOKEN_TTL = Duration.ofSeconds(60);
    private final ReleaseMcpBindingRepository releaseBindings;
    private final WorkloadEntitlementAuthority entitlementAuthority;
    private final Set<String> releaseAllowedCapabilities;
    private final Clock clock;

    public ReleaseMcpWorkloadAuthorizationService(
            ReleaseMcpBindingRepository releaseBindings,
            WorkloadEntitlementAuthority entitlementAuthority,
            Set<String> allowedCapabilities,
            Clock clock) {
        this.releaseBindings = Objects.requireNonNull(releaseBindings, "releaseBindings");
        this.entitlementAuthority = Objects.requireNonNull(entitlementAuthority, "entitlementAuthority");
        this.releaseAllowedCapabilities = Set.copyOf(Objects.requireNonNull(allowedCapabilities, "allowedCapabilities"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public WeaverWorkloadPrincipal authorize(ExchangedWorkloadToken token) {
        Objects.requireNonNull(token, "token");
        Instant now = clock.instant();
        if (token.expiresAt().isAfter(token.issuedAt().plus(MAXIMUM_EXCHANGED_TOKEN_TTL))
                || !now.isBefore(token.expiresAt())) {
            throw denied(McpWorkloadAuthorizationException.Reason.TOKEN_LIFETIME);
        }
        ReleaseMcpBinding binding;
        try {
            binding = releaseBindings.findByWorkload(token.issuer(), token.subject())
                    .orElseThrow(() -> denied(McpWorkloadAuthorizationException.Reason.IDENTITY_BINDING));
        } catch (McpWorkloadAuthorizationException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            throw new McpWorkloadAuthorizationException(true, McpWorkloadAuthorizationException.Reason.AUTHORITY_UNAVAILABLE);
        }
        return authorizeReleaseBinding(binding, token, now);
    }

    private WeaverWorkloadPrincipal authorizeReleaseBinding(
            ReleaseMcpBinding binding, ExchangedWorkloadToken token, Instant now) {
        if (!binding.active()
                || !now.isBefore(binding.expiresAt())
                || !binding.workloadIssuer().equals(token.issuer())
                || !binding.workloadSubject().equals(token.subject())
                || !binding.allowedToolClasses().containsAll(token.scopes())
                || !releaseAllowedCapabilities.containsAll(token.scopes())) {
            throw denied(McpWorkloadAuthorizationException.Reason.IDENTITY_BINDING);
        }
        String auditRef = "audit:mcp-workload:" + WorkloadReferences.fingerprint(
                token.issuer() + "\u0000" + token.tokenId() + "\u0000" + binding.bindingRef()).substring(7);
        WorkloadEntitlementAuthority.ObserveEntitlementCommand observationCommand =
                new WorkloadEntitlementAuthority.ObserveEntitlementCommand(
                        binding.organizationRef(), binding.personRef(), binding.memberBinding(), auditRef);
        WorkloadEntitlementObservation observation;
        try {
            observation = entitlementAuthority.observe(observationCommand);
        } catch (WorkloadEntitlementAuthorityException unavailable) {
            throw new McpWorkloadAuthorizationException(
                    true, McpWorkloadAuthorizationException.Reason.AUTHORITY_UNAVAILABLE);
        } catch (RuntimeException denied) {
            throw denied(McpWorkloadAuthorizationException.Reason.ENTITLEMENT_OBSERVATION);
        }
        // The authoritative lookup performs I/O and stamps its observation after
        // the initial token check. Compare it with a fresh clock reading so a
        // current observation is not mistaken for one from the future.
        Instant verifiedAt = clock.instant();
        if (!binding.organizationRef().equals(observation.organizationRef())
                || !binding.personRef().equals(observation.personRef())
                || !binding.memberBinding().equals(observation.memberBinding())
                || !verifiedAt.isBefore(token.expiresAt())
                || !verifiedAt.isBefore(binding.expiresAt())
                || !verifiedAt.isBefore(observation.expiresAt())
                || observation.observedAt().isAfter(verifiedAt)) {
            throw denied(McpWorkloadAuthorizationException.Reason.ENTITLEMENT_MISMATCH);
        }
        if (token.scopes().contains("calendar.write")) {
            Set<String> currentRoles;
            try {
                currentRoles = entitlementAuthority.currentWeaveRoles(observationCommand);
            } catch (WorkloadEntitlementAuthorityException unavailable) {
                throw new McpWorkloadAuthorizationException(
                        true, McpWorkloadAuthorizationException.Reason.AUTHORITY_UNAVAILABLE);
            } catch (RuntimeException denied) {
                throw denied(McpWorkloadAuthorizationException.Reason.ENTITLEMENT_OBSERVATION);
            }
            if (currentRoles == null
                    || !(currentRoles.contains("owner") || currentRoles.contains("admin"))) {
                throw denied(McpWorkloadAuthorizationException.Reason.TOOL_SCOPE);
            }
        }
        return new WeaverWorkloadPrincipal(
                token.issuer(), token.subject(), binding.workloadClientId(), token.edgeClientId(),
                binding.organizationRef(), binding.personRef(), binding.memberBinding(),
                observation.contextPrincipalClaim(), binding.bindingRef(), "workloadBindingRef",
                "mcp-binding:" + binding.revision(), "mcp-entitlement:" + observation.capabilityRevision(),
                minimum(token.expiresAt(), binding.expiresAt(), observation.expiresAt()),
                token.scopes(), token.scopes());
    }

    private static Instant minimum(Instant first, Instant second, Instant third) {
        Instant result = first.isBefore(second) ? first : second;
        return result.isBefore(third) ? result : third;
    }

    private static McpWorkloadAuthorizationException denied(
            McpWorkloadAuthorizationException.Reason reason) {
        return new McpWorkloadAuthorizationException(false, reason);
    }
}
