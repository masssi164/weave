package com.massimotter.weave.backend.service;



import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;


import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.admin.AdminAuditEventResponse;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
import com.massimotter.weave.backend.model.admin.AdminCurrentActorResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.capability.api.EffectivePolicyResponse;
import com.massimotter.weave.backend.capability.api.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.capability.api.EffectivePolicySimulationResponse;
import com.massimotter.weave.backend.model.admin.GoLiveReadinessResponse;
import com.massimotter.weave.backend.model.admin.PlatformIdentityReadinessCardResponse;
import com.massimotter.weave.backend.model.admin.PlatformIdentityReadinessResponse;
import com.massimotter.weave.backend.model.admin.McpServerBindingResponse;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestResponse;
import com.massimotter.weave.backend.model.admin.ProviderSelectionRequest;
import com.massimotter.weave.backend.model.admin.ProviderSelectionResponse;
import com.massimotter.weave.backend.model.admin.RcEvidenceGateReadinessResponse;
import com.massimotter.weave.backend.model.admin.ReleaseClaimControlResponse;
import com.massimotter.weave.backend.model.admin.SecretRefResponse;
import com.massimotter.weave.backend.model.admin.SuiteDomainReadinessResponse;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.api.ProviderRegistryResponse;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.discovery.admin.SuiteDomainDefinition;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AdminControlPlaneService {

    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "available",
            "disabled_by_policy",
            "not_configured",
            "degraded",
            "unavailable",
            "coming_later");


    private static final Set<String> SIMULATION_ROLES = Set.of("owner", "admin", "operator", "member", "guest");
    private static final Set<String> SIMULATION_GROUPS = Set.of(
            "/owners",
            "/admins",
            "/members",
            "/guests",
            "/capabilities/weaver");
    private static final Map<String, List<String>> SIMULATION_GROUP_CAPABILITIES = Map.of(
            "/capabilities/weaver", List.of("agent-runtime.entitled"));
    private static final Set<String> SIMULATION_KNOWN_CAPABILITIES = Set.of(
            "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
            "boards.read", "boards.update_task", "meetings.join", "meetings.host", "documents.view", "documents.edit",
            "decisions.read", "decisions.record", "manuals.read", "manuals.admin", "release_evidence.read", "release_evidence.manage",
            "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure", "operator.support_bundle.create",
            "agent-runtime.entitled");

    private final ProviderCategoryCatalog providerCategoryCatalog;
    private final ProviderCapabilityContracts providerCapabilityContracts;
    private final ProviderRegistry providerRegistry;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ProviderBindingRepository providerBindings;
    private final OrganizationIdentityContextResolver identityContexts;
    private final Clock clock;

    @Autowired
    public AdminControlPlaneService(
            ProviderCategoryCatalog providerCategoryCatalog,
            ProviderCapabilityContracts providerCapabilityContracts,
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            AuditEventPublisher auditEventPublisher,
            OrganizationIdentityContextResolver identityContexts,
            ObjectProvider<ProviderBindingRepository> providerBindings) {
        this(
                providerCategoryCatalog, providerCapabilityContracts,
                providerRegistry,
                workspaceCapabilityService,
                providerSelectionRepository,
                auditEventPublisher,
                Clock.systemUTC(),
                identityContexts,
                providerBindings.getObject());
    }

    AdminControlPlaneService(
            ProviderCategoryCatalog providerCategoryCatalog,
            ProviderCapabilityContracts providerCapabilityContracts,
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this(
                providerCategoryCatalog, providerCapabilityContracts,
                providerRegistry,
                workspaceCapabilityService,
                providerSelectionRepository,
                auditEventPublisher,
                clock,
                OrganizationIdentityContextResolver.defaults());
    }

    AdminControlPlaneService(
            ProviderCategoryCatalog providerCategoryCatalog,
            ProviderCapabilityContracts providerCapabilityContracts,
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock,
            OrganizationIdentityContextResolver identityContexts) {
        this(providerCategoryCatalog, providerCapabilityContracts, providerRegistry, workspaceCapabilityService, providerSelectionRepository,
                auditEventPublisher, clock,
                identityContexts, null);
    }

    AdminControlPlaneService(
            ProviderCategoryCatalog providerCategoryCatalog,
            ProviderCapabilityContracts providerCapabilityContracts,
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock,
            OrganizationIdentityContextResolver identityContexts,
            ProviderBindingRepository providerBindings) {
        this.providerCategoryCatalog = java.util.Objects.requireNonNull(providerCategoryCatalog);
        this.providerCapabilityContracts = java.util.Objects.requireNonNull(providerCapabilityContracts);
        this.providerRegistry = java.util.Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.workspaceCapabilityService = java.util.Objects.requireNonNull(
                workspaceCapabilityService, "workspaceCapabilityService");
        this.providerSelectionRepository = java.util.Objects.requireNonNull(
                providerSelectionRepository, "providerSelectionRepository");
        this.auditEventPublisher = java.util.Objects.requireNonNull(
                auditEventPublisher, "auditEventPublisher");
        this.providerBindings = providerBindings;
        this.identityContexts = java.util.Objects.requireNonNull(
                identityContexts, "identityContexts");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public AdminControlPlaneResponse overview(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "overview");
        OrganizationIdentityContext actor = identityContexts.resolve(jwt);
        ProviderRegistryResponse registry = providerRegistry.status();
        PlatformIdentityReadinessResponse identityReadiness = platformIdentityReadiness(registry, jwt);
        List<SuiteDomainReadinessResponse> suiteReadiness = suiteDomainReadiness(registry);
        return new AdminControlPlaneResponse(
                "admin-control-plane-v1",
                new AdminCurrentActorResponse(actor.accountId(), actor.organizationId(), actor.issuer(), actor.roles()),
                actor.organizationId(),
                organizationName(jwt),
                "Keycloak platform security",
                registry.providerConfigSource(),
                registry.bootstrapDefaultsAreSuggestionsOnly(),
                registry.backendOwnedFacades(),
                true,
                registry.supportSafe(),
                false,
                Instant.now(clock),
                registry.categories(),
                registry.selectedProviderMappings().stream()
                        .map(selection -> toSelectionResponse(selection, false, readinessFor(selection.category(), registry)))
                        .toList(),
                whitelist(jwt),
                identityReadiness,
                suiteReadiness,
                goLiveReadiness(identityReadiness, suiteReadiness),
                secretRefs(registry),
                mcpServerBindings(registry),
                Map.ofEntries(
                        Map.entry("providers", "/api/admin/providers/status"),
                        Map.entry("policy", "/api/admin/policies/capability-whitelist"),
                        Map.entry("audit", "/api/admin/audit/events"),
                        Map.entry("readinessTest", "/api/admin/providers/readiness-tests"),
                        Map.entry("providerReplacementDryRun", "/api/admin/providers/replacements/dry-run"),
                        Map.entry("identityReadiness", "/api/admin/platform/identity/readiness"),
                        Map.entry("effectivePolicySimulation", "/api/admin/policies/effective/simulations"),
                        Map.entry("providerSelections", "/api/admin/providers/selections"),
                        Map.entry("suiteReadiness", "/api/admin/control-plane#suiteDomainReadiness"),
                        Map.entry("mcpServerBindings", "/api/admin/control-plane#mcpServerBindings"),
                        Map.entry("agentRuntimes", "/api/admin/agent-runtimes/{personRef}")));
    }

    public PlatformIdentityReadinessResponse platformIdentityReadiness(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "platform-identity-security", "readiness");
        return platformIdentityReadiness(providerRegistry.status(), jwt);
    }

    public ProviderSelectionResponse selectProvider(ProviderSelectionRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "admin-control-plane", "select-provider");
        ProviderSelection selection = validateProviderSelection(request, jwt);
        rejectSelectionConflictingWithActiveBinding(selection, jwt);
        boolean dryRun = request.dryRun();
        if ("files".equals(selection.category()) && !dryRun) {
            throw new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "files-provider-activation-unverified",
                    "Files provider activation requires a verified organization-scoped replacement run.",
                    Map.of("category", "files", "supportSafe", true));
        }
        ProviderSelection applied = dryRun ? selection : providerSelectionRepository.save(selection);
        if (!dryRun) {
            auditEventPublisher.publish(new AuditEvent(
                    organizationId(jwt),
                    "admin-control-plane",
                    actorRef(jwt),
                    "provider-selection",
                    AuditAction.ADMIN_POLICY_UPDATED,
                    Instant.now(clock),
                    "provider-selection-" + selection.category() + "-" + Instant.now(clock).toEpochMilli(),
                    AuditRedactionLevel.SECRET_REDACTED,
                    Map.ofEntries(
                            Map.entry("category", selection.category()),
                            Map.entry("providerKey", selection.providerKey()),
                            Map.entry("choiceModel", selection.choiceModel()),
                            Map.entry("secretRef", safeSecretRef(selection.secretRef())),
                            Map.entry("reason", safeText(request.reason())),
                            Map.entry("providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE),
                            Map.entry("migrationDryRunRequired", selection.migrationDryRunRequired()),
                            Map.entry("lossyMappingNoteCount", selection.lossyMappingNotes().size()),
                            Map.entry("replacementApplyAttempt", true),
                            Map.entry("dryRunEvidenceRequired", selection.migrationDryRunRequired()),
                            Map.entry("token", "not-stored"))));
        }
        return toSelectionResponse(applied, dryRun,
                "files".equals(selection.category())
                        ? "blocked-unverified"
                        : dryRun ? "dry_run_valid" : "admin_selected_pending_readiness");
    }

    private void rejectSelectionConflictingWithActiveBinding(ProviderSelection selection, Jwt jwt) {
        if (providerBindings == null || !Set.of("files", "chat", "calendar").contains(selection.category())) {
            return;
        }
        providerBindings.current(organizationId(jwt), selection.category()).ifPresent(binding -> {
            if (!binding.adapterKey().equals(selection.providerKey())) {
                throw new ApiErrorException(
                        HttpStatus.CONFLICT,
                        "provider-selection-conflicts-active-binding",
                        "Category selection cannot replace the active organization provider binding.",
                        Map.of("category", selection.category(), "supportSafe", true));
            }
        });
    }

    public EffectivePolicyResponse effectivePolicy(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "effective-policy");
        return workspaceCapabilityService.effectivePolicySnapshot(jwt, "organization");
    }

    public EffectivePolicySimulationResponse simulateEffectivePolicy(EffectivePolicySimulationRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "effective-policy-simulation");
        List<String> deniedInputs = Stream.of(
                        deniedInputCodes(request == null ? null : request.roles(), "role", SIMULATION_ROLES),
                        deniedInputCodes(request == null ? null : request.groups(), "group", SIMULATION_GROUPS),
                        deniedInputCodes(request == null ? null : request.requestedCapabilities(), "capability", SIMULATION_KNOWN_CAPABILITIES))
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .toList();
        List<String> roles = normalizedKnownValues(request == null ? null : request.roles(), SIMULATION_ROLES);
        List<String> groups = normalizedKnownValues(request == null ? null : request.groups(), SIMULATION_GROUPS);
        List<String> requestedCapabilities = normalizedKnownValues(request == null ? null : request.requestedCapabilities(), SIMULATION_KNOWN_CAPABILITIES);
        boolean failClosed = !deniedInputs.isEmpty();
        LinkedHashSet<String> grants = new LinkedHashSet<>();
        if (!failClosed) {
            if (roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin"))) {
                grants.addAll(List.of(
                        "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
                        "boards.read", "boards.update_task", "meetings.join", "meetings.host", "documents.view", "documents.edit",
                        "decisions.read", "decisions.record", "manuals.read", "manuals.admin", "release_evidence.read", "release_evidence.manage",
                        "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure"));
            }
            if (roles.contains("operator")) {
                grants.addAll(List.of("admin_control_plane.readiness_read", "operator.support_bundle.create", "release_evidence.read", "manuals.admin", "manuals.read"));
            }
            if (roles.contains("member")) {
                grants.addAll(List.of("chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "boards.read", "meetings.join", "documents.view", "decisions.read", "manuals.read", "release_evidence.read"));
            }
            for (String group : groups) {
                grants.addAll(SIMULATION_GROUP_CAPABILITIES.getOrDefault(group, List.of()));
            }
        }
        List<EffectivePolicySimulationResponse.CapabilityState> capabilityStates = requestedCapabilities.stream()
                .map(capability -> simulationState(capability, grants, failClosed))
                .toList();
        String auditRef = "effective-policy-simulation-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "effective-policy-simulation",
                AuditAction.EFFECTIVE_POLICY_SIMULATED,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "subjectProvided", request != null && request.subject() != null && !request.subject().isBlank(),
                        "organizationProvided", request != null && request.organizationId() != null && !request.organizationId().isBlank(),
                        "roleCount", roles.size(),
                        "groupCount", groups.size(),
                        "requestedCapabilityCount", requestedCapabilities.size(),
                        "unknownInputCount", deniedInputs.size(),
                        "unknownInputsFailClosed", failClosed,
                        "supportSafe", true,
                        "reasonProvided", request != null && request.reason() != null && !request.reason().isBlank())));
        return new EffectivePolicySimulationResponse(
                safeSimulationIdentityRef(request == null ? null : request.subject()),
                request == null || request.organizationId() == null || request.organizationId().isBlank()
                        ? organizationId(jwt)
                        : safeText(request.organizationId()),
                roles,
                groups,
                requestedCapabilities,
                grants.stream().filter(requestedCapabilities::contains).sorted().toList(),
                deniedInputs,
                failClosed,
                true,
                true,
                capabilityStates,
                failClosed
                        ? List.of("Map unknown roles, groups, or capabilities before provider activation.")
                        : List.of("Review member-visible states before applying provider or realm changes."),
                List.of(auditRef));
    }

    public CapabilityWhitelistResponse whitelist(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "read-capability-whitelist");
        WorkspaceCapabilityPolicyResponse policy = workspaceCapabilityService.policySnapshot(jwt);
        Map<String, List<String>> profileCapabilities = new LinkedHashMap<>();
        profileCapabilities.put("workspace-admin", List.of(
                "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
                "boards.read", "boards.update_task", "admin.provider.configure", "admin.policy.edit"));
        profileCapabilities.put("workspace-operator", List.of(
                "admin_control_plane.readiness_read", "operator.support_bundle.create", "release_evidence.read", "manuals.admin"));
        profileCapabilities.put("member-default", List.of(
                "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "boards.read"));
        profileCapabilities.put("guest-deny-default", List.of());
        profileCapabilities.put("group:/capabilities/weaver", List.of("agent-runtime.entitled"));
        return new CapabilityWhitelistResponse(
                policy.denyByDefault(),
                false,
                STABLE_MEMBER_IMPACT_STATES,
                profileCapabilities,
                policy.profileKeys(),
                policy.grantedCapabilities(),
                "backend-control-plane");
    }

    public void updateWhitelist(CapabilityWhitelistUpdateRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.policy.edit", "admin-control-plane", "update-capability-whitelist");
        if (request == null || request.profileKey() == null || request.profileKey().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "capability-whitelist-invalid",
                    "Capability whitelist update requires a profile key.",
                    Map.of("reason", "profileKey is required"));
        }
        enforceLastAdminGuard(request);
        throw new ApiErrorException(
                HttpStatus.CONFLICT,
                "capability-whitelist-read-only",
                "Capability profiles cannot be changed through this endpoint. No policy was changed.",
                Map.of("readOnly", true, "supportSafe", true));
    }


    public ProviderReadinessTestResponse testProviderReadiness(ProviderReadinessTestRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "provider-readiness-test");
        if (request == null || request.providerKey() == null || request.providerKey().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-readiness-test-invalid",
                    "Provider readiness test requires a provider key.",
                    Map.of("reason", "providerKey is required"));
        }
        String providerKey = request.providerKey().trim();
        ProviderStatusResponse status = providerRegistry.status().providers().stream()
                .filter(provider -> providerKeyMatches(provider, providerKey))
                .findFirst()
                .orElseThrow(() -> new ApiErrorException(
                        HttpStatus.NOT_FOUND,
                        "provider-not-found",
                        "Provider key is not registered in the backend control plane.",
                        Map.of("providerKey", providerKey)));
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "provider-readiness-test",
                AuditAction.PROVIDER_READINESS_TESTED,
                Instant.now(clock),
                "provider-test-" + providerKey + "-" + Instant.now(clock).toEpochMilli(),
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "providerKey", providerKey,
                        "module", status.module().contractName(),
                        "testKind", safeText(request.testKind()),
                        "secretRef", safeSecretRef(request.secretRef()),
                        "state", status.state().contractName(),
                        "readiness", status.readiness(),
                        "supportSafe", status.supportSafe(),
                        "apiSecret", "not-stored")));
        return new ProviderReadinessTestResponse(
                providerKey,
                status.state().contractName(),
                status.readiness(),
                true,
                true,
                false,
                Map.of(
                        "providerKey", providerKey,
                        "backendAdapterKey", status.providerKey(),
                        "module", status.module().contractName(),
                        "configured", status.configured(),
                        "secretsReturned", false,
                        "rawProviderErrorsReturned", false,
                        "secretRef", safeSecretRef(request.secretRef())));
    }

    public List<AdminAuditEventResponse> auditEvents(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "read-audit-events");
        return auditEventResponses(auditEventPublisher.events());
    }

    private List<AdminAuditEventResponse> auditEventResponses(List<AuditEvent> events) {
        return events.stream()
                .map(event -> new AdminAuditEventResponse(
                        event.tenantId(),
                        event.actorRef(),
                        event.sourceRef(),
                        event.action().wireName(),
                        event.occurredAt(),
                        event.idempotencyKey(),
                        event.redactionLevel().name().toLowerCase(Locale.ROOT),
                        event.payload()))
                .toList();
    }

    private PlatformIdentityReadinessResponse platformIdentityReadiness(ProviderRegistryResponse registry, Jwt jwt) {
        boolean configured = jwt != null && jwt.getIssuer() != null;
        boolean enabled = configured;
        boolean failClosed = true;
        boolean supportSafe = true;
        CapabilityWhitelistResponse whitelist = whitelist(jwt);

        List<PlatformIdentityReadinessCardResponse> cards = List.of(
                readinessCard(
                        "realm-import",
                        "Keycloak realm baseline readiness",
                        configured ? "ready" : "admin-action-required",
                        "Static Keycloak state comes from the canonical realm import; dynamic human identity lifecycle remains owned by Weave Server.",
                        configured ? "ready" : "degraded",
                        "Render and verify the canonical realm baseline, then apply only the bounded post-import migration through the deployment gate.",
                        List.of("Verify the realm import and bounded migration receipts"),
                        List.of("keycloak-realm-baseline", "keycloak-post-import-migration"),
                        Map.of(
                                "configured", configured,
                                "enabled", enabled,
                                "secretsReturned", false,
                                "rawProviderErrorsReturned", false)),
                readinessCard(
                        "federation-protocol-readiness",
                        "OIDC and SAML federation readiness",
                        configured ? "ready" : "admin-action-required",
                        "OIDC and SAML posture is summarized by backend contract evidence; issuer, entity, client, redirect, certificate, and secret details are redacted.",
                        configured ? "ready" : "degraded",
                        "Confirm OIDC scopes and SAML attribute/nameID mappings through the canonical realm baseline and bounded migration, never through browser JavaScript.",
                        List.of("Validate OIDC scope mappings", "Validate SAML immutable subject and signed assertion requirements", "Keep federation secrets as SecretRef handles only"),
                        List.of("identity-federation-contract", "secretref-boundary"),
                        Map.of(
                                "oidcConceptCovered", true,
                                "samlConceptCovered", true,
                                "clientDetailsRedacted", true,
                                "samlMetadataRedacted", true,
                                "clientSecretsReturned", false,
                                "configured", configured)),
                readinessCard(
                        "provisioning-source-readiness",
                        "SCIM, LDAP, and AD provisioning readiness",
                        "admin-action-required",
                        "LDAP and Active Directory are configured only as Keycloak User Federation; OIDC and SAML sources are configured only as Keycloak identity brokers.",
                        "degraded",
                        "Choose the authoritative source of truth and prove immutable anchors such as SCIM externalId, Entra object ID, LDAP objectGUID, or AD objectSid before member go-live.",
                        List.of("Record source-of-truth for users and groups", "Run fixture-backed provisioning readiness checks", "Keep email addresses out of primary identity keys"),
                        List.of("scim-lifecycle-contract", "ldap-ad-anchor-contract", "fixture-backed-readiness"),
                        Map.of(
                                "scimConceptCovered", true,
                                "ldapConceptCovered", true,
                                "activeDirectoryConceptCovered", true,
                                "liveScimSyncClaimed", false,
                                "liveLdapAdConnectorClaimed", false,
                                "emailPrimaryKeyAllowed", false)),
                readinessCard(
                        "roles-groups-mapping",
                        "Roles and groups mapping",
                        whitelist.denyByDefault() ? "ready" : "policy-blocked",
                        "Known roles and groups map into canonical Weave capability profiles; unknown inputs deny by default.",
                        whitelist.denyByDefault() ? "ready" : "policy-blocked",
                        "Map unknown roles/groups before activation and keep email out of primary identity keys.",
                        List.of("Review effective policy simulation", "Map unknown identity inputs before they affect members"),
                        List.of("effective-policy-simulation", "deny-by-default-policy"),
                        Map.of(
                                "denyByDefault", whitelist.denyByDefault(),
                                "profileCount", whitelist.profileCapabilities().size(),
                                "stableMemberImpactStateCount", whitelist.stableMemberImpactStates().size(),
                                "emailPrimaryKeyAllowed", false)),
                readinessCard(
                        "login-readiness",
                        "Login readiness",
                        loginReadinessState(configured, enabled, failClosed),
                        "Member login is exposed only as product-level availability; provider endpoints and raw auth errors stay out of member flows.",
                        configured && enabled ? "ready" : "degraded",
                        "Keep member sign-in fail-closed until the fixed Keycloak issuer and backend readiness evidence are present.",
                        List.of("Verify backend auth facade readiness", "Confirm member client shows only stable capability states"),
                        List.of("member-boundary", "backend-auth-facade"),
                        Map.of(
                                "memberClientMayConfigureProvider", false,
                                "providerEndpointsReturned", false,
                                "rawAuthErrorsReturned", false,
                                "failClosed", failClosed)),
                readinessCard(
                        "deprovisioning-readiness",
                        "Deprovisioning readiness",
                        "admin-action-required",
                        "Offboarding revokes sessions and access projections before disabling the Keycloak user; hard deletion is unavailable.",
                        "degraded",
                        "Run a deprovisioning preview before disabling identities and keep retained owner/admin identity keys independent of email.",
                        List.of("Preview access and session revocation", "Review ownership/content references", "Verify retained admin before deprovisioning"),
                        List.of("identity-lifecycle-offboarding", "retained-admin-guard", "support-safe-audit"),
                        Map.of(
                                "accessRevocationCovered", true,
                                "sessionRevocationCovered", true,
                                "ownershipTransferCovered", true,
                                "contentReferenceReviewCovered", true,
                                "hardDeleteAvailable", false,
                                "providerPayloadDetailsReturned", false)),
                readinessCard(
                        "break-glass-readiness",
                        "Break-glass readiness",
                        whitelist.denyByDefault() && failClosed ? "ready" : "policy-blocked",
                        "Recovery access requires retained admin proof, guarded copy, and audit evidence without exposing provider internals.",
                        whitelist.denyByDefault() && failClosed ? "ready" : "policy-blocked",
                        "Keep at least one immutable owner/admin recovery identity and audit every break-glass decision before provider apply.",
                        List.of("Verify retained owner/admin subject refs", "Store guarded recovery copy as SecretRef or evidence ref", "Review support-safe audit refs"),
                        List.of("last-admin-guard", "break-glass-recovery-contract", "support-safe-audit"),
                        Map.of(
                                "retainedAdminProofRequired", true,
                                "guardedCopyRequired", true,
                                "auditRequired", true,
                                "emailRecoveryKeyAllowed", false,
                                "rawRecoveryPayloadReturned", false)),
                readinessCard(
                        "service-principal-readiness",
                        "Service principal readiness",
                        "admin-action-required",
                        "Non-human actors must have stable identity, least-privilege scopes, expiry or rotation expectations, and audit identity before activation.",
                        "degraded",
                        "Register service principals as non-human identities with scoped capabilities, rotation evidence, and support-safe audit refs before use.",
                        List.of("Declare non-human actor owner", "Limit scopes through capability policy", "Set expiry or rotation evidence", "Verify audit identity"),
                        List.of("service-principal-contract", "capability-whitelist", "support-safe-audit"),
                        Map.of(
                                "nonHumanActorCovered", true,
                                "scopeReviewRequired", true,
                                "expiryOrRotationRequired", true,
                                "auditIdentityRequired", true,
                                "secretMaterialReturned", false)),
                readinessCard(
                        "policy-readiness",
                        "Policy readiness",
                        whitelist.denyByDefault() && failClosed ? "ready" : "policy-blocked",
                        "Capability policy is the gate between provider claims and Weave product access.",
                        whitelist.denyByDefault() && failClosed ? "ready" : "policy-blocked",
                        "Retain deny-by-default policy and last-owner recovery capabilities before member access changes.",
                        List.of("Retain admin.policy.edit for workspace-admin", "Review policy simulation before member access changes"),
                        List.of("capability-whitelist", "last-admin-guard"),
                        Map.of(
                                "denyByDefault", whitelist.denyByDefault(),
                                "failClosed", failClosed,
                                "normalMembersMayAuthorPolicy", whitelist.normalMembersMayAuthorPolicy())));
        String overallState = aggregateIdentityReadiness(cards);
        return new PlatformIdentityReadinessResponse(
                "platform-identity-readiness-v1",
                "keycloak",
                overallState,
                supportSafe,
                true,
                true,
                false,
                true,
                registry.generatedAt(),
                List.of("ready", "degraded", "policy-blocked", "admin-action-required", "coming_later", "disabled"),
                cards,
                identityNextActions(overallState),
                Map.of(
                        "overview", "/api/admin/control-plane",
                        "readiness", "/api/admin/platform/identity/readiness",
                        "realmBaseline", "operator://keycloak-realm-baseline",
                        "effectivePolicySimulation", "/api/admin/policies/effective/simulations"),
                Map.ofEntries(
                        Map.entry("contractOptional", false),
                        Map.entry("versionSkewSafe", true),
                        Map.entry("memberClientMayConfigurePlatformSecurity", false),
                        Map.entry("diagnosticsRedacted", true),
                        Map.entry("runtimeSelectionAvailable", false),
                        Map.entry("configured", configured),
                        Map.entry("enabled", enabled),
                        Map.entry("secretsReturned", false),
                        Map.entry("rawKeycloakErrorsReturned", false),
                        Map.entry("liveScimSyncClaimed", false),
                        Map.entry("liveLdapAdConnectorClaimed", false),
                        Map.entry("fixtureBackedReadiness", true)));
    }

    private PlatformIdentityReadinessCardResponse readinessCard(
            String key,
            String label,
            String state,
            String summary,
            String memberImpact,
            String remediation,
            List<String> nextActions,
            List<String> evidenceRefs,
            Map<String, Object> diagnostics) {
        return new PlatformIdentityReadinessCardResponse(
                key,
                label,
                state,
                summary,
                memberImpact,
                remediation,
                nextActions,
                evidenceRefs,
                diagnostics);
    }

    private String loginReadinessState(boolean configured, boolean enabled, boolean failClosed) {
        if (!enabled) {
            return "admin-action-required";
        }
        if (!configured) {
            return "admin-action-required";
        }
        return failClosed ? "ready" : "policy-blocked";
    }

    private List<String> identityNextActions(String overallState) {
        return switch (overallState) {
            case "ready" -> List.of("Monitor audit/readiness transitions and keep support bundles redacted.");
            case "policy-blocked" -> List.of("Resolve policy blockers, unknown mappings, or last-owner guard failures before member lifecycle changes.");
            case "disabled" -> List.of("Restore the fixed Keycloak platform-security runtime before allowing member sign-in.");
            default -> List.of(
                    "Verify the canonical realm import and bounded post-import migration receipts.",
                    "Resolve admin-action-required cards before treating sign-in as ready.",
                    "Verify member clients expose only product-level states.");
        };
    }

    private String aggregateIdentityReadiness(List<PlatformIdentityReadinessCardResponse> cards) {
        List<String> states = cards.stream().map(PlatformIdentityReadinessCardResponse::state).toList();
        if (states.contains("policy-blocked")) {
            return "policy-blocked";
        }
        if (states.contains("admin-action-required")) {
            return "admin-action-required";
        }
        if (states.contains("degraded")) {
            return "degraded";
        }
        if (states.stream().allMatch("disabled"::equals)) {
            return "disabled";
        }
        return "ready";
    }

    private ProviderSelection validateProviderSelection(ProviderSelectionRequest request, Jwt jwt) {
        if (request == null || request.category() == null || request.category().isBlank()
                || request.providerKey() == null || request.providerKey().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-invalid",
                    "Provider selection requires category and provider key.",
                    Map.of("reason", "category and providerKey are required"));
        }
        String category = request.category().trim();
        String providerKey = request.providerKey().trim();
        if (providerCategoryCatalog.category(category).isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-category-unknown",
                    "Provider category is not part of the Weave canonical control-plane contract.",
                    Map.of("category", category));
        }
        if (!providerMatchesCategory(providerKey, category)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-category-mismatch",
                    "Provider key is not registered as a support-safe candidate for the selected category.",
                    Map.of("category", category, "providerKey", providerKey));
        }
        return new ProviderSelection(
                category,
                providerKey,
                selectionChoiceModel(request.choiceModel()),
                selectionSecretRef(request.secretRef()),
                actorRef(jwt),
                Instant.now(clock),
                !request.dryRun(),
                true,
                requiresMigrationDryRun(request),
                safeLossyMappingNotes(request.lossyMappingNotes()));
    }

    private void enforceLastAdminGuard(CapabilityWhitelistUpdateRequest request) {
        if (!"workspace-admin".equals(request.profileKey().trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        List<String> capabilities = safeCapabilities(request.capabilityKeys());
        if (capabilities.contains("admin.policy.edit")) {
            return;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "last-admin-guard",
                "Workspace admin policy updates must retain at least one admin policy editor.",
                Map.of(
                        "profileKey", "workspace-admin",
                        "requiredCapability", "admin.policy.edit",
                        "supportSafe", true));
    }





    private boolean providerMatchesCategory(String providerKey, String category) {
        boolean registeredCandidate = providerRegistry.status().providers().stream()
                .filter(provider -> providerCategoryCatalog.providerMatchesCategory(provider, category))
                .anyMatch(provider -> providerKeyMatches(provider, providerKey));
        if (registeredCandidate) {
            return true;
        }
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return providerCapabilityContracts.providerCandidates(category).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private boolean providerKeyMatches(ProviderStatusResponse provider, String providerKey) {
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return provider.providerKey().equals(providerKey)
                || provider.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private String selectionChoiceModel(String value) {
        if (value == null || value.isBlank()) {
            return "recommended_self_hosted_default";
        }
        String trimmed = value.trim();
        if (trimmed.equals("recommended_self_hosted_default")
                || trimmed.equals("external_existing_provider")
                || trimmed.equals("managed_cloud_provider")
                || trimmed.equals("hybrid_composite")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "provider-selection-choice-model-invalid",
                "Provider selection choice model is not part of the Weave provider choice contract.",
                Map.of("choiceModel", "invalid-choice-model-redacted"));
    }

    private String selectionSecretRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateSecretRef(value, "provider-selection-secretref-invalid", "Provider selections may reference credentials only through SecretRef URIs.");
    }


    private String validateSecretRef(String value, String code, String message) {
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                code,
                message,
                Map.of("secretRef", "invalid-secret-ref-redacted"));
    }


    private boolean requiresMigrationDryRun(ProviderSelectionRequest request) {
        return request.lossyMappingNotes() != null && !request.lossyMappingNotes().isEmpty()
                || "external_existing_provider".equals(request.choiceModel())
                || "managed_cloud_provider".equals(request.choiceModel())
                || "hybrid_composite".equals(request.choiceModel());
    }

    private List<String> safeLossyMappingNotes(List<String> notes) {
        if (notes == null) {
            return List.of();
        }
        return notes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safeText)
                .distinct()
                .limit(10)
                .toList();
    }

    private ProviderSelectionResponse toSelectionResponse(ProviderSelection selection, boolean dryRun, String readiness) {
        return new ProviderSelectionResponse(
                selection.category(),
                selection.providerKey(),
                selection.choiceModel(),
                selection.secretRef(),
                selection.selectedBy(),
                selection.selectedAt(),
                selection.applied() && !dryRun,
                dryRun,
                true,
                !selection.applied() || dryRun,
                selection.migrationDryRunRequired(),
                selection.lossyMappingNotes(),
                readiness,
                providerSelectionRepository.persistencePosture(),
                selection.selectedAt());
    }

    private List<SuiteDomainReadinessResponse> suiteDomainReadiness(ProviderRegistryResponse registry) {
        return List.of(
                suiteDomain(SuiteDomainDefinition.FILES_DOCS, registry,
                        "backend-owned file/document facade with guarded editor sessions",
                        List.of("Export manifests required before provider replacement", "Document editing remains guarded until WOPI session evidence is fresh"),
                        "Select/test files and documents providers, then attach support-safe export/delete evidence."),
                suiteDomain(SuiteDomainDefinition.BOARDS_TASKS, registry,
                        "local workspace writes; provider sync/write promotion gated by contract evidence",
                        List.of("Provider-write apply is refused until read-sync, conflict, and rollback evidence pass", "Keyboard create/move/complete flows are required before member promotion"),
                        "Verify board/task workspace contract, accessible workflows, conflict states, and audit events."),
                suiteDomain(SuiteDomainDefinition.CALENDAR_MEETINGS, registry,
                        "workspace/team/channel calendar facade; private personal calendars blocked",
                        List.of("Secret-free setup metadata only", "Private calendar ingestion and credential profile downloads are out of scope"),
                        "Confirm workspace/team/channel event readiness and keep private-calendar setup blocked."));
    }

    private SuiteDomainReadinessResponse suiteDomain(
            SuiteDomainDefinition definition,
            ProviderRegistryResponse registry,
            String sourceOfTruthMode,
            List<String> portabilityNotes,
            String nextAction) {
        List<String> readinessStates = definition.providerCategoryKeys().stream()
                .map(category -> readinessFor(category, registry))
                .filter(state -> !"unknown".equals(state))
                .toList();
        String adminReadiness = aggregateDomainReadiness(readinessStates);
        return new SuiteDomainReadinessResponse(
                definition.domain(),
                definition.label(),
                adminReadiness,
                memberStateForAdminReadiness(adminReadiness),
                selectedAdapterPosture(definition.providerCategoryKeys(), registry),
                sourceOfTruthMode,
                definition.providerCategoryKeys(),
                definition.canonicalObjectKinds(),
                Stream.concat(definition.readCapabilities().stream(), definition.writeCapabilities().stream()).toList(),
                List.of("support-safe-error-codes-only", "raw-provider-bodies-redacted", "credential-bearing-urls-blocked"),
                portabilityNotes,
                List.of("audit://suite/" + definition.domain() + "/readiness"),
                nextAction,
                true,
                true,
                false,
                Map.of(
                        "providerCategoryCount", definition.providerCategoryKeys().size(),
                        "supportSafe", true,
                        "rawProviderConfigReturned", false,
                        "memberProviderSetupControlsReturned", false));
    }

    private String aggregateDomainReadiness(List<String> states) {
        if (states.isEmpty()) {
            return "not_configured";
        }
        if (states.contains("misconfigured") || states.contains("admin-action-required")) {
            return "admin-action-required";
        }
        if (states.contains("policy-blocked") || states.contains("disabled")) {
            return "disabled";
        }
        if (states.contains("degraded")) {
            return "degraded";
        }
        if (states.stream().allMatch(state -> state.equals("ready") || state.equals("configured"))) {
            return "ready";
        }
        return "admin-action-required";
    }

    private String memberStateForAdminReadiness(String state) {
        return switch (state) {
            case "ready", "configured" -> "available";
            case "disabled", "policy-blocked" -> "disabled_by_policy";
            case "not_configured" -> "not_configured";
            case "unsupported" -> "unavailable";
            default -> "degraded";
        };
    }

    private String selectedAdapterPosture(List<String> categories, ProviderRegistryResponse registry) {
        return categories.stream()
                .map(category -> category + "=" + registry.selectedProviderMappings().stream()
                        .filter(selection -> selection.category().equals(category))
                        .map(ProviderSelection::providerKey)
                        .findFirst()
                        .orElse("awaiting_admin_selection"))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private GoLiveReadinessResponse goLiveReadiness(
            PlatformIdentityReadinessResponse identityReadiness,
            List<SuiteDomainReadinessResponse> suiteReadiness) {
        List<String> blockers = new ArrayList<>();
        if (!"ready".equals(identityReadiness.overallState())) {
            blockers.add("platform-identity-security:" + identityReadiness.overallState());
        }
        suiteReadiness.stream()
                .filter(domain -> !"ready".equals(domain.adminReadiness()))
                .map(domain -> domain.domain() + ":" + domain.adminReadiness())
                .forEach(blockers::add);
        String state = blockers.isEmpty() ? "ready" : "admin-action-required";
        return new GoLiveReadinessResponse(
                state,
                blockers.isEmpty() ? "available" : "degraded",
                blockers,
                blockers.isEmpty()
                        ? List.of("Invite members only while audit and readiness evidence remains fresh.")
                        : List.of("Resolve listed readiness blockers before member go-live.", "Run effective policy simulation for representative users/groups."),
                List.of("audit://admin-control-plane/go-live-readiness"),
                true,
                false,
                false,
                releaseClaimControl(blockers));
    }

    private ReleaseClaimControlResponse releaseClaimControl(List<String> readinessBlockers) {
        List<String> unresolvedVetoes = new ArrayList<>(readinessBlockers);
        unresolvedVetoes.add("#591-manual-assistive-technology-signoff-open");
        unresolvedVetoes.add("release-owner-rc-decision-required");
        return new ReleaseClaimControlResponse(
                "admin-action-required",
                "v0.1.0-rc.next",
                "specs/weave-specs.lock.json#24c746c674da7d98e5c6abc1f1abac033a8774f2",
                "merged PR release-notes labels and generated draft",
                "support-bundle://admin-health/go-live-redacted-sample",
                "docs/evidence/accessibility/sprint-18-manual-at-blocker.md#591",
                unresolvedVetoes,
                List.of(
                        new RcEvidenceGateReadinessResponse(
                                "pinned-spec-corpus",
                                "Pinned specification corpus",
                                "ready",
                                "fresh",
                                List.of("specs/weave-specs.lock.json"),
                                "Keep the candidate tied to the pinned corpus commit.",
                                false),
                        new RcEvidenceGateReadinessResponse(
                                "sprint-18-manual-at-signoff",
                                "Sprint 18 manual AT signoff (#591)",
                                "admin-action-required",
                                "missing",
                                List.of("https://github.com/masssi164/weave/issues/591", "docs/evidence/accessibility/sprint-18-manual-at-blocker.md"),
                                "Keep public/final release claims blocked until real manual assistive-technology evidence or an accepted issue-linked split exists; Sprint 19 dogfood work may proceed.",
                                true),
                        new RcEvidenceGateReadinessResponse(
                                "conformance-gates",
                                "Conformance and acceptance gates",
                                "admin-action-required",
                                "missing",
                                List.of("./gradlew acceptanceContract", "./gradlew releaseEvidenceCheck"),
                                "Run candidate-head gates and attach sanitized CI evidence.",
                                true),
                        new RcEvidenceGateReadinessResponse(
                                "support-safe-bundle",
                                "Support-safe evidence bundle",
                                "ready",
                                "fresh",
                                List.of("support-bundle://admin-health/go-live-redacted-sample"),
                                "Verify the bundle contains only refs, reason codes, and redacted diagnostics.",
                                false),
                        new RcEvidenceGateReadinessResponse(
                                "accessibility-evidence",
                                "Accessibility evidence",
                                "degraded",
                                "stale",
                                List.of("docs/evidence/accessibility/sprint-18-manual-at-blocker.md", "docs/evidence/weaver-security-privacy-accessibility-report.md"),
                                "Refresh manual AT evidence for admin go-live, Workspace, migration, and Agent Runtime Control lifecycle surfaces before public/final release claims.",
                                true),
                        new RcEvidenceGateReadinessResponse(
                                "release-notes-input",
                                "Release notes input",
                                "configured",
                                "sample_only",
                                List.of("docs/release-notes/unreleased.md"),
                                "Generate release notes from merged PR metadata before RC tagging.",
                                true)));
    }

    private String readinessFor(String category, ProviderRegistryResponse registry) {
        return registry.categories().stream()
                .filter(value -> value.category().equals(category))
                .map(value -> value.readiness().value())
                .findFirst()
                .orElse("unknown");
    }

    private List<McpServerBindingResponse> mcpServerBindings(ProviderRegistryResponse registry) {
        return List.of(new McpServerBindingResponse(
                "weave-domain-tools",
                "Weave governed domain tools",
                "streamable-http",
                "internal://weave-mcp/streamable-http",
                "credentialref://weave/mcp/weave-domain-tools/runtime-token",
                List.of("admin.get_readiness", "weaver.get_runtime_profile_projection", "calendar.search_events", "boards.comment"),
                List.of("weaver.admin_readiness_read", "weaver.runtime_profile_read", "weaver.calendar_read", "weaver.boards_write"),
                true,
                false,
                "disabled-by-default",
                true,
                false,
                false,
                false,
                List.of("audit://weaver/mcp/weave-domain-tools/binding-preview"),
                List.of("Enable only after org policy, runtime grants, Streamable HTTP auth, and approval receipts are configured.")));
    }

    private List<SecretRefResponse> secretRefs(ProviderRegistryResponse registry) {
        return registry.providers().stream()
                .filter(provider -> provider.supportSafeErrorCodes().stream().anyMatch(code -> code.contains("not-configured"))
                        || provider.diagnostics().keySet().stream().anyMatch(key -> key.toLowerCase(Locale.ROOT).contains("secret")
                        || key.toLowerCase(Locale.ROOT).contains("token")
                        || key.toLowerCase(Locale.ROOT).contains("key")))
                .map(provider -> new SecretRefResponse(
                        "secretref://weave/provider/" + provider.providerKey(),
                        provider.providerKey(),
                        provider.module().contractName() + " backend credential",
                        provider.configured(),
                        false,
                        true,
                        false))
                .toList();
    }

    private List<String> normalizedKnownValues(List<String> values, Set<String> knownValues) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(this::safeSimulationInputToken)
                .filter(knownValues::contains)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> deniedInputCodes(List<String> values, String kind, Set<String> knownValues) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        return "invalid-" + kind;
                    }
                    String normalized = value.trim().toLowerCase(Locale.ROOT);
                    if (!safeSimulationInputToken(normalized)) {
                        return "invalid-" + kind;
                    }
                    if (!knownValues.contains(normalized)) {
                        return "unknown-" + kind;
                    }
                    return null;
                })
                .filter(value -> value != null)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean safeSimulationInputToken(String value) {
        return value != null
                && value.matches("(?:[a-z][a-z0-9_.:-]*|(?:/[a-z][a-z0-9_.:-]*)+)");
    }

    private EffectivePolicySimulationResponse.CapabilityState simulationState(
            String capability,
            Set<String> grants,
            boolean failClosed) {
        if (failClosed) {
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "policy-blocked",
                    "unknown-identity-inputs-fail-closed",
                    "Admins must map unknown provider inputs before members receive this capability.");
        }
        if ("agent-runtime.entitled".equals(capability)) {
            if (grants.contains(capability)) {
                return new EffectivePolicySimulationResponse.CapabilityState(
                        capability,
                        "ready",
                        "current-keycloak-entitlement",
                        "The person is currently entitled; ARC still validates cell and profile state independently.");
            }
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "disabled",
                    "agent-runtime-entitlement-required",
                    "Agent Runtime Control remains closed without current Keycloak entitlement.");
        }
        if (grants.contains(capability)) {
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "ready",
                    "granted-by-effective-policy",
                    "Member-visible capability state may be ready if provider readiness also passes.");
        }
        return new EffectivePolicySimulationResponse.CapabilityState(
                capability,
                "policy-blocked",
                "deny-by-default-capability-policy",
                "This capability remains blocked unless a known org role or group grants it.");
    }

    private String safeSimulationIdentityRef(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        String trimmed = value.trim();
        if (trimmed.contains("@") || trimmed.matches("(?i).*(bearer\\s+|xox[baprs]-|secret(ref)?://|https?://|token|secret).*")) {
            return "identity-ref-redacted";
        }
        return safeText(trimmed);
    }

    private List<String> safeCapabilities(List<String> capabilities) {
        if (capabilities == null) {
            return List.of();
        }
        return capabilities.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> value.matches("[a-z][a-z0-9_.-]*"))
                .distinct()
                .sorted()
                .toList();
    }

    private String safeSecretRef(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            return trimmed;
        }
        return "invalid-secret-ref-redacted";
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        return value.trim()
                .replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [redacted]")
                .replaceAll("(?i)xox[baprs]-[A-Za-z0-9-]+", "slack-token-[redacted]")
                .replaceAll("(?i)https?://[^\\s]+", "url-[redacted]")
                .replaceAll("(?i)secret(ref)?://[^\\s]+", "secret-ref-[redacted]");
    }


    private String organizationId(Jwt jwt) {
        return identityContexts.resolve(jwt).organizationId();
    }

    private String organizationName(Jwt jwt) {
        return claim(jwt, "weave_organization_name")
                .or(() -> claim(jwt, "organization_name"))
                .or(() -> claim(jwt, "org_name"))
                .orElse("Weave Dogfood");
    }

    private String actorRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "actor:system";
        }
        return "user:" + jwt.getSubject();
    }

    private Optional<String> claim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return Optional.empty();
        }
        Object raw = jwt.getClaims().get(claimName);
        if (raw instanceof String value && !value.isBlank()) {
            return Optional.of(value.trim());
        }
        return Optional.empty();
    }
}
