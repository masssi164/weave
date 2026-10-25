package com.massimotter.weave.backend.security.workload.config;

import com.massimotter.weave.backend.security.workload.provider.KeycloakWorkloadEntitlementAuthority;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Existing shared IDM connection coordinates; source keys remain compatible with reviewed configuration. */
@ConfigurationProperties(prefix = "weave.agent-runtime.workload-identity")
public class WorkloadIdentityProperties {
    private URI keycloakAdminBaseUrl;
    private URI issuer;
    private String realm = "weave";
    private String organizationRef = "tenant-default";
    private String keycloakOrganizationId = "";
    private String keycloakOrganizationAlias = "";
    private Duration timeout = Duration.ofSeconds(10);

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

    public Duration timeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public KeycloakWorkloadEntitlementAuthority.Settings entitlementSettings(
            WorkloadEntitlementProperties entitlement) {
        return new KeycloakWorkloadEntitlementAuthority.Settings(
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
