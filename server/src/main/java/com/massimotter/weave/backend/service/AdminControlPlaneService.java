package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.FileAuditEventPublisher;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyReport;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunReport;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyProperties;
import com.massimotter.weave.backend.identity.realm.IdentityRealmEvidenceRepository;
import com.massimotter.weave.backend.identity.realm.IdentityRealmLiveApplyAdapter;
import com.massimotter.weave.backend.identity.realm.IdentityRealmProvider;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.admin.AdminAuditEventResponse;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
import com.massimotter.weave.backend.model.admin.AttachExistingPortabilityPlanResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationResponse;
import com.massimotter.weave.backend.model.admin.GoLiveReadinessResponse;
import com.massimotter.weave.backend.model.admin.IdentityProviderReadinessCardResponse;
import com.massimotter.weave.backend.model.admin.IdentityProviderReadinessResponse;
import com.massimotter.weave.backend.model.admin.McpServerBindingResponse;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapRequest;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapResponse;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestResponse;
import com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunResponse;
import com.massimotter.weave.backend.model.admin.ProviderSelectionRequest;
import com.massimotter.weave.backend.model.admin.ProviderSelectionResponse;
import com.massimotter.weave.backend.model.admin.RcEvidenceGateReadinessResponse;
import com.massimotter.weave.backend.model.admin.ReleaseClaimControlResponse;
import com.massimotter.weave.backend.model.admin.SecretRefResponse;
import com.massimotter.weave.backend.model.admin.SuiteDomainReadinessResponse;
import com.massimotter.weave.backend.model.admin.WeaverDistributionPolicyResponse;
import com.massimotter.weave.backend.model.admin.WeaverMcpGrantResponse;
import com.massimotter.weave.backend.model.admin.WeaverModelAliasResponse;
import com.massimotter.weave.backend.model.admin.WeaverRuntimeProfileChangeResponse;
import com.massimotter.weave.backend.model.admin.WeaverRuntimeProjectionItemResponse;
import com.massimotter.weave.backend.model.admin.WeaverRuntimeProjectionResponse;
import com.massimotter.weave.backend.provider.ProviderCapabilityContracts;
import com.massimotter.weave.backend.provider.ProviderCategoryCatalog;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.domainfacade.CanonicalDomainDefinition;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import static com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_PRIMARY_IDENTITY_KEY_LENGTH;
import static com.massimotter.weave.backend.model.IdentityKeyFormat.PRIMARY_IDENTITY_KEY_PATTERN;

