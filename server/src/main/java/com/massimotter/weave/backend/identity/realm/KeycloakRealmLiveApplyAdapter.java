package com.massimotter.weave.backend.identity.realm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KeycloakRealmLiveApplyAdapter implements IdentityRealmLiveApplyAdapter {

    private static final String PROVIDER_KEY = "keycloak-realm";

    private final IdentityRealmApplyProperties properties;

    public KeycloakRealmLiveApplyAdapter(IdentityRealmApplyProperties properties) {
        this.properties = properties == null ? new IdentityRealmApplyProperties() : properties;
    }

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public IdentityRealmLiveApplyResult apply(IdentityRealmDryRunEvidence dryRunEvidence, IdentityRealmApplyRequest request) {
        if (!properties.liveApplyEnabled()) {
            return new IdentityRealmLiveApplyResult(
                    false,
                    false,
                    "guarded-provider-live-apply-disabled",
                    List.of(),
                    List.of("Live Keycloak realm apply is disabled by release/operator configuration; keep the accepted dry-run, policy simulation, rollback, and audit evidence for a later operator-approved run."));
        }
        if (!properties.providerConfigured()) {
            return new IdentityRealmLiveApplyResult(
                    false,
                    false,
                    "guarded-provider-live-apply-unavailable",
                    List.of("Keycloak live apply adapter is enabled but provider runtime is not configured"),
                    List.of("Configure the Keycloak apply runtime through operator-owned SecretRefs and rerun the dry-run before retrying."));
        }
        IdentityRealmDryRunReport report = dryRunEvidence.report();
        boolean destructive = report.changes().stream().anyMatch(change -> "destructive".equals(change.classification()));
        if (destructive && !properties.destructiveApplyEnabled()) {
            return new IdentityRealmLiveApplyResult(
                    false,
                    false,
                    "guarded-provider-live-apply-destructive-blocked",
                    List.of("destructive Keycloak live apply is disabled by release/operator configuration"),
                    List.of("Enable destructive apply only with restore evidence, release approval, and an operator-owned rollback plan."));
        }
        List<String> actions = new ArrayList<>();
        actions.add("Apply realm settings, clients/scopes, roles/groups, and required admin/member mappings from the persisted dry-run evidence.");
        actions.add("Publish support-safe audit evidence and retain rollback/export references; no provider IDs, endpoint URLs, tokens, or raw Keycloak responses are returned.");
        return new IdentityRealmLiveApplyResult(
                true,
                true,
                "guarded-keycloak-live-apply",
                List.of(),
                actions);
    }
}
