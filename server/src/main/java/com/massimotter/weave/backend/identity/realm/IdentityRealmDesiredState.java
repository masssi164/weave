package com.massimotter.weave.backend.identity.realm;

import java.util.List;

/**
 * Product-level identity realm intent owned by the Weave admin control plane.
 *
 * <p>This is deliberately provider-shaped enough for Keycloak/OIDC realm planning,
 * but it is not Terraform state and it must not contain raw secrets.</p>
 */
public record IdentityRealmDesiredState(
        String realmId,
        List<RealmClient> clients,
        List<String> roles,
        List<String> scopes,
        List<ClaimMapper> claimMappers,
        List<String> redirectOrigins,
        List<String> providerWarnings,
        List<String> blockers) {

    public IdentityRealmDesiredState {
        clients = clients == null ? List.of() : List.copyOf(clients);
        roles = roles == null ? List.of() : List.copyOf(roles);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        claimMappers = claimMappers == null ? List.of() : List.copyOf(claimMappers);
        redirectOrigins = redirectOrigins == null ? List.of() : List.copyOf(redirectOrigins);
        providerWarnings = providerWarnings == null ? List.of() : List.copyOf(providerWarnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public record RealmClient(
            String clientId,
            boolean publicClient,
            List<String> redirectOrigins,
            List<String> roles,
            List<String> scopes) {
        public RealmClient {
            redirectOrigins = redirectOrigins == null ? List.of() : List.copyOf(redirectOrigins);
            roles = roles == null ? List.of() : List.copyOf(roles);
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }

    public record ClaimMapper(
            String name,
            String sourceClaim,
            String targetClaim,
            boolean required) {
    }
}