@Service
public class AdminControlPlaneService {

    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "ready",
            "disabled",
            "degraded",
            "policy-blocked");
    private static final int MAX_BOOTSTRAP_ADMIN_KEYS = 25;
    private static final int MAX_BOOTSTRAP_ADMIN_KEY_LENGTH = MAX_PRIMARY_IDENTITY_KEY_LENGTH;
    private static final Pattern PRIMARY_IDENTITY_KEY_REGEX = Pattern.compile(PRIMARY_IDENTITY_KEY_PATTERN);

    private final ProviderRegistry providerRegistry;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionService providerSelectionService;
    private final ProviderReplacementDryRunService providerReplacementDryRunService;
    private final EffectivePolicySimulationService effectivePolicySimulationService;
    private final OrganizationBootstrapRepository organizationBootstrapRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final IdentityRealmWorkflowService identityRealmWorkflowService;
    private final Clock clock;

    @Autowired
    public AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionService providerSelectionService,
            ProviderReplacementDryRunService providerReplacementDryRunService,
            EffectivePolicySimulationService effectivePolicySimulationService,
            OrganizationBootstrapRepository organizationBootstrapRepository,
            AuditEventPublisher auditEventPublisher,
            IdentityRealmWorkflowService identityRealmWorkflowService,
            Clock clock) {
        this.providerRegistry = providerRegistry;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.providerSelectionService = providerSelectionService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.providerReplacementDryRunService = providerReplacementDryRunService == null
                ? new ProviderReplacementDryRunService(providerSelectionService, auditEventPublisher, this.clock)
                : providerReplacementDryRunService;
        this.effectivePolicySimulationService = effectivePolicySimulationService == null
                ? new EffectivePolicySimulationService(auditEventPublisher, this.clock)
                : effectivePolicySimulationService;
        this.organizationBootstrapRepository = organizationBootstrapRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.identityRealmWorkflowService = Objects.requireNonNull(identityRealmWorkflowService, "identityRealmWorkflowService");
    }

    AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            OrganizationBootstrapRepository organizationBootstrapRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this(providerRegistry, workspaceCapabilityService, new ProviderSelectionService(providerRegistry, providerSelectionRepository, clock), null, null, organizationBootstrapRepository, auditEventPublisher,
                new IdentityRealmWorkflowService(workspaceCapabilityService, auditEventPublisher, List.of(new com.massimotter.weave.backend.identity.realm.KeycloakRealmDryRunProvider()), new com.massimotter.weave.backend.identity.realm.InMemoryIdentityRealmEvidenceRepository(), List.of(new com.massimotter.weave.backend.identity.realm.KeycloakRealmLiveApplyAdapter(new IdentityRealmApplyProperties())), new IdentityRealmApplyProperties(), clock), clock);
    }

    AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            OrganizationBootstrapRepository organizationBootstrapRepository,
            AuditEventPublisher auditEventPublisher,
            List<IdentityRealmProvider> identityRealmProviders,
            IdentityRealmEvidenceRepository identityRealmEvidenceRepository,
            List<IdentityRealmLiveApplyAdapter> identityRealmLiveApplyAdapters,
            IdentityRealmApplyProperties identityRealmApplyProperties,
            Clock clock) {
        this(providerRegistry, workspaceCapabilityService, new ProviderSelectionService(providerRegistry, providerSelectionRepository, clock), null, null, organizationBootstrapRepository, auditEventPublisher,
                new IdentityRealmWorkflowService(workspaceCapabilityService, auditEventPublisher, identityRealmProviders, identityRealmEvidenceRepository, identityRealmLiveApplyAdapters, identityRealmApplyProperties, clock), clock);
    }

    public AdminControlPlaneResponse overview(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "overview");
        ProviderRegistryResponse registry = providerRegistry.status();
        IdentityProviderReadinessResponse identityReadiness = identityProviderReadiness(registry, jwt);
        List<SuiteDomainReadinessResponse> suiteReadiness = suiteDomainReadiness(registry);
        return new AdminControlPlaneResponse(
                "admin-control-plane-v1",
                organizationId(jwt),
                organizationName(jwt),
                "OIDC/SAML selected IDM",
                registry.providerConfigSource(),
                registry.bootstrapDefaultsAreSuggestionsOnly(),
                registry.backendOwnedFacades(),
                true,
                registry.supportSafe(),
                false,
                Instant.now(clock),
                registry.categories(),
                registry.selectedProviderMappings().stream()
                        .map(selection -> providerSelectionService.toResponse(selection, false, providerSelectionService.readinessFor(selection.category(), registry)))
                        .toList(),
                whitelist(jwt),
                weaverDistributionPolicy(registry),
                weaverRuntimeProjection(registry),
                identityReadiness,
                suiteReadiness,
                goLiveReadiness(identityReadiness, suiteReadiness),
                secretRefs(registry),
                mcpServerBindings(registry),
                Map.ofEntries(
                        Map.entry("providers", "/api/providers/status"),
                        Map.entry("policy", "/api/admin/policies/capability-whitelist"),
                        Map.entry("audit", "/api/admin/audit/events"),
                        Map.entry("readinessTest", "/api/admin/providers/readiness-tests"),
                        Map.entry("providerReplacementDryRun", "/api/admin/providers/replacements/dry-run"),
                        Map.entry("attachExistingFilesPortabilityPlan", "/api/admin/portability/attach-existing/files/plan"),
                        Map.entry("identityReadiness", "/api/admin/identity/readiness"),
                        Map.entry("identityRealmDryRun", "/api/admin/identity/realm/dry-run"),
                        Map.entry("identityRealmApply", "/api/admin/identity/realm/apply"),
                        Map.entry("effectivePolicySimulation", "/api/admin/policies/effective/simulations"),
                        Map.entry("providerSelections", "/api/admin/providers/selections"),
                        Map.entry("suiteReadiness", "/api/admin/control-plane#suiteDomainReadiness"),
                        Map.entry("mcpServerBindings", "/api/admin/control-plane#mcpServerBindings"),
                        Map.entry("weaverRuntimeProjection", "/api/admin/control-plane#weaverRuntimeProjection")));
    }

    public IdentityProviderReadinessResponse identityProviderReadiness(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "identity-provider", "readiness");
        return identityProviderReadiness(providerRegistry.status(), jwt);
    }

    public ProviderSelectionResponse selectProvider(ProviderSelectionRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "admin-control-plane", "select-provider");
        ProviderSelection selection = providerSelectionService.validate(request, actorRef(jwt));
        boolean dryRun = request.dryRun();
        ProviderSelection applied = dryRun ? selection : providerSelectionService.save(selection);
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
        return providerSelectionService.toResponse(applied, dryRun, dryRun ? "dry_run_valid" : "admin_selected_pending_readiness");
    }

    public ProviderReplacementDryRunResponse dryRunProviderReplacement(ProviderReplacementDryRunRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "admin-control-plane", "provider-replacement-dry-run");
        return providerReplacementDryRunService.dryRun(request, organizationId(jwt), actorRef(jwt));
    }

    public EffectivePolicyResponse effectivePolicy(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "effective-policy");
        return workspaceCapabilityService.effectivePolicySnapshot(jwt, "organization");
    }

    public EffectivePolicySimulationResponse simulateEffectivePolicy(EffectivePolicySimulationRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "effective-policy-simulation");
        return effectivePolicySimulationService.simulate(request, organizationId(jwt), actorRef(jwt));
    }

    public IdentityRealmDryRunReport dryRunIdentityRealm(IdentityRealmDryRunRequest request, Jwt jwt) {
        return identityRealmWorkflowService.dryRunIdentityRealm(request, jwt);
    }

    public IdentityRealmApplyReport applyIdentityRealm(IdentityRealmApplyRequest request, Jwt jwt) {
        return identityRealmWorkflowService.applyIdentityRealm(request, jwt);
    }

    public CapabilityWhitelistResponse whitelist(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "read-capability-whitelist");
        WorkspaceCapabilityPolicyResponse policy = workspaceCapabilityService.policySnapshot(jwt);
        Map<String, List<String>> profileCapabilities = new LinkedHashMap<>();
        profileCapabilities.put("workspace-admin", List.of(
                "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
                "boards.read", "boards.update_task", "admin.provider.configure", "admin.policy.edit", "weaver.exec_disabled"));
        profileCapabilities.put("workspace-operator", List.of(
                "admin_control_plane.readiness_read", "operator.support_bundle.create", "release_evidence.read", "manuals.admin", "weaver.exec_disabled"));
        profileCapabilities.put("member-default", List.of(
                "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "boards.read", "weaver.exec_disabled"));
        profileCapabilities.put("guest-deny-default", List.of());
        profileCapabilities.put("group:weave-weaver-pilot", List.of("weaver.files_read", "weaver.exec_disabled"));
        return new CapabilityWhitelistResponse(
                policy.denyByDefault(),
                false,
                STABLE_MEMBER_IMPACT_STATES,
                profileCapabilities,
                policy.profileKeys(),
                policy.grantedCapabilities(),
                "backend-control-plane");
    }

    public CapabilityWhitelistResponse updateWhitelist(CapabilityWhitelistUpdateRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.policy.edit", "admin-control-plane", "update-capability-whitelist");
        if (request == null || request.profileKey() == null || request.profileKey().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "capability-whitelist-invalid",
                    "Capability whitelist update requires a profile key.",
                    Map.of("reason", "profileKey is required"));
        }
        String profileKey = request.profileKey().trim().toLowerCase(Locale.ROOT);
        enforceLastAdminGuard(request);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "organization-admin-console",
                AuditAction.ADMIN_POLICY_UPDATED,
                Instant.now(clock),
                "admin-policy-" + Instant.now(clock).toEpochMilli(),
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "profileKey", profileKey,
                        "capabilityKeys", safeCapabilities(request.capabilityKeys()),
                        "reason", safeText(request.reason()),
                        "denyByDefault", true,
                        "rawProviderError", "redacted before audit",
                        "token", "not-stored")));
        return whitelist(jwt);
    }

    public OrganizationBootstrapResponse bootstrapOrganization(OrganizationBootstrapRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.policy.edit", "admin-control-plane", "bootstrap-organization");
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        if (request == null || request.organizationId() == null || request.organizationId().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "organization-bootstrap-invalid",
                    "Organization bootstrap requires an organization id.",
                    Map.of("reason", "organizationId is required"));
        }
        String organizationId = safeOrganizationId(request.organizationId());
        String bootstrapMode = bootstrapMode(request.bootstrapMode());
        List<String> retainedAdmins = retainedAdminPrimaryIdentityKeys(request.adminPrimaryIdentityKeys(), identity.primaryIdentityKey());
        Instant bootstrappedAt = Instant.now(clock);
        OrganizationBootstrapRecord record = organizationBootstrapRepository.save(new OrganizationBootstrapRecord(
                organizationId,
                bootstrapMode,
                identity.primaryIdentityKey(),
                retainedAdmins,
                bootstrappedAt));
        String auditRef = "organization-bootstrap-" + record.organizationId() + "-" + bootstrappedAt.toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                record.organizationId(),
                "admin-control-plane",
                actorRef(jwt),
                "organization-bootstrap",
                AuditAction.ADMIN_POLICY_UPDATED,
                bootstrappedAt,
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "bootstrapMode", record.bootstrapMode(),
                        "actorPrimaryIdentityKey", record.actorPrimaryIdentityKey(),
                        "retainedAdminPrimaryIdentityKeyCount", record.retainedAdminPrimaryIdentityKeys().size(),
                        "lastAdminGuardPassed", true,
                        "supportSafe", true,
                        "emailPrimaryKey", false,
                        "reason", safeText(request.reason()))));
        return new OrganizationBootstrapResponse(
                record.organizationId(),
                record.bootstrapMode(),
                record.actorPrimaryIdentityKey(),
                record.retainedAdminPrimaryIdentityKeys(),
                true,
                true,
                record.bootstrappedAt(),
                List.of(auditRef));
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

    public AttachExistingPortabilityPlanResponse attachExistingFilesPortabilityPlan(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "attach-existing-files-portability-plan");
        Instant inspectedAt = Instant.now(clock);
        String auditRef = "attach-existing-files-portability-plan-inspected-" + inspectedAt.toEpochMilli();
        List<AttachExistingPortabilityPlanResponse.AdapterBinding> bindings = List.of(
                new AttachExistingPortabilityPlanResponse.AdapterBinding("cloud-drive-files-existing", List.of("files"), "hyperscaler_cloud_existing", "active", "read_only", true, false, false, "audit:attach-existing-files:current-active-binding"),
                new AttachExistingPortabilityPlanResponse.AdapterBinding("cloud-drive-files-discovery-source", List.of("files"), "hyperscaler_cloud_existing", "discovery_read_only", "read_only", false, false, false, "audit:attach-existing-files:discovery-source"),
                new AttachExistingPortabilityPlanResponse.AdapterBinding("nextcloud-files-sovereign-target", List.of("files"), "self_hosted_sovereign_candidate", "candidate", "plan_only", false, false, false, "audit:attach-existing-files:candidate-target"));
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "attach-existing-files-portability-plan",
                AuditAction.ATTACH_EXISTING_PORTABILITY_PLAN_INSPECTED,
                inspectedAt,
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "planId", "attach-existing-files-portability-plan-mvp",
                        "mode", "attach_existing",
                        "domainKey", "files",
                        "destructiveActionAllowed", false,
                        "providerMutationPerformed", false,
                        "memberVisibleProviderInternals", false,
                        "providerDetailsAudience", "admin_operator_only",
                        "token", "not-stored")));
        return new AttachExistingPortabilityPlanResponse(
                "attach-existing-files-portability-plan-mvp",
                "admin-attach-existing-portability-plan-v1",
                "attach_existing",
                "files",
                "inspection-ready-read-only",
                "Read-only discovery and portability planning only. This does not prove destructive migration, production cutover, release readiness, legal compliance, or lossless provider migration.",
                true,
                true,
                false,
                false,
                false,
                List.of(
                        new AttachExistingPortabilityPlanResponse.CapabilityMapItem("files.read", "cloud-drive-file-read", "nextcloud-files-read", "available"),
                        new AttachExistingPortabilityPlanResponse.CapabilityMapItem("files.share_links", "cloud-drive-external-link-read", "nextcloud-share-link-policy-review", "degraded"),
                        new AttachExistingPortabilityPlanResponse.CapabilityMapItem("files.retention_labels", "cloud-drive-proprietary-retention-labels", "manual-policy-rebuild-required", "coming_later")),
                bindings,
                "permission-impact:attach-existing-files:mvp",
                List.of(new AttachExistingPortabilityPlanResponse.ReportItem("FileShare", null, "manual_review", "External share links need admin policy review before a Nextcloud target can reproduce equivalent exposure.", null)),
                "loss-report:attach-existing-files:mvp",
                List.of(
                        new AttachExistingPortabilityPlanResponse.ReportItem("File", "provider_native_retention_label", "vendor_locked", null, "Source retention labels are proprietary metadata and must be exported to an archive report or manually rebuilt."),
                        new AttachExistingPortabilityPlanResponse.ReportItem("FileVersion", "historic_version_blob", "archive_only", null, "Historic versions are discoverable but not imported in this MVP plan.")),
                "conflict-report:attach-existing-files:mvp",
                List.of(new AttachExistingPortabilityPlanResponse.ReportItem("FileShare", null, null, null, "Two source groups map to one target group slug; admin must choose merge or split before cutover.")),
                List.of("audit:attach-existing-files:discovery-read-only", "audit:attach-existing-files:plan-generated", auditRef),
                new AttachExistingPortabilityPlanResponse.RecommendedTarget("nextcloud-files-sovereign-target", "Self-hosted Files candidate improves data-sovereignty posture because data plane, audit sink, and retention policy can be operated under the organization-controlled stack after a separately approved migration path."),
                new AttachExistingPortabilityPlanResponse.NextSteps(
                        List.of("Keep cloud-drive-files active while discovery_read_only evidence is reviewed.", "Run export dry-run and archive manifest checks before requesting migration_source or migration_target status.", "Require admin approval of loss, permission, and conflict reports before any guarded apply."),
                        List.of("Retain the existing cloud-drive-files binding as the active binding until post-cutover verification passes.", "Keep rollback retention and restore-smoke refs support-safe and admin-visible only.")),
                List.of("available", "degraded", "coming_later"),
                new AttachExistingPortabilityPlanResponse.NegativeChecks(true, true, exactlyOneActiveBindingPerDomain(bindings)));
    }

    private boolean exactlyOneActiveBindingPerDomain(List<AttachExistingPortabilityPlanResponse.AdapterBinding> bindings) {
        Map<String, Long> activeCounts = bindings.stream()
                .filter(AttachExistingPortabilityPlanResponse.AdapterBinding::activeBinding)
                .flatMap(binding -> binding.domainKeys().stream())
                .collect(java.util.stream.Collectors.groupingBy(domain -> domain, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return activeCounts.values().stream().allMatch(count -> count == 1L);
    }

    public List<AdminAuditEventResponse> auditEvents(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "admin-control-plane", "read-audit-events");
        if (auditEventPublisher instanceof InMemoryAuditEventPublisher memoryAudit) {
            return auditEventResponses(memoryAudit.events());
        }
        if (auditEventPublisher instanceof FileAuditEventPublisher fileAudit) {
            return auditEventResponses(fileAudit.events());
        }
        return List.of();
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

    private IdentityProviderReadinessResponse identityProviderReadiness(ProviderRegistryResponse registry, Jwt jwt) {
        var identityCategory = registry.categories().stream()
                .filter(category -> "identity-idm".equals(category.category()))
                .findFirst();
        ProviderStatusResponse identityProvider = registry.providers().stream()
                .filter(provider -> provider.module() == ProviderModule.IDENTITY_REALM)
                .findFirst()
                .orElse(null);
        boolean selectedByAdmin = identityCategory.map(category -> category.selectedByAdmin()).orElse(false);
        boolean configured = identityProvider != null && identityProvider.configured();
        boolean enabled = identityProvider != null && identityProvider.enabled();
        boolean failClosed = identityProvider == null || identityProvider.failClosed();
        boolean supportSafe = identityProvider == null || identityProvider.supportSafe();
        String providerKey = identityCategory.map(category -> category.selectedProviderKey())
                .filter(value -> value != null && !value.isBlank())
                .orElse(identityProvider == null ? "awaiting_admin_selection" : identityProvider.providerKey());
        CapabilityWhitelistResponse whitelist = whitelist(jwt);

        List<IdentityProviderReadinessCardResponse> cards = List.of(
                readinessCard(
                        "realm-import",
                        "Realm import readiness",
                        !selectedByAdmin ? "admin-action-required" : configured ? "ready" : "admin-action-required",
                        selectedByAdmin
                                ? "Identity realm is selected in the backend control plane; import/apply evidence is evaluated by backend dry-run contracts."
                                : "Identity realm provider mapping has not been selected by an admin.",
                        selectedByAdmin && configured ? "ready" : "degraded",
                        selectedByAdmin
                                ? "Run the realm desired-state dry-run and attach support-safe evidence before apply."
                                : "Select an identity provider mapping in Admin Console before exposing sign-in readiness.",
                        List.of("Run /api/admin/identity/realm/dry-run", "Review blockers and audit refs before apply"),
                        List.of("identity-realm-dry-run", "admin-control-plane-selection"),
                        Map.of(
                                "selectedByAdmin", selectedByAdmin,
                                "configured", configured,
                                "enabled", enabled,
                                "secretsReturned", false,
                                "rawProviderErrorsReturned", false)),
                readinessCard(
                        "oidc-client-readiness",
                        "OIDC client readiness",
                        selectedByAdmin && configured ? "ready" : "admin-action-required",
                        "OIDC client posture is summarized by the backend; client identifiers, redirect details, and secrets are not exposed to members.",
                        selectedByAdmin && configured ? "ready" : "degraded",
                        "Confirm client scopes and redirect allowlists through backend dry-run output, not frontend provider APIs.",
                        List.of("Validate client scope mappings", "Keep client secrets as SecretRef handles only"),
                        List.of("identity-client-contract", "secretref-boundary"),
                        Map.of(
                                "clientDetailsRedacted", true,
                                "clientSecretsReturned", false,
                                "selectedByAdmin", selectedByAdmin,
                                "configured", configured)),
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
                        loginReadinessState(selectedByAdmin, configured, enabled, failClosed),
                        "Member login is exposed only as product-level availability; provider endpoints and raw auth errors stay out of member flows.",
                        selectedByAdmin && configured && enabled ? "ready" : "degraded",
                        "Keep member sign-in fail-closed until selected provider configuration and backend readiness evidence are present.",
                        List.of("Verify backend auth facade readiness", "Confirm member client shows only stable capability states"),
                        List.of("member-boundary", "backend-auth-facade"),
                        Map.of(
                                "memberClientMayConfigureProvider", false,
                                "providerEndpointsReturned", false,
                                "rawAuthErrorsReturned", false,
                                "failClosed", failClosed)),
                readinessCard(
                        "policy-readiness",
                        "Policy readiness",
                        whitelist.denyByDefault() && failClosed ? "ready" : "policy-blocked",
                        "Capability policy is the gate between provider claims and Weave product access.",
                        whitelist.denyByDefault() && failClosed ? "ready" : "policy-blocked",
                        "Retain deny-by-default policy and last-admin recovery capabilities before provider apply.",
                        List.of("Retain admin.policy.edit for workspace-admin", "Review policy simulation before realm apply"),
                        List.of("capability-whitelist", "last-admin-guard"),
                        Map.of(
                                "denyByDefault", whitelist.denyByDefault(),
                                "failClosed", failClosed,
                                "normalMembersMayAuthorPolicy", whitelist.normalMembersMayAuthorPolicy())));
        String overallState = aggregateIdentityReadiness(cards);
        return new IdentityProviderReadinessResponse(
                "identity-provider-readiness-v1",
                "identity-idm",
                providerKey,
                overallState,
                supportSafe,
                true,
                true,
                false,
                true,
                registry.generatedAt(),
                List.of("ready", "degraded", "policy-blocked", "admin-action-required", "disabled"),
                cards,
                identityNextActions(overallState),
                Map.of(
                        "overview", "/api/admin/control-plane",
                        "readiness", "/api/admin/identity/readiness",
                        "realmDryRun", "/api/admin/identity/realm/dry-run",
                        "effectivePolicySimulation", "/api/admin/policies/effective/simulations"),
                Map.of(
                        "contractOptional", true,
                        "versionSkewSafe", true,
                        "memberClientMayConfigureIdentityProvider", false,
                        "providerDiagnosticsRedacted", true,
                        "selectedByAdmin", selectedByAdmin,
                        "configured", configured,
                        "enabled", enabled,
                        "secretsReturned", false,
                        "rawProviderErrorsReturned", false));
    }

    private IdentityProviderReadinessCardResponse readinessCard(
            String key,
            String label,
            String state,
            String summary,
            String memberImpact,
            String remediation,
            List<String> nextActions,
            List<String> evidenceRefs,
            Map<String, Object> diagnostics) {
        return new IdentityProviderReadinessCardResponse(
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

    private String loginReadinessState(boolean selectedByAdmin, boolean configured, boolean enabled, boolean failClosed) {
        if (!enabled) {
            return selectedByAdmin ? "admin-action-required" : "disabled";
        }
        if (!selectedByAdmin || !configured) {
            return "admin-action-required";
        }
        return failClosed ? "ready" : "policy-blocked";
    }

    private List<String> identityNextActions(String overallState) {
        return switch (overallState) {
            case "ready" -> List.of("Monitor audit/readiness transitions and keep support bundles redacted.");
            case "policy-blocked" -> List.of("Resolve policy blockers, unknown mappings, or last-admin guard failures before provider apply.");
            case "disabled" -> List.of("Select an identity provider mapping or keep member flows disabled by policy.");
            default -> List.of(
                    "Run the backend identity realm dry-run.",
                    "Resolve admin-action-required cards before treating sign-in as ready.",
                    "Verify member clients expose only product-level states.");
        };
    }

    private String aggregateIdentityReadiness(List<IdentityProviderReadinessCardResponse> cards) {
        List<String> states = cards.stream().map(IdentityProviderReadinessCardResponse::state).toList();
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

    private String safeOrganizationId(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "organization-id-invalid",
                    "Organization id must be a support-safe slug.",
                    Map.of("organizationId", "invalid-organization-id-redacted"));
        }
        return trimmed;
    }

    private String bootstrapMode(String value) {
        if (value == null || value.isBlank()) {
            return "existing_org";
        }
        String trimmed = value.trim();
        if (trimmed.equals("existing_org") || trimmed.equals("new_org")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "organization-bootstrap-mode-invalid",
                "Organization bootstrap mode must be existing_org or new_org.",
                Map.of("bootstrapMode", "invalid-bootstrap-mode-redacted"));
    }

    private List<String> retainedAdminPrimaryIdentityKeys(List<String> suppliedKeys, String actorPrimaryIdentityKey) {
        List<String> supplied = suppliedKeys == null ? List.of() : suppliedKeys;
        List<String> retained = Stream.concat(supplied.stream(), Stream.of(actorPrimaryIdentityKey))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (supplied.size() > MAX_BOOTSTRAP_ADMIN_KEYS || retained.stream().anyMatch(this::unsafeBootstrapAdminKey)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "organization-bootstrap-admin-key-invalid",
                    "Organization bootstrap admin keys must be support-safe immutable issuer+subject keys.",
                    Map.of(
                            "requiredFormat", "issuer+subject:<issuer>#<subject>",
                            "maxSuppliedCount", MAX_BOOTSTRAP_ADMIN_KEYS,
                            "maxLength", MAX_BOOTSTRAP_ADMIN_KEY_LENGTH,
                            "supportSafe", true));
        }
        if (!retained.contains(actorPrimaryIdentityKey)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "last-admin-guard",
                    "Organization bootstrap must retain the current owner/admin as a recovery administrator.",
                    Map.of("supportSafe", true));
        }
        return retained;
    }

    private boolean unsafeBootstrapAdminKey(String value) {
        return value.length() > MAX_BOOTSTRAP_ADMIN_KEY_LENGTH || !PRIMARY_IDENTITY_KEY_REGEX.matcher(value).matches();
    }





    private String requiredSecretRef(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-secretref-invalid",
                    "Provider replacement dry-run requires a backend SecretRef before activation can be evaluated.",
                    Map.of("secretRef", "invalid-secret-ref-redacted"));
        }
        return validateSecretRef(value, "provider-replacement-secretref-invalid", "Provider replacement dry-run may reference credentials only through SecretRef URIs.");
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

    private String safeSourceOfTruth(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-source-of-truth-invalid",
                    "Provider replacement dry-run requires a support-safe source-of-truth declaration.",
                    Map.of("sourceOfTruth", "invalid-source-of-truth-redacted"));
        }
        String trimmed = value.trim();
        String redacted = safeText(trimmed);
        if (!redacted.equals(trimmed) || trimmed.length() > 160) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-source-of-truth-invalid",
                    "Provider replacement source-of-truth declaration must not contain URLs, bearer tokens, or secret material.",
                    Map.of("sourceOfTruth", "invalid-source-of-truth-redacted"));
        }
        return trimmed;
    }




    private List<SuiteDomainReadinessResponse> suiteDomainReadiness(ProviderRegistryResponse registry) {
        return List.of(
                suiteDomain(CanonicalDomainDefinition.FILES_DOCS, registry,
                        "backend-owned file/document facade with guarded editor sessions",
                        List.of("Export manifests required before provider replacement", "Document editing remains guarded until WOPI session evidence is fresh"),
                        "Select/test files and documents providers, then attach support-safe export/delete evidence."),
                suiteDomain(CanonicalDomainDefinition.BOARDS_TASKS, registry,
                        "local workspace writes; provider sync/write promotion gated by contract evidence",
                        List.of("Provider-write apply is refused until read-sync, conflict, and rollback evidence pass", "Keyboard create/move/complete flows are required before member promotion"),
                        "Verify board/task workspace contract, accessible workflows, conflict states, and audit events."),
                suiteDomain(CanonicalDomainDefinition.CALENDAR_MEETINGS, registry,
                        "workspace/team/channel calendar facade; private personal calendars blocked",
                        List.of("Secret-free setup metadata only", "Private calendar ingestion and credential profile downloads are out of scope"),
                        "Confirm workspace/team/channel event readiness and keep private-calendar setup blocked."));
    }

    private SuiteDomainReadinessResponse suiteDomain(
            CanonicalDomainDefinition definition,
            ProviderRegistryResponse registry,
            String sourceOfTruthMode,
            List<String> portabilityNotes,
            String nextAction) {
        List<String> readinessStates = definition.providerCategoryKeys().stream()
                .map(category -> providerSelectionService.readinessFor(category, registry))
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
                List.of("provider jurisdiction is visible as procurement-risk metadata", "do not claim Cloud-Act-proof or legally sovereign posture"),
                List.of("audit://suite/" + definition.domain() + "/readiness"),
                nextAction,
                "provider and jurisdiction exposure visible; raw diagnostics redacted",
                "export/import/lossy/conflict/rollback reports required before provider replacement",
                "support-safe audit event required for readiness, policy, export, import, and Weaver access decisions",
                "model_first_read_only_governed",
                true,
                true,
                false,
                Map.of(
                        "providerCategoryCount", definition.providerCategoryKeys().size(),
                        "supportSafe", true,
                        "rawProviderConfigReturned", false,
                        "memberProviderSetupControlsReturned", false,
                        "exposureDescriptor", "provider and jurisdiction exposure visible; raw diagnostics redacted",
                        "weaverMode", "model_first_read_only_governed"));
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
            IdentityProviderReadinessResponse identityReadiness,
            List<SuiteDomainReadinessResponse> suiteReadiness) {
        List<String> blockers = new ArrayList<>();
        if (!"ready".equals(identityReadiness.overallState())) {
            blockers.add("identity-idm:" + identityReadiness.overallState());
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
                                "Refresh manual AT evidence for admin go-live, Workspace, migration, and governed Weaver surfaces before public/final release claims.",
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

    private WeaverRuntimeProjectionResponse weaverRuntimeProjection(ProviderRegistryResponse registry) {
        Instant generatedAt = Instant.now(clock);
        String modelProviderKey = registry.selectedProviderMappings().stream()
                .filter(selection -> selection.category().equals("model"))
                .map(ProviderSelection::providerKey)
                .findFirst()
                .orElse("lmstudio");
        String chatProviderKey = registry.selectedProviderMappings().stream()
                .filter(selection -> selection.category().equals("chat"))
                .map(ProviderSelection::providerKey)
                .findFirst()
                .orElse("matrix-chat");
        return new WeaverRuntimeProjectionResponse(
                "weaver-runtime-profile-v1",
                "runtime-profile-hash-pending-live-regeneration",
                generatedAt.plusSeconds(3600).toString(),
                generatedAt.toString(),
                true,
                true,
                false,
                true,
                true,
                "sandbox-readiness-recorded-runtime-execution-disabled",
                List.of(),
                List.of("audit://weaver/runtime-profile/projection"),
                List.of(
                        projectionItem("chat-route", "chat", "channels.weave-chat via " + chatProviderKey, "ready", "available", "Stable chat route only; provider rooms stay behind Weave Chat.", false),
                        projectionItem("model-alias-general", "model", "general-assistant via " + modelProviderKey, providerSelectionService.readinessFor("model", registry), "disabled_by_policy", "Alias is admin-selected but runtime remains disabled by default.", false),
                        projectionItem("tool-calendar-search", "tool", "calendar.search_events", providerSelectionService.readinessFor("calendar", registry), "disabled_by_policy", "Read-only discovery requires weaver.calendar_read and calendar.read grants.", false),
                        projectionItem("tool-boards-comment", "tool", "boards.comment", providerSelectionService.readinessFor("boards-tasks", registry), "disabled_by_policy", "Write-like tool requires explicit approval receipt and audit.", true),
                        projectionItem("mcp-weave-domain-tools", "mcp", "weave-domain-tools via streamable-http", "configured", "disabled_by_policy", "Admin-bound MCP server is discoverable only to granted RuntimeProfiles and remains disabled until org policy enables Weaver.", true),
                        projectionItem("consent-shared-space", "mcp", "shared-space consent gate", "admin-action-required", "disabled_by_policy", "Group chat/shared-space participation requires org policy and consent evidence.", true)));
    }

    private WeaverRuntimeProjectionItemResponse projectionItem(
            String id,
            String category,
            String label,
            String state,
            String memberImpact,
            String policyImpact,
            boolean approvalRequired) {
        String safeState = "unknown".equals(state) ? "admin-action-required" : state;
        return new WeaverRuntimeProjectionItemResponse(
                id,
                category,
                label,
                safeState,
                memberImpact,
                policyImpact,
                "Projected through Weave domain policy; raw OpenClaw config, provider credentials, and downstream payloads are not exposed.",
                List.of("audit://weaver/runtime-profile/" + id),
                false,
                approvalRequired);
    }

    private WeaverDistributionPolicyResponse weaverDistributionPolicy(ProviderRegistryResponse registry) {
        String modelProviderKey = registry.selectedProviderMappings().stream()
                .filter(selection -> selection.category().equals("model"))
                .map(ProviderSelection::providerKey)
                .findFirst()
                .orElse("lmstudio");
        String chatProviderKey = registry.selectedProviderMappings().stream()
                .filter(selection -> selection.category().equals("chat"))
                .map(ProviderSelection::providerKey)
                .findFirst()
                .orElse("matrix-chat");
        String readiness = providerSelectionService.readinessFor("model", registry);
        if ("unknown".equals(readiness)) {
            readiness = "admin-action-required";
        }
        return new WeaverDistributionPolicyResponse(
                false,
                chatProviderKey,
                readiness,
                List.of("Changing the model provider regenerates the Weaver RuntimeProfile projection and requires live completion evidence before member rollout."),
                List.of("runtime profile generation remains blocked until weaver.enabled is explicitly granted by organization policy"),
                List.of(new WeaverModelAliasResponse(
                        "general-assistant",
                        modelProviderKey,
                        "lmstudio/qwen/qwen3.5-9b",
                        true)),
                "general-assistant",
                List.of(),
                List.of("chat.search_messages"),
                List.of("workspace-triage"),
                List.of(new WeaverMcpGrantResponse("weave-chat", List.of("reply"), true)),
                List.of("shell.exec", "provider.raw_config.read"),
                List.of("chat.reply", "external.send"),
                List.of(
                        "chat.provider=" + chatProviderKey,
                        "model.provider=" + modelProviderKey,
                        "models.default=general-assistant",
                        "credentialRef=credentialref://weave/channels/weave-chat/runtime-token"),
                "runtime-profile-hash-pending-live-regeneration",
                null,
                "not_revoked",
                null,
                List.of("audit://weaver/pa-chat/bridge-roundtrip"),
                List.of(new WeaverRuntimeProfileChangeResponse(
                        "sprint-14-pa-chat",
                        "runtime-profile-hash-pending-live-regeneration",
                        Instant.now(clock).toString(),
                        "draft",
                        "Admin-selected model provider is projected into Weaver aliases without exposing provider secrets to members.")));
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

    private boolean providerKeyMatches(ProviderStatusResponse provider, String providerKey) {
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return provider.providerKey().equals(providerKey)
                || provider.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private String providerSelectionChoiceModel(String value) {
        try {
            return com.massimotter.weave.backend.provider.ProviderChoiceModel.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-choice-model-invalid",
                    "Provider selection choice model is not part of the Weave provider choice contract.",
                    Map.of("choiceModel", "invalid-choice-model-redacted"));
        }
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

    private boolean safePrimaryIdentityKey(String value) {
        return value != null
                && value.length() <= MAX_BOOTSTRAP_ADMIN_KEY_LENGTH
                && PRIMARY_IDENTITY_KEY_REGEX.matcher(value).matches();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
        return claim(jwt, "weave_tenant")
                .or(() -> claim(jwt, "tenant"))
                .or(() -> claim(jwt, "tid"))
                .orElse("weave-dogfood");
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
