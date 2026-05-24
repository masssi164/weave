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
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestResponse;
import com.massimotter.weave.backend.model.admin.SecretRefResponse;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    @Autowired
    public AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher) {
        this(providerRegistry, workspaceCapabilityService, auditEventPublisher, Clock.systemUTC());
    }

    AdminControlPlaneService(
            ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.providerRegistry = providerRegistry;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public AdminControlPlaneResponse overview(Jwt jwt) {
        ProviderRegistryResponse registry = providerRegistry.status();
        return new AdminControlPlaneResponse(
                "admin-control-plane-v1",
                organizationId(jwt),
                organizationName(jwt),
                "keycloak",
                registry.backendOwnedFacades(),
                true,
                registry.supportSafe(),
                false,
                Instant.now(clock),
                registry.categories(),
                whitelist(jwt),
                secretRefs(registry),
                Map.of(
                        "providers", "/api/providers/status",
                        "policy", "/api/admin/policies/capability-whitelist",
                        "audit", "/api/admin/audit/events",
                        "readinessTest", "/api/admin/providers/readiness-tests"));
    }

    public CapabilityWhitelistResponse whitelist(Jwt jwt) {
        WorkspaceCapabilityPolicyResponse policy = workspaceCapabilityService.policySnapshot(jwt);
        Map<String, List<String>> profileCapabilities = new LinkedHashMap<>();
        profileCapabilities.put("workspace-admin", List.of(
                "chat.read", "chat.send", "files.read", "files.upload", "calendar.read", "calendar.manage_events",
                "boards.read", "boards.update_task", "weaver.exec_disabled"));
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
                .filter(provider -> provider.providerKey().equals(providerKey))
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
