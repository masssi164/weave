package com.massimotter.weave.backend.agentruntime.config;

import com.massimotter.weave.backend.security.workload.config.WorkloadIdentityProperties;

import com.massimotter.weave.backend.security.keycloak.provider.SpringSecurityKeycloakAdminAccessTokenProvider;

import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;

import com.massimotter.weave.backend.agentruntime.provider.identity.KeycloakAgentRuntimeWorkloadIdentityAdmin;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.agent-runtime.workload-identity")
public class AgentRuntimeWorkloadIdentityProperties extends WorkloadIdentityProperties {
    private boolean enabled;
    private String adminClientId = "weave-agent-runtime-admin";
    private String adminCredentialRef = "";
    private Path secretRoot;
    private String workloadRole = "weaver-runtime";
    private List<String> defaultClientScopes = new ArrayList<>(List.of("weaver-runtime-workload"));
    private List<String> optionalClientScopes =
            new ArrayList<>(List.of("agent-runtime.profile.read", "mcp.tools", "files.read", "calendar.read", "calendar.write"));
    private int accessTokenLifespanSeconds =
            KeycloakAgentRuntimeWorkloadIdentityAdmin.WORKLOAD_ACCESS_TOKEN_LIFESPAN_SECONDS;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public Path secretRoot() {
        return secretRoot;
    }

    public void setSecretRoot(Path secretRoot) {
        this.secretRoot = secretRoot;
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
                keycloakAdminBaseUrl(),
                issuer(),
                realm(),
                timeout(),
                workloadRole,
                defaultClientScopes(),
                optionalClientScopes(),
                accessTokenLifespanSeconds);
    }

    public SpringSecurityKeycloakAdminAccessTokenProvider.Settings workloadAdminTokenSettings() {
        return new SpringSecurityKeycloakAdminAccessTokenProvider.Settings(
                keycloakAdminBaseUrl(),
                realm(),
                adminClientId,
                adminCredentialRef,
                issuer(),
                timeout());
    }

}
