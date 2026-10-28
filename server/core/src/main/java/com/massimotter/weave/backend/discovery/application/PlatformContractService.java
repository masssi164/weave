package com.massimotter.weave.backend.discovery.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.discovery.api.OrgManifestResponse;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.discovery.port.MatrixPlatformContribution;
import com.massimotter.weave.backend.discovery.port.PlatformDiagnosticContribution;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;

/** Shared manifest and diagnostics assembly consumes only installed domain observations. */
public class PlatformContractService {
    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final PlatformContractProperties platformProperties;
    private final WeaveSecurityProperties securityProperties;
    private final WorkspaceCapabilityProperties workspaceProperties;
    private final WorkspaceCapabilityService capabilities;
    private final MatrixPlatformContribution matrix;
    private final List<PlatformDiagnosticContribution> diagnostics;

    @Value("${weave.platform.release-posture:dogfood}")
    private String releasePosture = "dogfood";

    public PlatformContractService(
            OAuth2ResourceServerProperties resourceServerProperties,
            PlatformContractProperties platformProperties,
            WeaveSecurityProperties securityProperties,
            WorkspaceCapabilityProperties workspaceProperties,
            WorkspaceCapabilityService capabilities,
            List<MatrixPlatformContribution> matrixContributions,
            List<PlatformDiagnosticContribution> diagnostics) {
        this.resourceServerProperties = resourceServerProperties;
        this.platformProperties = platformProperties;
        this.securityProperties = securityProperties;
        this.workspaceProperties = workspaceProperties;
        this.capabilities = capabilities;
        if (matrixContributions.size() > 1) {
            throw new IllegalArgumentException("Duplicate Matrix platform contribution");
        }
        this.matrix = matrixContributions.isEmpty() ? null : matrixContributions.getFirst();
        var identifiers = new HashSet<>(List.of("backend", "auth", "matrix"));
        for (var diagnostic : diagnostics) {
            if (diagnostic.id() == null || diagnostic.id().isBlank()
                    || !identifiers.add(diagnostic.id())) {
                throw new IllegalArgumentException("Invalid or duplicate platform diagnostic contribution");
            }
        }
        this.diagnostics = diagnostics.stream()
                .sorted(Comparator.comparingInt(PlatformDiagnosticContribution::order)
                        .thenComparing(PlatformDiagnosticContribution::id))
                .toList();
    }

    public OrgManifestResponse config() {
        return new OrgManifestResponse(
                2,
                safeManifestUrl(platformProperties.publicBaseUrl(), "organizationOrigin"),
                safeManifestUrl(platformProperties.apiBaseUrl(), "userApiBaseUrl"),
                new OrgManifestResponse.Oidc(oidcIssuerUrl(), securityProperties.clientId()),
                new OrgManifestResponse.Protocols(matrix == null ? null : matrix.advertisedBaseUrl()),
                releasePosture(),
                manifestDomains(),
                List.of());
    }

    public PlatformStatusResponse status(String requestId) {
        var backend = new PlatformStatusResponse.DiagnosticStatus(
                "up", "ready", "The Weave backend process is serving product API diagnostics.", null);
        var auth = authStatus();
        // Validate installed Chat metadata even when Chat is disabled; absence is a distinct graph.
        if (matrix != null) matrix.advertisedBaseUrl();
        var matrixStatus = matrix == null ? null : matrix.status(auth);
        List<PlatformStatusResponse.DiagnosticCheck> checks = new ArrayList<>();
        checks.add(check("backend", "Backend API", backend));
        checks.add(check("auth", "Keycloak auth", auth));
        if (matrixStatus != null) checks.add(new PlatformStatusResponse.DiagnosticCheck(
                "matrix", "Matrix chat", matrixStatus.status(), matrixStatus.readiness(),
                matrixStatus.message(), matrixStatus.action()));
        Map<String, PlatformStatusResponse.DiagnosticStatus> installed = new LinkedHashMap<>();
        for (var diagnostic : diagnostics) {
            var status = java.util.Objects.requireNonNull(
                    diagnostic.status(auth), "Installed diagnostic must return an observation");
            installed.put(diagnostic.id(), status);
            checks.add(check(diagnostic.id(), diagnostic.label(), status));
        }
        return new PlatformStatusResponse(
                requestId, backend, auth, matrixStatus,
                installed.get("files"), installed.get("calendar"), installed.get("nextcloud"),
                List.copyOf(checks), actions(checks));
    }

