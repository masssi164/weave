package com.massimotter.weave.backend.identity.realm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Product-level identity realm intent owned by the Weave admin control plane.
 *
 * <p>This is deliberately provider-shaped enough for Keycloak/OIDC realm planning,
 * but it is not Terraform state and it must not contain raw secrets.</p>
 */
@Schema(description = "Support-safe identity realm desired/current state for admin dry-run planning.")
public record IdentityRealmDesiredState(
        String realmId,
        String displayName,
        Boolean enabled,
        List<RealmClient> clients,
        List<String> roles,
        List<String> groups,
        List<String> scopes,
        List<ClaimMapper> claimMappers,
        List<String> redirectOrigins,
        List<FeatureMapping> featureMappings,
        List<ServiceAccount> serviceAccounts,
        List<RecoveryIdentity> breakGlassIdentities,
        List<String> lastAdminSubjectRefs,
        String primarySubjectClaim,
        List<String> providerWarnings,
        List<String> blockers) {

    public IdentityRealmDesiredState {
        clients = clients == null ? List.of() : List.copyOf(clients);
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        claimMappers = claimMappers == null ? List.of() : List.copyOf(claimMappers);
        redirectOrigins = redirectOrigins == null ? List.of() : List.copyOf(redirectOrigins);
        featureMappings = featureMappings == null ? List.of() : List.copyOf(featureMappings);
        serviceAccounts = serviceAccounts == null ? List.of() : List.copyOf(serviceAccounts);
        breakGlassIdentities = breakGlassIdentities == null ? List.of() : List.copyOf(breakGlassIdentities);
        lastAdminSubjectRefs = lastAdminSubjectRefs == null ? List.of() : List.copyOf(lastAdminSubjectRefs);
        primarySubjectClaim = primarySubjectClaim == null || primarySubjectClaim.isBlank() ? "sub" : primarySubjectClaim;
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

    public record ServiceAccount(
            String subjectRef,
            List<String> roles,
            List<String> scopes) {
        public ServiceAccount {
            roles = roles == null ? List.of() : List.copyOf(roles);
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }

    public record RecoveryIdentity(
            String subjectRef,
            String purpose,
            boolean breakGlass,
            List<String> roles) {
        public RecoveryIdentity {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record FeatureMapping(
            String featureKey,
            List<String> requiredRoles,
            List<String> requiredGroups,
            List<String> requiredScopes) {
        public FeatureMapping {
            requiredRoles = requiredRoles == null ? List.of() : List.copyOf(requiredRoles);
            requiredGroups = requiredGroups == null ? List.of() : List.copyOf(requiredGroups);
            requiredScopes = requiredScopes == null ? List.of() : List.copyOf(requiredScopes);
        }
    }
}
