package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.admin.AdminAuditEventResponse;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapRequest;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapResponse;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestResponse;
import com.massimotter.weave.backend.model.admin.ProviderSelectionRequest;
import com.massimotter.weave.backend.model.admin.ProviderSelectionResponse;
import com.massimotter.weave.backend.model.admin.SecretRefResponse;
import com.massimotter.weave.backend.provider.ProviderCapabilityContracts;
import com.massimotter.weave.backend.provider.ProviderCategoryCatalog;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AdminControlPlaneService {

    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "ready",
            "disabled",
            "degraded",
            "policy-blocked");

    private final ProviderRegistry providerRegistry;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    @Autowired
    public AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            AuditEventPublisher auditEventPublisher) {
        this(providerRegistry, workspaceCapabilityService, providerSelectionRepository, auditEventPublisher, Clock.systemUTC());
    }

    AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository providerSelectionRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.providerRegistry = providerRegistry;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.providerSelectionRepository = providerSelectionRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public AdminControlPlaneResponse overview(Jwt jwt) {
        ProviderRegistryResponse registry = providerRegistry.status();
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
                secretRefs(registry),
                Map.of(
                        "providers", "/api/providers/status",
                        "policy", "/api/admin/policies/capability-whitelist",
                        "audit", "/api/admin/audit/events",
                        "readinessTest", "/api/admin/providers/readiness-tests",
                        "providerSelections", "/api/admin/providers/selections"));
    }

    public ProviderSelectionResponse selectProvider(ProviderSelectionRequest request, Jwt jwt) {
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
                    Map.of(
                            "category", selection.category(),
                            "providerKey", selection.providerKey(),
                            "choiceModel", selection.choiceModel(),
                            "secretRef", safeSecretRef(selection.secretRef()),
                            "reason", safeText(request.reason()),
                            "providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                            "migrationDryRunRequired", selection.migrationDryRunRequired(),
                            "lossyMappingNoteCount", selection.lossyMappingNotes().size(),
                            "token", "not-stored")));
        }
        return toSelectionResponse(applied, dryRun, dryRun ? "dry_run_valid" : "admin_selected_pending_readiness");
    }

    public EffectivePolicyResponse effectivePolicy(Jwt jwt) {
        return workspaceCapabilityService.effectivePolicySnapshot(jwt, "organization");
    }

    public CapabilityWhitelistResponse whitelist(Jwt jwt) {
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
        if (request == null || request.profileKey() == null || request.profileKey().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "capability-whitelist-invalid",
                    "Capability whitelist update requires a profile key.",
                    Map.of("reason", "profileKey is required"));
        }
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
                        "profileKey", request.profileKey().trim(),
                        "capabilityKeys", safeCapabilities(request.capabilityKeys()),
                        "reason", safeText(request.reason()),
                        "denyByDefault", true,
                        "rawProviderError", "redacted before audit",
                        "token", "not-stored")));
        return whitelist(jwt);
    }

    public OrganizationBootstrapResponse bootstrapOrganization(OrganizationBootstrapRequest request, Jwt jwt) {
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
        List<String> retainedAdmins = retainedAdminSubjectKeys(request.adminSubjectKeys(), identity.primaryIdentityKey());
        String auditRef = "organization-bootstrap-" + organizationId + "-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId,
                "admin-control-plane",
                actorRef(jwt),
                "organization-bootstrap",
                AuditAction.ADMIN_POLICY_UPDATED,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "bootstrapMode", bootstrapMode,
                        "actorPrimaryIdentityKey", identity.primaryIdentityKey(),
                        "retainedAdminSubjectKeyCount", retainedAdmins.size(),
                        "lastAdminGuardPassed", true,
                        "supportSafe", true,
                        "emailPrimaryKey", false,
                        "reason", safeText(request.reason()))));
        return new OrganizationBootstrapResponse(
                organizationId,
                bootstrapMode,
                identity.primaryIdentityKey(),
                retainedAdmins,
                true,
                true,
                Instant.now(clock),
                List.of(auditRef));
    }

    public ProviderReadinessTestResponse testProviderReadiness(ProviderReadinessTestRequest request, Jwt jwt) {
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

    public List<AdminAuditEventResponse> auditEvents() {
        if (auditEventPublisher instanceof InMemoryAuditEventPublisher memoryAudit) {
            return memoryAudit.events().stream()
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
        return List.of();
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
        if (!"workspace-admin".equals(request.profileKey().trim())) {
            return;
        }
        List<String> capabilities = safeCapabilities(request.capabilityKeys());
        if (capabilities.isEmpty() || capabilities.contains("admin.policy.edit")) {
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
        if (!trimmed.matches("[a-z0-9][a-z0-9-]{1,62}")) {
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

    private List<String> retainedAdminSubjectKeys(List<String> suppliedKeys, String actorPrimaryIdentityKey) {
        List<String> retained = Stream.concat(suppliedKeys.stream(), Stream.of(actorPrimaryIdentityKey))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (!retained.contains(actorPrimaryIdentityKey)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "last-admin-guard",
                    "Organization bootstrap must retain the current owner/admin as a recovery administrator.",
                    Map.of("supportSafe", true));
        }
        return retained;
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
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "provider-selection-secretref-invalid",
                "Provider selections may reference credentials only through SecretRef URIs.",
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
                readiness);
    }

    private String readinessFor(String category, ProviderRegistryResponse registry) {
        return registry.categories().stream()
                .filter(value -> value.category().equals(category))
                .map(value -> value.readiness().value())
                .findFirst()
                .orElse("unknown");
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
        return value.trim().replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [redacted]");
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