    private List<OrgManifestResponse.DomainCapability> manifestDomains() {
        List<OrgManifestResponse.DomainCapability> domains = new ArrayList<>();
        domains.add(domain("identity", true, List.of("identity.sign_in", "identity.groups", "identity.roles", "policy.read", "policy.manage")));
        for (var definition : capabilities.installedDefinitions()) {
            if (!definition.manifestCapabilities().isEmpty()) {
                domains.add(domain(definition.domainId(), definition.settings().enabled(), definition.manifestCapabilities()));
            }
        }
        domains.add(domain("health", true, List.of("health.read", "health.run_diagnostic", "health.export_support_bundle", "backup.restore.verify")));
        return List.copyOf(domains);
    }

    private String releasePosture() {
        String normalized = releasePosture == null
                ? "dogfood"
                : releasePosture.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "development", "dogfood", "release_candidate", "stable" -> normalized;
            default -> throw new IllegalStateException("Unsupported Weave release posture");
        };
    }

    private OrgManifestResponse.DomainCapability domain(
            String domain,
            boolean enabled,
            List<String> capabilities) {
        return new OrgManifestResponse.DomainCapability(
                domain,
                enabled ? "available" : "not_configured",
                enabled ? capabilities : List.of(),
                null);
    }

    private String oidcIssuerUrl() {
        String configuredIssuer = resourceServerProperties.getJwt().getIssuerUri();
        if (hasText(configuredIssuer)) {
            return safeManifestUrl(configuredIssuer, "oidc.issuer");
        }
        return safeManifestUrl(joinUrlPath(platformProperties.authBaseUrl(), "/realms/weave"), "oidc.issuer");
    }

    private String safeManifestUrl(String value, String field) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || ("organizationOrigin".equals(field) && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("Unsafe public origin");
            }
            return uri.toString();
        } catch (IllegalArgumentException | NullPointerException invalidUrl) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "organization-manifest-invalid-public-url",
                    "The organization connection configuration is unavailable.",
                    Map.of("field", field, "diagnosticsRedacted", true));
        }
    }

    private String joinUrlPath(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    private PlatformStatusResponse.DiagnosticStatus authStatus() {
        if (!workspaceProperties.shellAccess().enabled()) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "disabled",
                    "unavailable",
                    "Shell access is disabled for this backend runtime.",
                    "Enable WEAVE_WORKSPACE_SHELL_ACCESS_ENABLED when authenticated Weave product APIs should serve traffic.");
        }

        List<String> missing = new ArrayList<>();
        if (!hasText(resourceServerProperties.getJwt().getIssuerUri())) {
            missing.add("WEAVE_OIDC_ISSUER_URI");
        }
        if (!hasText(securityProperties.requiredAudience())) {
            missing.add("WEAVE_OIDC_REQUIRED_AUDIENCE");
        }
        if (!hasText(securityProperties.clientId())) {
            missing.add("WEAVE_CLIENT_ID");
        }

        if (missing.isEmpty()) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "up",
                    "ready",
                    "JWT issuer, audience, client, and workspace-scope validation are configured.",
                    null);
        }

        return new PlatformStatusResponse.DiagnosticStatus(
                "blocked",
                "blocked",
                "Missing auth runtime inputs: " + String.join(", ", missing) + ".",
                "Provide the missing auth runtime inputs for the backend: " + String.join(", ", missing) + ".");
    }

    private PlatformStatusResponse.DiagnosticCheck check(
            String key,
            String label,
            PlatformStatusResponse.DiagnosticStatus status) {
        return new PlatformStatusResponse.DiagnosticCheck(
                key,
                label,
                status.status(),
                status.readiness(),
                status.message(),
                status.action());
    }

    private List<String> actions(List<PlatformStatusResponse.DiagnosticCheck> checks) {
        return checks.stream()
                .map(PlatformStatusResponse.DiagnosticCheck::action)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
