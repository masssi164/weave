package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.FileAuditEventPublisher;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyProperties;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyReport;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDesiredState;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunEvidence;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunReport;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmEvidenceRepository;
import com.massimotter.weave.backend.identity.realm.IdentityRealmLiveApplyAdapter;
import com.massimotter.weave.backend.identity.realm.IdentityRealmProvider;
import com.massimotter.weave.backend.identity.realm.InMemoryIdentityRealmEvidenceRepository;
import com.massimotter.weave.backend.identity.realm.KeycloakRealmDryRunProvider;
import com.massimotter.weave.backend.identity.realm.KeycloakRealmLiveApplyAdapter;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.admin.AdminAuditEventResponse;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
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
import com.massimotter.weave.backend.model.admin.WeaverEligibilityPreviewResponse;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final Set<String> SIMULATION_ROLES = Set.of("owner", "admin", "operator", "member", "guest");
    private static final Set<String> SIMULATION_GROUPS = Set.of(
            "weave-calendar-editors",
            "weave-board-editors",
            "weave-meeting-hosts",
            "weave-document-editors",
            "weave-decision-recorders",
            "weaver-group",
            "weave-weaver-pilot");
    private static final Map<String, List<String>> SIMULATION_GROUP_CAPABILITIES = Map.of(
            "weave-calendar-editors", List.of("calendar.manage_events"),
            "weave-board-editors", List.of("boards.update_task"),
            "weave-meeting-hosts", List.of("meetings.host"),
            "weave-document-editors", List.of("documents.edit"),
            "weave-decision-recorders", List.of("decisions.record"),
            "weaver-group", List.of("weaver.files_read", "weaver.exec_disabled"),
            "weave-weaver-pilot", List.of("weaver.files_read", "weaver.exec_disabled"));
    private static final Set<String> SIMULATION_KNOWN_CAPABILITIES = Set.of(
            "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
            "boards.read", "boards.update_task", "meetings.join", "meetings.host", "documents.view", "documents.edit",
            "decisions.read", "decisions.record", "manuals.read", "manuals.admin", "release_evidence.read", "release_evidence.manage",
            "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure", "operator.support_bundle.create",
            "weaver.enabled", "weaver.files_read", "weaver.exec_disabled");
    private static final int MAX_BOOTSTRAP_ADMIN_KEYS = 25;
    private static final int MAX_BOOTSTRAP_ADMIN_KEY_LENGTH = MAX_PRIMARY_IDENTITY_KEY_LENGTH;
    private static final Pattern PRIMARY_IDENTITY_KEY_REGEX = Pattern.compile(PRIMARY_IDENTITY_KEY_PATTERN);

    private final ProviderRegistry providerRegistry;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final OrganizationBootstrapRepository organizationBootstrapRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final List<IdentityRealmProvider> identityRealmProviders;
    private final IdentityRealmEvidenceRepository identityRealmEvidenceRepository;
    private final List<IdentityRealmLiveApplyAdapter> identityRealmLiveApplyAdapters;
    private final IdentityRealmApplyProperties identityRealmApplyProperties;
    private final Clock clock;
    private final WeaverRuntimeProperties weaverRuntimeProperties;

    @Autowired
    public AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            OrganizationBootstrapRepository organizationBootstrapRepository,
            AuditEventPublisher auditEventPublisher,
            List<IdentityRealmProvider> identityRealmProviders,
            ObjectProvider<IdentityRealmEvidenceRepository> identityRealmEvidenceRepository,
            ObjectProvider<List<IdentityRealmLiveApplyAdapter>> identityRealmLiveApplyAdapters,
            ObjectProvider<IdentityRealmApplyProperties> identityRealmApplyProperties,
            ObjectProvider<WeaverRuntimeProperties> weaverRuntimeProperties) {
        IdentityRealmApplyProperties properties = identityRealmApplyProperties.getIfAvailable(IdentityRealmApplyProperties::new);
        this.providerRegistry = providerRegistry;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.providerSelectionRepository = providerSelectionRepository;
        this.organizationBootstrapRepository = organizationBootstrapRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.identityRealmProviders = identityRealmProviders == null || identityRealmProviders.isEmpty()
                ? List.of(new KeycloakRealmDryRunProvider())
                : List.copyOf(identityRealmProviders);
        this.identityRealmEvidenceRepository = identityRealmEvidenceRepository.getIfAvailable(InMemoryIdentityRealmEvidenceRepository::new);
        this.identityRealmApplyProperties = properties;
        List<IdentityRealmLiveApplyAdapter> adapters = identityRealmLiveApplyAdapters.getIfAvailable(List::of);
        this.identityRealmLiveApplyAdapters = adapters == null || adapters.isEmpty()
                ? List.of(new KeycloakRealmLiveApplyAdapter(this.identityRealmApplyProperties))
                : List.copyOf(adapters);
        this.weaverRuntimeProperties = weaverRuntimeProperties.getIfAvailable(
                () -> new WeaverRuntimeProperties(false, null, null, null, null, null, null, null, null, null, null, false, false, true, false));
        this.clock = Clock.systemUTC();
    }

    AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            OrganizationBootstrapRepository organizationBootstrapRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this(providerRegistry, workspaceCapabilityService, providerSelectionRepository, organizationBootstrapRepository, auditEventPublisher,
                List.of(new KeycloakRealmDryRunProvider()), new InMemoryIdentityRealmEvidenceRepository(), List.of(new KeycloakRealmLiveApplyAdapter(new IdentityRealmApplyProperties())), new IdentityRealmApplyProperties(), clock,
                new WeaverRuntimeProperties(false, null, null, null, null, null, null, null, null, null, null, false, false, true, false));
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
            Clock clock,
            WeaverRuntimeProperties weaverRuntimeProperties) {
        this.providerRegistry = providerRegistry;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.providerSelectionRepository = providerSelectionRepository;
        this.organizationBootstrapRepository = organizationBootstrapRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.identityRealmProviders = identityRealmProviders == null || identityRealmProviders.isEmpty()
                ? List.of(new KeycloakRealmDryRunProvider())
                : List.copyOf(identityRealmProviders);
        this.identityRealmEvidenceRepository = identityRealmEvidenceRepository == null
                ? new InMemoryIdentityRealmEvidenceRepository()
                : identityRealmEvidenceRepository;
        this.identityRealmApplyProperties = identityRealmApplyProperties == null
                ? new IdentityRealmApplyProperties()
                : identityRealmApplyProperties;
        this.identityRealmLiveApplyAdapters = identityRealmLiveApplyAdapters == null || identityRealmLiveApplyAdapters.isEmpty()
                ? List.of(new KeycloakRealmLiveApplyAdapter(this.identityRealmApplyProperties))
                : List.copyOf(identityRealmLiveApplyAdapters);
        this.clock = clock;
        this.weaverRuntimeProperties = weaverRuntimeProperties == null
                ? new WeaverRuntimeProperties(false, null, null, null, null, null, null, null, null, null, null, false, false, true, false)
                : weaverRuntimeProperties;
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
                        .map(selection -> toSelectionResponse(selection, false, readinessFor(selection.category(), registry)))
                        .toList(),
                whitelist(jwt),
                weaverDistributionPolicy(registry),
                weaverEligibilityPreview(),
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
        ProviderSelection selection = validateProviderSelection(request, jwt);
        boolean dryRun = request.dryRun();
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
        return toSelectionResponse(applied, dryRun, dryRun ? "dry_run_valid" : "admin_selected_pending_readiness");
    }

    public ProviderReplacementDryRunResponse dryRunProviderReplacement(ProviderReplacementDryRunRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "admin-control-plane", "provider-replacement-dry-run");
        if (request == null || request.category() == null || request.category().isBlank()
                || request.currentAdapter() == null || request.currentAdapter().isBlank()
                || request.targetAdapter() == null || request.targetAdapter().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-invalid",
                    "Provider replacement dry-run requires category, current adapter, and target adapter.",
                    Map.of("reason", "category/currentAdapter/targetAdapter are required"));
        }
        String category = request.category().trim();
        String currentAdapter = request.currentAdapter().trim();
        String targetAdapter = request.targetAdapter().trim();
        if (ProviderCategoryCatalog.category(category).isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-category-unknown",
                    "Provider category is not part of the Weave canonical control-plane contract.",
                    Map.of("category", category));
        }
        if (!providerMatchesCategory(currentAdapter, category) || !providerMatchesCategory(targetAdapter, category)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-category-mismatch",
                    "Provider replacement adapters must both be registered as support-safe candidates for the selected category.",
                    Map.of("category", category, "adapters", "unsupported-adapter-redacted"));
        }
        String choiceModel = selectionChoiceModel(request.choiceModel());
        requiredSecretRef(request.secretRef());
        String declaredSourceOfTruth = safeSourceOfTruth(request.sourceOfTruth());
        List<String> adminNotes = safeLossyMappingNotes(request.lossyMappingNotes());
        boolean matrixChatDryRun = "chat".equals(category) && (isMatrixChatAdapter(currentAdapter) || isMatrixChatAdapter(targetAdapter));
        List<String> conflicts = new ArrayList<>();
        if (currentAdapter.equalsIgnoreCase(targetAdapter)) {
            conflicts.add("Current and target adapters are identical; record no-op or choose a distinct target before activation.");
        }
        if (matrixChatDryRun) {
            conflicts.add("Matrix Chat production apply/cutover remains blocked from Sprint 15 dry-run evidence; only the bounded Sprint 18 fixture apply/cutover/rollback proof may be reviewed.");
            conflicts.add("Encrypted room history requires a future client-side key/export strategy before any migration claim.");
            conflicts.add("Power-level parity and media retention stay manual-review blockers until operator evidence resolves them.");
        }
        boolean migrationRequired = true;
        String status = conflicts.isEmpty() ? "dry-run-ready" : matrixChatDryRun ? "dry-run-blocked-for-apply" : "requires-admin-review";
        String dryRunId = "provider-replacement-dry-run-" + category + "-" + Instant.now(clock).toEpochMilli();
        String auditRef = "provider-replacement-dry-run-" + category + "-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "provider-replacement-dry-run",
                AuditAction.PROVIDER_REPLACEMENT_DRY_RUN,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.ofEntries(
                        Map.entry("category", category),
                        Map.entry("currentAdapter", currentAdapter),
                        Map.entry("targetAdapter", targetAdapter),
                        Map.entry("choiceModel", choiceModel),
                        Map.entry("sourceOfTruth", declaredSourceOfTruth),
                        Map.entry("secretRefPresent", true),
                        Map.entry("secretRef", safeSecretRef(request.secretRef())),
                        Map.entry("migrationDryRunRequired", migrationRequired),
                        Map.entry("portableExportImportRequired", request.portableExportImportRequired()),
                        Map.entry("lossyMappingNoteCount", adminNotes.size()),
                        Map.entry("rawProviderError", "redacted before audit"),
                        Map.entry("token", "not-stored"))));
        return new ProviderReplacementDryRunResponse(
                dryRunId,
                status,
                "dry-run",
                category,
                currentAdapter,
                targetAdapter,
                choiceModel,
                declaredSourceOfTruth,
                true,
                conflicts.isEmpty() ? "ready-for-admin-review" : "blocked-until-conflicts-resolved",
                migrationRequired,
                new ProviderReplacementDryRunResponse.LossyMappingReport(
                        ProviderCapabilityContracts.canonicalObjects(category),
                        ProviderCapabilityContracts.lossyMappingRisks(category),
                        adminNotes,
                        conflicts,
                        ProviderCapabilityContracts.replacementRequirement(category)),
                new ProviderReplacementDryRunResponse.LifecycleExpectations(
                        ProviderCapabilityContracts.sourceOfTruth(category),
                        ProviderCapabilityContracts.exportDeleteExpectation(category),
                        ProviderCapabilityContracts.exportDeleteExpectation(category),
                        "deprovision source identities, groups, memberships, grants, and service principals through the authoritative provider before capability cutover",
                        "rollback is an admin decision boundary; dry-run does not mutate provider state and apply must preserve mapping history"),
                new ProviderReplacementDryRunResponse.PortableExportImportContract(
                        category + "-portable-export-manifest-v0.1",
                        category + "-portable-import-manifest-v0.1",
                        "v0.1 guarantees a documented portable export/import contract before claiming automated migration.",
                        List.of("full automated cross-provider migration is not claimed in v0.1"),
                        List.of("provider-switch-preflight", "portable-export-import-contract", "rollback-recovery-plan", auditRef)),
                new ProviderReplacementDryRunResponse.SwitchPlan(
                        category + "-switch-plan-v0.1",
                        true,
                        true,
                        true,
                        matrixChatDryRun ? "coming_later" : "degraded",
                        matrixChatDryRun
                                ? List.of(
                                        "keep current Chat provider active; production cutover is not authorized by this proof",
                                        "retain source Matrix exports and rollback archive refs until media and permission-impact review is complete",
                                        "route member copy through provider-neutral states only")
                                : List.of(
                                        "keep current adapter active until export/import evidence is accepted",
                                        "block apply when rollback evidence or support-safe audit refs are missing")),
                consequencePreview(category, matrixChatDryRun, adminNotes, conflicts),
                noUnaccountedDataLossReport(category, matrixChatDryRun, adminNotes),
                boundedProof(category, matrixChatDryRun, auditRef),
                crossDomainImpact(category, matrixChatDryRun, auditRef),
                matrixChatDryRun
                        ? List.of(
                                "SecretRef exists and remains backend-only; raw credentials are never returned.",
                                "Backend Matrix Chat proof may only exercise bounded fixture apply/cutover/rollback evidence; production cutover remains blocked.",
                                "Resolve encrypted-room history, power-level impact, media retention, audit, rollback restore-smoke, and release-claim evidence before any future production gate.")
                        : List.of(
                                "SecretRef exists and remains backend-only; raw credentials are never returned.",
                                "Admin confirms source-of-truth, export/delete, lossy mapping, and rollback/support notes.",
                                "Readiness test and migration dry-run evidence are reviewed before activation."),
                matrixChatDryRun
                        ? List.of("available", "degraded", "unsupported", "coming_later")
                        : List.of("available", "disabled_by_policy", "degraded", "coming_later"),
                true,
                true,
                List.of(auditRef));
    }

    private List<ProviderReplacementDryRunResponse.CrossDomainImpactItem> crossDomainImpact(
            String category,
            boolean matrixChatDryRun,
            String auditRef) {
        if (!matrixChatDryRun) {
            return List.of(new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                    category,
                    "weave:" + category + ":provider-replacement-scope",
                    "manual_review",
                    "Backend dry-run must classify provider replacement impact before any apply or cutover claim.",
                    List.of(auditRef, category + "-portable-export-manifest-v0.1", category + "-portable-import-manifest-v0.1"),
                    List.of("cross-domain provider impact report is required before apply.")));
        }
        return List.of(
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "chat",
                        "weave:chat:conversation/sprint19-matrix-room",
                        "portable",
                        "Conversation metadata, current membership, simple replies, and canonical message refs are portable inside the bounded fixture.",
                        List.of("impact:s19:chat:matrix-room:portable", "specs/0006-portability-contract/matrix-synapse-chat-cross-domain-impact-proof.json"),
                        List.of()),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "files",
                        "weave:files:attachment-ref/sprint19-channel-media",
                        "archive_only",
                        "Matrix media references stay archive-only unless copied into Weave-controlled storage under an approved retention policy.",
                        List.of("impact:s19:files:attachment-retention", "docs/matrix-chat-migration-proof.md"),
                        List.of("media retention decision and rollback archive refs are required before cutover.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "boards",
                        "weave:boards:task-comment-link/sprint19-linked-decision",
                        "manual_review",
                        "Task/comment/watchers linked from Chat require manual review because Matrix sender roles do not map 1:1 to board permissions.",
                        List.of("impact:s19:boards:task-comment-watchers", "docs/matrix-chat-migration-proof.md"),
                        List.of("manual-review decision is required for board watcher and attachment relation impact.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "calendar",
                        "weave:calendar:event-link/sprint19-room-meeting",
                        "lossy",
                        "Meeting links and recurrence/resource metadata can be preserved only as support-safe refs when provider-specific room state has no canonical equivalent.",
                        List.of("impact:s19:calendar:meeting-link-recurrence", "docs/matrix-chat-migration-proof.md"),
                        List.of("calendar recurrence/resource lossy mapping must be accepted before cutover.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "decisions",
                        "weave:decisions:evidence-link/sprint19-chat-rationale",
                        "unsupported",
                        "Encrypted or redacted Chat rationale cannot be promoted into Decisions evidence by server-side migration and remains unsupported.",
                        List.of("impact:s19:decisions:encrypted-rationale", "docs/evidence/accessibility/sprint-18-manual-at-blocker.md"),
                        List.of("unsupported encrypted rationale blocks lossless migration and production replacement claims.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "chat",
                        "weave:chat:provider-extension/sprint19-federated-widget",
                        "vendor_locked",
                        "Provider-specific widgets and federated extension state stay vendor-locked and cannot be represented as portable Weave domain data.",
                        List.of("impact:s19:chat:vendor-locked-widget", "specs/0006-portability-contract/matrix-synapse-chat-cross-domain-impact-proof.json"),
                        List.of("vendor-locked extension state blocks all-provider portability claims.")));
    }

    private ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport noUnaccountedDataLossReport(
            String category,
            boolean matrixChatDryRun,
            List<String> adminNotes) {
        if (matrixChatDryRun) {
            return new ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport(
                    42,
                    7,
                    3,
                    5,
                    11,
                    0,
                    List.of("Complex relations and exact Matrix power-level parity are known lossy/manual-review areas."),
                    List.of("Encrypted Matrix history is unsupported for server-side migration without client-side key/export evidence."),
                    List.of(
                            "Rollback can clean bounded target imports and rely on retained source/archive refs.",
                            "Rollback cannot recreate unsupported encrypted history or exact Matrix power-level parity."),
                    List.of(
                            "This is one bounded Chat-domain Matrix/Synapse proof, not production migration availability.",
                            "No lossless migration, legal-compliance, E2EE-history, private-channel parity, or all-provider portability claim is made."));
        }
        return new ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport(
                Math.max(1, ProviderCapabilityContracts.canonicalObjects(category).size()),
                ProviderCapabilityContracts.lossyMappingRisks(category).size(),
                0,
                adminNotes.size(),
                0,
                0,
                ProviderCapabilityContracts.lossyMappingRisks(category),
                List.of(),
                List.of("Rollback boundary follows backend dry-run and archive evidence."),
                List.of("Provider replacement claims remain bounded by accepted dry-run evidence."));
    }

    private ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof boundedProof(
            String category,
            boolean matrixChatDryRun,
            String auditRef) {
        if (matrixChatDryRun) {
            return new ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof(
                    "fixture_only_matrix_synapse_chat_sprint18",
                    true,
                    false,
                    true,
                    List.of(
                            category + "-portable-export-manifest-v0.1",
                            category + "-portable-import-manifest-v0.1",
                            category + "-cutover-plan-v0.1",
                            category + "-rollback-restore-smoke-v0.1",
                            category + "-no-unaccounted-data-loss-report-v0.1",
                            auditRef),
                    List.of(
                            "production provider mutation and cutover are blocked",
                            "manual-review Matrix power-level and media-retention decisions remain unresolved",
                            "encrypted history remains unsupported/coming_later"));
        }
        return new ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof(
                "dry_run_only",
                false,
                false,
                true,
                List.of(auditRef),
                List.of("bounded apply proof is not available for this provider category"));
    }

    private ProviderReplacementDryRunResponse.ConsequencePreview consequencePreview(
            String category,
            boolean matrixChatDryRun,
            List<String> adminNotes,
            List<String> conflicts) {
        if (matrixChatDryRun) {
            return new ProviderReplacementDryRunResponse.ConsequencePreview(
                    42,
                    7,
                    3,
                    5,
                    11,
                    List.of(
                            "Members keep Chat access during review; migration apply is coming_later and no provider internals are shown.",
                            "Encrypted history is unsupported for server migration until a client-side export strategy exists.",
                            "Some permissions and media require manual_review before any future cutover."),
                    List.of(
                            "Rollback depends on retained source Matrix export and support-safe archive refs.",
                            "Rollback cannot recreate unsupported encrypted history or exact Matrix power-level parity."),
                    List.copyOf(conflicts));
        }
        return new ProviderReplacementDryRunResponse.ConsequencePreview(
                Math.max(1, ProviderCapabilityContracts.canonicalObjects(category).size()),
                ProviderCapabilityContracts.lossyMappingRisks(category).size(),
                0,
                adminNotes.size(),
                0,
                List.of("Members see provider-neutral capability states while admins review replacement consequences."),
                List.of("Rollback boundary follows backend dry-run and archive evidence."),
                List.copyOf(conflicts));
    }

    private boolean isMatrixChatAdapter(String adapter) {
        String normalized = adapter == null ? "" : adapter.toLowerCase(Locale.ROOT);
        return normalized.contains("matrix") || normalized.contains("synapse");
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
                        "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure", "weaver.exec_disabled"));
            }
            if (roles.contains("operator")) {
                grants.addAll(List.of("admin_control_plane.readiness_read", "operator.support_bundle.create", "release_evidence.read", "manuals.admin", "manuals.read", "weaver.exec_disabled"));
            }
            if (roles.contains("member")) {
                grants.addAll(List.of("chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "boards.read", "meetings.join", "documents.view", "decisions.read", "manuals.read", "release_evidence.read", "weaver.exec_disabled"));
            }
            for (String group : groups) {
                grants.addAll(SIMULATION_GROUP_CAPABILITIES.getOrDefault(group, List.of()));
            }
            grants.remove("weaver.enabled");
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

    public IdentityRealmDryRunReport dryRunIdentityRealm(IdentityRealmDryRunRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "identity-realm", "dry-run");
        IdentityRealmProvider provider = identityRealmProvider("keycloak-realm");
        IdentityRealmDryRunReport report = provider.dryRun(request);
        String auditRef = "identity-realm-dry-run-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "identity-realm-dry-run",
                AuditAction.PROVIDER_REPLACEMENT_DRY_RUN,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "providerKey", provider.providerKey(),
                        "realmId", report.realmId(),
                        "readiness", report.readiness(),
                        "changeCount", report.changes().size(),
                        "blockerCount", report.blockers().size(),
                        "supportSafe", report.supportSafe(),
                        "rawSecretExposed", report.rawSecretExposed(),
                        "destructiveApplyAvailable", report.destructiveApplyAvailable(),
                        "dryRunReasonPresent", request != null && request.reason() != null && !request.reason().isBlank())));
        IdentityRealmDryRunReport persistedReport = new IdentityRealmDryRunReport(
                report.providerKey(),
                report.realmId(),
                report.dryRunId(),
                report.operation(),
                report.readiness(),
                report.destructiveApplyAvailable(),
                report.supportSafe(),
                report.rawSecretExposed(),
                report.changes(),
                report.readinessChecks(),
                report.diff(),
                report.warnings(),
                report.blockers(),
                report.nextActions(),
                List.of(auditRef));
        identityRealmEvidenceRepository.save(new IdentityRealmDryRunEvidence(
                persistedReport.dryRunId(),
                auditRef,
                provider.providerKey(),
                persistedReport.realmId(),
                persistedReport,
                Instant.now(clock)));
        return persistedReport;
    }

    public IdentityRealmApplyReport applyIdentityRealm(IdentityRealmApplyRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "identity-realm", "apply");
        IdentityRealmProvider provider = identityRealmProvider("keycloak-realm");
        IdentityRealmDryRunReport requestedDryRun = provider.dryRun(request == null ? null : request.dryRunRequest());
        Optional<IdentityRealmDryRunEvidence> persistedEvidence = identityRealmEvidenceRepository.findDryRun(request == null ? null : request.dryRunId());
        IdentityRealmDryRunReport dryRun = persistedEvidence.map(IdentityRealmDryRunEvidence::report).orElse(requestedDryRun);
        long safeChangeCount = dryRun.changes().stream().filter(change -> "safe".equals(change.classification())).count();
        long riskyChangeCount = dryRun.changes().stream().filter(change -> "risky".equals(change.classification())).count();
        long destructiveChangeCount = dryRun.changes().stream().filter(change -> "destructive".equals(change.classification())).count();
        boolean hasRisky = riskyChangeCount > 0;
        boolean hasDestructive = destructiveChangeCount > 0;
        boolean rollbackRequired = hasRisky || hasDestructive;
        boolean rollbackAccepted = !rollbackRequired || hasText(request == null ? null : request.rollbackEvidenceRef());
        boolean lastAdminGuardPassed = retainedAdminProofPresent(request);
        boolean confirmationProvided = request != null && "APPLY WEAVE IDENTITY REALM".equals(request.confirmationPhrase());
        boolean policySimulationPresent = request != null
                && hasText(request.policySimulationRef())
                && request.policySimulationRef().startsWith("effective-policy-simulation-");
        boolean persistedDryRunFresh = persistedEvidence
                .filter(evidence -> evidence.providerKey().equals(provider.providerKey()))
                .filter(evidence -> evidence.dryRunId().equals(requestedDryRun.dryRunId()))
                .filter(evidence -> !evidence.createdAt().plusSeconds(identityRealmApplyProperties.dryRunFreshnessSeconds()).isBefore(Instant.now(clock)))
                .isPresent();
        List<String> blocked = new ArrayList<>();
        if (!persistedDryRunFresh) {
            blocked.add("fresh persisted dry-run evidence is required before identity realm apply");
        }
        if (!policySimulationPresent) {
            blocked.add("effective policy simulation evidence ref is required before identity realm apply");
        }
        if (!confirmationProvided) {
            blocked.add("explicit confirmation phrase is required");
        }
        if (!lastAdminGuardPassed) {
            blocked.add("last-admin guard requires at least one retained immutable admin identity key");
        }
        if (hasRisky && (request == null || !request.approveRisky())) {
            blocked.add("risky changes require approveRisky=true");
        }
        if (hasDestructive && (request == null || !request.approveDestructive())) {
            blocked.add("destructive changes require approveDestructive=true");
        }
        if (hasDestructive && !provider.destructiveApplyAvailable()) {
            blocked.add("provider destructive apply is not available for this contract");
        }
        if (!rollbackAccepted) {
            blocked.add("rollback/restore evidence ref is required for risky or destructive apply");
        }
        blocked.addAll(dryRun.blockers());
        boolean guardsAccepted = blocked.isEmpty();
        IdentityRealmLiveApplyAdapter.IdentityRealmLiveApplyResult liveApply = guardsAccepted
                ? identityRealmLiveApplyAdapter(provider.providerKey()).apply(persistedEvidence.orElseThrow(), request)
                : new IdentityRealmLiveApplyAdapter.IdentityRealmLiveApplyResult(false, false, "guarded-provider-apply-blocked-before-adapter", List.of(), List.of());
        blocked.addAll(liveApply.blockedReasons());
        boolean accepted = blocked.isEmpty();
        boolean applied = accepted && liveApply.applied();
        boolean providerMutationPerformed = accepted && liveApply.providerMutationPerformed();
        String executionMode = accepted ? liveApply.executionMode() : "guarded-provider-apply-blocked-before-mutation";
        List<String> nextActions = new ArrayList<>(applyNextActions(accepted, blocked, rollbackRequired, hasRisky, hasDestructive));
        nextActions.addAll(liveApply.nextActions());
        nextActions = nextActions.stream().distinct().toList();
        String auditRef = "identity-realm-apply-" + Instant.now(clock).toEpochMilli();
        String actorRef = actorRef(jwt);
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef,
                "identity-realm-apply",
                AuditAction.IDENTITY_REALM_APPLY_GUARDED,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.ofEntries(
                        Map.entry("actorRef", actorRef),
                        Map.entry("candidateRef", "identity-realm:" + dryRun.realmId()),
                        Map.entry("planRef", dryRun.dryRunId()),
                        Map.entry("providerKey", provider.providerKey()),
                        Map.entry("realmId", dryRun.realmId()),
                        Map.entry("decision", accepted ? "accepted" : "blocked"),
                        Map.entry("result", accepted ? (applied ? "accepted-with-provider-mutation" : "accepted-without-provider-mutation") : "blocked-before-provider-mutation"),
                        Map.entry("executionMode", executionMode),
                        Map.entry("liveApplyEnabled", identityRealmApplyProperties.liveApplyEnabled()),
                        Map.entry("providerConfigured", identityRealmApplyProperties.providerConfigured()),
                        Map.entry("providerMutationPerformed", providerMutationPerformed),
                        Map.entry("safeChangeCount", safeChangeCount),
                        Map.entry("riskyChangeCount", riskyChangeCount),
                        Map.entry("destructiveChangeCount", destructiveChangeCount),
                        Map.entry("blockedReasonCount", blocked.size()),
                        Map.entry("nextActionCount", nextActions.size()),
                        Map.entry("persistedDryRunEvidencePresent", persistedEvidence.isPresent()),
                        Map.entry("persistedDryRunFresh", persistedDryRunFresh),
                        Map.entry("effectivePolicySimulationEvidencePresent", policySimulationPresent),
                        Map.entry("confirmationProvided", confirmationProvided),
                        Map.entry("retainedAdminIdentityKeyCount", request == null ? 0 : request.retainedAdminPrimaryIdentityKeys().stream().filter(this::safePrimaryIdentityKey).count()),
                        Map.entry("rollbackRestoreEvidencePresent", request != null && hasText(request.rollbackEvidenceRef())),
                        Map.entry("lastAdminGuardPassed", lastAdminGuardPassed),
                        Map.entry("rollbackEvidenceAccepted", rollbackAccepted),
                        Map.entry("supportSafe", true))));
        return new IdentityRealmApplyReport(
                provider.providerKey(),
                dryRun.realmId(),
                dryRun.dryRunId(),
                accepted ? "accepted" : "blocked",
                executionMode,
                applied,
                providerMutationPerformed,
                true,
                false,
                lastAdminGuardPassed,
                rollbackRequired,
                rollbackAccepted,
                blocked.stream().distinct().toList(),
                dryRun.changes(),
                nextActions,
                List.of(auditRef));
    }

    private boolean retainedAdminProofPresent(IdentityRealmApplyRequest request) {
        if (request == null || request.retainedAdminPrimaryIdentityKeys().isEmpty()) {
            return false;
        }
        IdentityRealmDesiredState desiredState = request.dryRunRequest().desiredState();
        Set<String> retainedSafeKeys = request.retainedAdminPrimaryIdentityKeys().stream()
                .filter(this::safePrimaryIdentityKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (retainedSafeKeys.isEmpty()) {
            return false;
        }
        Set<String> desiredLastAdminRefs = desiredState.lastAdminSubjectRefs().stream()
                .filter(this::safePrimaryIdentityKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> recoveryAdminRefs = desiredState.breakGlassIdentities().stream()
                .filter(identity -> identity != null && identity.breakGlass())
                .filter(identity -> identity.roles().stream().map(role -> role.toLowerCase(Locale.ROOT)).anyMatch(role -> role.equals("owner") || role.equals("admin")))
                .map(IdentityRealmDesiredState.RecoveryIdentity::subjectRef)
                .filter(this::safePrimaryIdentityKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return retainedSafeKeys.stream().anyMatch(key -> desiredLastAdminRefs.contains(key) || recoveryAdminRefs.contains(key));
    }

    private List<String> applyNextActions(
            boolean accepted,
            List<String> blocked,
            boolean rollbackRequired,
            boolean hasRisky,
            boolean hasDestructive) {
        if (accepted) {
            return List.of(
                    "Guarded apply decision accepted after persisted dry-run, policy simulation, retained-admin, rollback, audit, and confirmation checks.",
                    "Archive the dry-run, policy simulation, retained-admin, rollback/export evidence, and audit ref before any future provider adapter retry.");
        }
        List<String> nextActions = new ArrayList<>();
        if (blocked.stream().anyMatch(reason -> reason.contains("confirmation"))) {
            nextActions.add("Re-submit with confirmationPhrase=APPLY WEAVE IDENTITY REALM after reviewing the dry-run.");
        }
        if (blocked.stream().anyMatch(reason -> reason.contains("last-admin"))) {
            nextActions.add("Retain at least one immutable owner/admin primary identity key such as issuer+subject before retrying.");
        }
        if (hasRisky) {
            nextActions.add("Review risky change classifications, run effective policy simulation, and set approveRisky=true only with operator evidence.");
        }
        if (hasDestructive) {
            nextActions.add("Treat destructive changes as unavailable until provider destructive apply support and restore evidence are explicitly proven.");
        }
        if (rollbackRequired && blocked.stream().anyMatch(reason -> reason.contains("rollback/restore evidence"))) {
            nextActions.add("Attach a support-safe rollback/restore evidence reference before retrying risky or destructive apply.");
        }
        nextActions.add("Resolve blockedReasons and re-run /api/admin/identity/realm/dry-run before another apply attempt.");
        return nextActions.stream().distinct().toList();
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
        profileCapabilities.put("group:weaver-group", List.of("weaver.files_read", "weaver.exec_disabled"));
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

    private IdentityRealmProvider identityRealmProvider(String providerKey) {
        return identityRealmProviders.stream()
                .filter(provider -> provider.providerKey().equals(providerKey))
                .findFirst()
                .orElseGet(KeycloakRealmDryRunProvider::new);
    }

    private IdentityRealmLiveApplyAdapter identityRealmLiveApplyAdapter(String providerKey) {
        return identityRealmLiveApplyAdapters.stream()
                .filter(adapter -> adapter.providerKey().equals(providerKey))
                .findFirst()
                .orElseGet(() -> new KeycloakRealmLiveApplyAdapter(identityRealmApplyProperties));
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
        if (ProviderCategoryCatalog.category(category).isEmpty()) {
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

    private boolean providerMatchesCategory(String providerKey, String category) {
        boolean registeredCandidate = providerRegistry.status().providers().stream()
                .filter(provider -> ProviderCategoryCatalog.providerMatchesCategory(provider, category))
                .anyMatch(provider -> providerKeyMatches(provider, providerKey));
        if (registeredCandidate) {
            return true;
        }
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return ProviderCapabilityContracts.providerCandidates(category).stream()
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
                        projectionItem("model-alias-general", "model", "general-assistant via " + modelProviderKey, readinessFor("model", registry), "disabled_by_policy", "Alias is admin-selected but runtime remains disabled by default.", false),
                        projectionItem("tool-calendar-search", "tool", "calendar.search_events", readinessFor("calendar", registry), "disabled_by_policy", "Read-only discovery requires weaver.calendar_read and calendar.read grants.", false),
                        projectionItem("tool-boards-comment", "tool", "boards.comment", readinessFor("boards-tasks", registry), "disabled_by_policy", "Write-like tool requires explicit approval receipt and audit.", true),
                        projectionItem("mcp-weave-domain-tools", "mcp", "weave-domain-tools via streamable-http", "configured", "disabled_by_policy", "Admin-bound MCP server is discoverable only to granted RuntimeProfiles and remains disabled until org policy enables Weaver.", true),
                        projectionItem("consent-shared-space", "mcp", "shared-space consent gate", "admin-action-required", "disabled_by_policy", "Group chat/shared-space participation requires org policy and consent evidence.", true)));
    }

    private WeaverEligibilityPreviewResponse weaverEligibilityPreview() {
        boolean policyEnabled = weaverRuntimeProperties.enabled();
        List<String> requiredGroups = weaverRuntimeProperties.enabledGroups();
        String canonicalGroup = requiredGroups.isEmpty() ? "weaver-group" : requiredGroups.get(0);
        List<String> blockedReasons = new ArrayList<>();
        if (!policyEnabled) {
            blockedReasons.add("weaver.enabled remains blocked until organization policy enables governed Weaver runtime provisioning");
        }
        blockedReasons.add("members outside " + canonicalGroup + " stay deny-by-default for Weaver runtime provisioning");
        return new WeaverEligibilityPreviewResponse(
                policyEnabled,
                true,
                requiredGroups,
                List.of("weaver.files_read", "weaver.exec_disabled"),
                "disabled_by_policy",
                "disabled_by_policy",
                policyEnabled ? "coming_later" : "disabled_by_policy",
                blockedReasons,
                List.of(
                        "Grant weaver.enabled through organization policy before runtime rollout.",
                        "Map eligible members into " + canonicalGroup + " only after member impact preview and audit review."),
                List.of("audit://weaver/eligibility-preview"));
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
        String readiness = readinessFor("model", registry);
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
        return value != null && value.matches("[a-z][a-z0-9_.:-]*");
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
        if ("weaver.enabled".equals(capability)) {
            return new EffectivePolicySimulationResponse.CapabilityState(
                    capability,
                    "disabled",
                    "weaver-default-disabled",
                    "Weaver remains opt-in, governed, audited, and disabled by default.");
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
