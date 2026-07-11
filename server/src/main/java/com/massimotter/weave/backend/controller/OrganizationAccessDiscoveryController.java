package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationAccessDiscoveryController {
    private final PlatformContractProperties platform;
    private final IdentityInvitationProperties identity;
    private final WeaveSecurityProperties security;
    private final String issuer;

    public OrganizationAccessDiscoveryController(PlatformContractProperties platform,
            IdentityInvitationProperties identity, WeaveSecurityProperties security,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer) {
        this.platform = platform;
        this.identity = identity;
        this.security = security;
        this.issuer = issuer == null ? "" : issuer.trim();
    }

    @GetMapping("/.well-known/weave")
    public OrganizationAccessDiscovery discover() {
        return new OrganizationAccessDiscovery(
                "weave-organization-access-v1",
                new Organization(identity.keycloak().organizationAlias(), "Weave"),
                platform.apiBaseUrl(),
                issuer,
                security.clientId(),
                Map.of("authorizationCodePkce", true, "organizationScope", true));
    }

    public record OrganizationAccessDiscovery(
            String contractVersion,
            Organization organization,
            String apiBaseUrl,
            String oidcIssuer,
            String oidcClientId,
            Map<String, Boolean> capabilities) {}

    public record Organization(String id, String displayName) {}
}
