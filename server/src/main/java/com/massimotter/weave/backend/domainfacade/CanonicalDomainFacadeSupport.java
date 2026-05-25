package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.provider.ProviderCategoryCatalog;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CanonicalDomainFacadeSupport {

    private final CanonicalDomainDefinition definition;
    private final ProviderRegistry providerRegistry;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final Clock clock;

    public CanonicalDomainFacadeSupport(
            CanonicalDomainDefinition definition,
            ProviderRegistry providerRegistry,
            ProviderSelectionRepository providerSelectionRepository,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        this.definition = definition;
        this.providerRegistry = providerRegistry;
        this.providerSelectionRepository = providerSelectionRepository;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public CanonicalDomainContract contract() {
        return definition.contract();
    }

    public CanonicalDomainReadiness readiness(Jwt jwt, boolean includeAdminDiagnostics) {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot(jwt);
        WorkspaceCapabilityStatusResponse primaryCapability = definition.primaryCapability(capabilities);
        CanonicalMemberState policyState = policyState(primaryCapability);
        if (policyState == CanonicalMemberState.POLICY_BLOCKED || policyState == CanonicalMemberState.DISABLED) {
            return readinessWithoutProviderLookup(primaryCapability, policyState, includeAdminDiagnostics);
        }

        ProviderRegistryResponse registry = providerRegistry.status();
        List<CanonicalProviderMapping> mappings = new ArrayList<>();
        CanonicalMemberState aggregateState = CanonicalMemberState.READY;
        for (String providerCategoryKey : definition.providerCategoryKeys()) {
            Optional<ProviderSelection> maybeSelection = providerSelectionRepository.findByCategory(providerCategoryKey);
            Optional<ProviderStatusResponse> maybeProvider = maybeSelection.flatMap(selection -> registry.providers().stream()
                    .filter(provider -> ProviderCategoryCatalog.providerMatchesCategory(provider, providerCategoryKey))
                    .filter(provider -> providerKeyMatches(provider, selection.providerKey()))
                    .findFirst());
            CanonicalMemberState state = mapState(primaryCapability, maybeSelection, maybeProvider);
            aggregateState = worst(aggregateState, state);
            if (includeAdminDiagnostics) {
                mappings.add(mapping(providerCategoryKey, maybeSelection, maybeProvider, state));
            }
        }
        Map<String, Object> diagnostics = includeAdminDiagnostics
                ? adminDiagnostics(primaryCapability, aggregateState, mappings, true)
                : memberDiagnostics(primaryCapability, aggregateState, true);
        return new CanonicalDomainReadiness(
                contract().contractVersion(),
                definition.domain(),
                aggregateState,
                memberImpact(aggregateState, primaryCapability.memberImpact()),
                aggregateState != CanonicalMemberState.READY,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                contract(),
                includeAdminDiagnostics ? mappings : List.of(),
                diagnostics,
                Instant.now(clock));
    }

    public CanonicalDomainItems items(Jwt jwt) {
        CanonicalDomainReadiness readiness = readiness(jwt, false);
        return new CanonicalDomainItems(readiness, List.of());
    }

    public CanonicalCapabilityDecision evaluateCapability(Jwt jwt, String capability, String operation) {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot(jwt);
        WorkspaceCapabilityStatusResponse primaryCapability = definition.primaryCapability(capabilities);
        String safeCapability = safeIdentifier(capability, "unsupported-capability");
        boolean known = capability != null && definition.knownCapability(capability);
        boolean policyAllowed = known && primaryCapability.grantedCapabilities().contains(capability);
        CanonicalMemberState state = !known
                ? CanonicalMemberState.UNSUPPORTED
                : primaryCapability.policyState() == WorkspaceCapabilityPolicyState.POLICY_BLOCKED
                        ? CanonicalMemberState.POLICY_BLOCKED
                        : policyAllowed ? CanonicalMemberState.READY : CanonicalMemberState.POLICY_BLOCKED;
        boolean allowed = state == CanonicalMemberState.READY;
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("domain", definition.domain());
        diagnostics.put("operation", safeIdentifier(operation, "unspecified-operation"));
        diagnostics.put("knownCapability", known);
        diagnostics.put("effectivePolicyState", primaryCapability.policyState().value());
        diagnostics.put("providerLookupPerformed", false);
        diagnostics.put("policyEvaluatedBeforeProviderAccess", true);
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("diagnosticsRedacted", true);
        return new CanonicalCapabilityDecision(
                definition.domain(),
                safeCapability,
                allowed,
                state,
                allowed ? "allowed_by_weave_policy" : known ? "capability_policy_blocked" : "unsupported_capability_fail_closed",
                allowed,
                !allowed,
                true,
                false,
                false,
                diagnostics);
    }

    private CanonicalDomainReadiness readinessWithoutProviderLookup(
            WorkspaceCapabilityStatusResponse primaryCapability,
            CanonicalMemberState state,
            boolean includeAdminDiagnostics) {
        Map<String, Object> diagnostics = includeAdminDiagnostics
                ? adminDiagnostics(primaryCapability, state, List.of(), false)
                : memberDiagnostics(primaryCapability, state, false);
        return new CanonicalDomainReadiness(
                contract().contractVersion(),
                definition.domain(),
                state,
                memberImpact(state, primaryCapability.memberImpact()),
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                contract(),
                List.of(),
                diagnostics,
                Instant.now(clock));
    }

    private CanonicalMemberState policyState(WorkspaceCapabilityStatusResponse capability) {
        if (capability.policyState() == WorkspaceCapabilityPolicyState.POLICY_BLOCKED) {
            return CanonicalMemberState.POLICY_BLOCKED;
        }
        if (capability.policyState() == WorkspaceCapabilityPolicyState.DISABLED || !capability.enabled()) {
            return CanonicalMemberState.DISABLED;
        }
        return CanonicalMemberState.READY;
    }

    private CanonicalMemberState mapState(
            WorkspaceCapabilityStatusResponse primaryCapability,
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider) {
        if (primaryCapability.policyState() == WorkspaceCapabilityPolicyState.POLICY_BLOCKED) {
            return CanonicalMemberState.POLICY_BLOCKED;
        }
        if (primaryCapability.policyState() == WorkspaceCapabilityPolicyState.DISABLED || !primaryCapability.enabled()) {
            return CanonicalMemberState.DISABLED;
        }
        if (maybeSelection.isEmpty()) {
            return CanonicalMemberState.MISCONFIGURED;
        }
        if (maybeProvider.isEmpty()) {
            return CanonicalMemberState.UNAVAILABLE;
        }
        ProviderStatusResponse provider = maybeProvider.get();
        if (!provider.enabled() || provider.state() == ProviderState.DISABLED) {
            return CanonicalMemberState.DISABLED;
        }
        if (!provider.configured() || provider.state() == ProviderState.NOT_CONFIGURED) {
            return CanonicalMemberState.MISCONFIGURED;
        }
        if (provider.state() == ProviderState.DEGRADED) {
            return CanonicalMemberState.DEGRADED;
        }
        if (primaryCapability.readiness() == WorkspaceCapabilityReadiness.DEGRADED) {
            return CanonicalMemberState.DEGRADED;
        }
        if (provider.state() == ProviderState.READY || provider.state() == ProviderState.CONFIGURED) {
            return CanonicalMemberState.READY;
        }
        return CanonicalMemberState.UNAVAILABLE;
    }

    private CanonicalProviderMapping mapping(
            String providerCategoryKey,
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider,
            CanonicalMemberState state) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("domain", definition.domain());
        diagnostics.put("providerCategoryKey", providerCategoryKey);
        diagnostics.put("providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE);
        diagnostics.put("selectedByAdmin", maybeSelection.isPresent());
        diagnostics.put("configured", maybeProvider.map(ProviderStatusResponse::configured).orElse(false));
        diagnostics.put("readinessState", state.value());
        diagnostics.put("missingConfigurationCategory", missingConfigurationCategory(maybeSelection, maybeProvider, state));
        diagnostics.put("supportedCapabilities", maybeProvider.map(provider -> List.copyOf(provider.supportedCapabilities())).orElse(List.of()));
        diagnostics.put("unsupportedOperations", maybeProvider.map(provider -> List.copyOf(provider.unsupportedOperations())).orElse(List.of()));
        diagnostics.put("secretRefConfigured", maybeSelection.map(ProviderSelection::hasSecretRef).orElse(false));
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("downstreamPayloadsReturned", false);
        diagnostics.put("diagnosticsRedacted", true);
        return new CanonicalProviderMapping(
                definition.domain(),
                providerCategoryKey,
                maybeSelection.map(ProviderSelection::providerKey).orElse("awaiting_admin_selection"),
                ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                maybeSelection.isPresent(),
                maybeProvider.map(ProviderStatusResponse::configured).orElse(false),
                state,
                state != CanonicalMemberState.READY,
                true,
                maybeSelection.map(ProviderSelection::hasSecretRef).orElse(false),
                false,
                false,
                false,
                maybeSelection.map(ProviderSelection::lossyMappingNotes).orElse(List.of()),
                diagnostics);
    }

    private Map<String, Object> memberDiagnostics(
            WorkspaceCapabilityStatusResponse primaryCapability,
            CanonicalMemberState state,
            boolean providerLookupPerformed) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("domain", definition.domain());
        diagnostics.put("state", state.value());
        diagnostics.put("effectivePolicyState", primaryCapability.policyState().value());
        diagnostics.put("diagnosticsExposed", false);
        diagnostics.put("providerLookupPerformed", providerLookupPerformed);
        diagnostics.put("policyEvaluatedBeforeProviderAccess", true);
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("downstreamPayloadsReturned", false);
        return diagnostics;
    }

    private Map<String, Object> adminDiagnostics(
            WorkspaceCapabilityStatusResponse primaryCapability,
            CanonicalMemberState state,
            List<CanonicalProviderMapping> mappings,
            boolean providerLookupPerformed) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("domain", definition.domain());
        diagnostics.put("state", state.value());
        diagnostics.put("effectivePolicyState", primaryCapability.policyState().value());
        diagnostics.put("providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE);
        diagnostics.put("providerLookupPerformed", providerLookupPerformed);
        diagnostics.put("policyEvaluatedBeforeProviderAccess", true);
        diagnostics.put("providerCategoryCount", definition.providerCategoryKeys().size());
        diagnostics.put("mappingCount", mappings.size());
        diagnostics.put("secretRefOnly", mappings.stream().allMatch(mapping -> !mapping.secretMaterialReturned()));
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("downstreamPayloadsReturned", false);
        diagnostics.put("diagnosticsRedacted", true);
        return diagnostics;
    }

    private String missingConfigurationCategory(
            Optional<ProviderSelection> maybeSelection,
            Optional<ProviderStatusResponse> maybeProvider,
            CanonicalMemberState state) {
        if (state == CanonicalMemberState.POLICY_BLOCKED) {
            return "policy";
        }
        if (maybeSelection.isEmpty()) {
            return "admin_provider_selection";
        }
        if (maybeProvider.isEmpty()) {
            return "unsupported_provider_mapping";
        }
        if (!maybeProvider.get().configured()) {
            return "backend_provider_configuration";
        }
        return "none";
    }

    private String memberImpact(CanonicalMemberState state, String capabilityImpact) {
        return switch (state) {
            case READY -> capabilityImpact == null || capabilityImpact.isBlank()
                    ? definition.label() + " are available through Weave."
                    : capabilityImpact;
            case DISABLED -> definition.label() + " are disabled by workspace policy.";
            case DEGRADED -> definition.label() + " are degraded. Ask an admin to review Workspace Health.";
            case POLICY_BLOCKED -> definition.label() + " are blocked by your role or group policy. Ask an admin if you need access.";
            case UNAVAILABLE -> definition.label() + " are unavailable for this workspace right now.";
            case MISCONFIGURED -> definition.label() + " are not ready for members. Ask an admin to review Workspace Health.";
            case UNSUPPORTED -> definition.label() + " requested capability is unsupported and failed closed.";
        };
    }

    private boolean providerKeyMatches(ProviderStatusResponse provider, String providerKey) {
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return provider.providerKey().equals(providerKey)
                || provider.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private CanonicalMemberState worst(CanonicalMemberState current, CanonicalMemberState candidate) {
        return rank(candidate) > rank(current) ? candidate : current;
    }

    private int rank(CanonicalMemberState state) {
        return switch (state) {
            case READY -> 0;
            case DEGRADED -> 1;
            case DISABLED -> 2;
            case MISCONFIGURED -> 3;
            case UNAVAILABLE -> 4;
            case POLICY_BLOCKED -> 5;
            case UNSUPPORTED -> 6;
        };
    }

    private String safeIdentifier(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String safe = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
        return safe.isBlank() ? fallback : safe;
    }
}
