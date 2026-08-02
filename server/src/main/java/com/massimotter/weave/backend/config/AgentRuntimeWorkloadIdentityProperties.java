package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAgentRuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakRuntimeIdentityAuthority;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.agent-runtime.workload-identity")
public class AgentRuntimeWorkloadIdentityProperties {
    private boolean enabled;
    private URI keycloakAdminBaseUrl;
    private URI issuer;
    private String realm = "weave";
    private String organizationRef = "tenant-default";
    private String keycloakOrganizationId = "";
    private String keycloakOrganizationAlias = "";
    private String adminClientId = "weave-agent-runtime-admin";
    private String adminCredentialRef = "";
    private String entitlementClientId = "weave-identity-admin";
    private String entitlementCredentialRef = "";
    private Path secretRoot;
    private Duration timeout = Duration.ofSeconds(10);
    private String workloadRole = "weaver-runtime";
    private List<String> defaultClientScopes = new ArrayList<>(List.of("weaver-runtime-workload"));
    private List<String> optionalClientScopes =
            new ArrayList<>(List.of("agent-runtime.profile.read", "mcp.tools", "files.read"));
    private int accessTokenLifespanSeconds =
            KeycloakAgentRuntimeWorkloadIdentityAdmin.WORKLOAD_ACCESS_TOKEN_LIFESPAN_SECONDS;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI keycloakAdminBaseUrl() {
        return keycloakAdminBaseUrl;
    }

    public void setKeycloakAdminBaseUrl(URI keycloakAdminBaseUrl) {
        this.keycloakAdminBaseUrl = keycloakAdminBaseUrl;
    }

    public URI issuer() {
        return issuer;
    }

    public void setIssuer(URI issuer) {
        this.issuer = issuer;
    }

    public String realm() {
        return realm;
    }

    public String organizationRef() {
        return organizationRef;
    }

    public void setOrganizationRef(String organizationRef) {
        this.organizationRef = organizationRef;
    }

    public String keycloakOrganizationId() {
        return keycloakOrganizationId;
    }

    public void setKeycloakOrganizationId(String keycloakOrganizationId) {
        this.keycloakOrganizationId = keycloakOrganizationId;
    }

    public String keycloakOrganizationAlias() {
        return keycloakOrganizationAlias;
    }

    public void setKeycloakOrganizationAlias(String keycloakOrganizationAlias) {
        this.keycloakOrganizationAlias = keycloakOrganizationAlias;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String adminClientId() {
        return adminClientId;
    }

    public void setAdminClientId(String adminClientId) {
        this.adminClientId = adminClientId;
    }

    public String adminCredentialRef() {
        return adminCredentialRef;
    }

    public void setAdminCredentialRef(String adminCredentialRef) {
        this.adminCredentialRef = adminCredentialRef;
    }

    public String entitlementClientId() {
        return entitlementClientId;
    }

    public void setEntitlementClientId(String entitlementClientId) {
        this.entitlementClientId = entitlementClientId;
    }

    public String entitlementCredentialRef() {
        return entitlementCredentialRef;
    }

    public void setEntitlementCredentialRef(String entitlementCredentialRef) {
        this.entitlementCredentialRef = entitlementCredentialRef;
    }

    public Path secretRoot() {
        return secretRoot;
    }

    public void setSecretRoot(Path secretRoot) {
        this.secretRoot = secretRoot;
    }

    public Duration timeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String workloadRole() {
        return workloadRole;
    }

    public void setWorkloadRole(String workloadRole) {
        this.workloadRole = workloadRole;
    }

    public List<String> defaultClientScopes() {
        return List.copyOf(defaultClientScopes);
    }

    public void setDefaultClientScopes(List<String> defaultClientScopes) {
        this.defaultClientScopes = defaultClientScopes == null
                ? new ArrayList<>()
                : new ArrayList<>(defaultClientScopes);
    }

    public List<String> optionalClientScopes() {
        return List.copyOf(optionalClientScopes);
    }

    public void setOptionalClientScopes(List<String> optionalClientScopes) {
        this.optionalClientScopes = optionalClientScopes == null
                ? new ArrayList<>()
                : new ArrayList<>(optionalClientScopes);
    }

    public int accessTokenLifespanSeconds() {
        return accessTokenLifespanSeconds;
    }

    public void setAccessTokenLifespanSeconds(int accessTokenLifespanSeconds) {
        this.accessTokenLifespanSeconds = accessTokenLifespanSeconds;
    }

    public Path requiredSecretRoot() {
        if (secretRoot == null) {
            throw new IllegalStateException("Agent Runtime workload identity requires an explicit SecretRef root");
        }
        return secretRoot;
    }

    public KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings workloadSettings() {
        return new KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings(
                keycloakAdminBaseUrl,
                issuer,
                realm,
                timeout,
                workloadRole,
                defaultClientScopes(),
                optionalClientScopes(),
                accessTokenLifespanSeconds);
    }

    public SpringSecurityKeycloakAdminAccessTokenProvider.Settings workloadAdminTokenSettings() {
        return new SpringSecurityKeycloakAdminAccessTokenProvider.Settings(
                keycloakAdminBaseUrl,
                realm,
                adminClientId,
                adminCredentialRef,
                SpringSecurityKeycloakAdminAccessTokenProvider.CredentialMethod.PRIVATE_KEY_JWT,
                issuer,
                timeout);
    }

    public SpringSecurityKeycloakAdminAccessTokenProvider.Settings entitlementTokenSettings() {
        return new SpringSecurityKeycloakAdminAccessTokenProvider.Settings(
                keycloakAdminBaseUrl,
                realm,
                entitlementClientId,
                entitlementCredentialRef,
                SpringSecurityKeycloakAdminAccessTokenProvider.CredentialMethod.CLIENT_SECRET_BASIC,
                issuer,
                timeout);
    }

    public KeycloakRuntimeIdentityAuthority.Settings entitlementSettings(
            AgentRuntimeEntitlementProperties entitlement) {
        return new KeycloakRuntimeIdentityAuthority.Settings(
                entitlement.enabled(),
                keycloakAdminBaseUrl,
                issuer,
                organizationRef,
                keycloakOrganizationId,
                keycloakOrganizationAlias,
                realm,
                timeout,
                entitlement.observationTtl(),
                entitlement.allowedCapabilities());
    }
}
