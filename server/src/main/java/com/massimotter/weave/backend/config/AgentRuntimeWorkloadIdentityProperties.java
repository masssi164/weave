package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.ClientSecretKeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAgentRuntimeWorkloadIdentityAdmin;
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
    private String adminClientId = "weave-identity-admin";
    private String adminCredentialRef = "";
    private Path secretRoot;
    private Duration timeout = Duration.ofSeconds(10);
    private String workloadRole = "weaver-runtime";
    private List<String> optionalClientScopes = new ArrayList<>(List.of("agent-runtime.profile.read"));
    private int accessTokenLifespanSeconds = 60;

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
                optionalClientScopes(),
                accessTokenLifespanSeconds);
    }

    public ClientSecretKeycloakAdminAccessTokenProvider.Settings adminTokenSettings() {
        return new ClientSecretKeycloakAdminAccessTokenProvider.Settings(
                keycloakAdminBaseUrl,
                realm,
                adminClientId,
                adminCredentialRef,
                timeout);
    }
}
