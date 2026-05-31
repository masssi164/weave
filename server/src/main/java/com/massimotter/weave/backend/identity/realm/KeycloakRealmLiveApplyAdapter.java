package com.massimotter.weave.backend.identity.realm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KeycloakRealmLiveApplyAdapter implements IdentityRealmLiveApplyAdapter {

    private static final String PROVIDER_KEY = "keycloak-realm";

    private final IdentityRealmApplyProperties properties;
    private final KeycloakRealmAdminClient keycloakRealmAdminClient;

    @Autowired
    public KeycloakRealmLiveApplyAdapter(IdentityRealmApplyProperties properties) {
        this(properties, null);
    }

    KeycloakRealmLiveApplyAdapter(IdentityRealmApplyProperties properties, KeycloakRealmAdminClient keycloakRealmAdminClient) {
        this.properties = properties == null ? new IdentityRealmApplyProperties() : properties;
        this.keycloakRealmAdminClient = keycloakRealmAdminClient;
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
        IdentityRealmDryRunReport report = dryRunEvidence == null ? null : dryRunEvidence.report();
        boolean destructive = report != null && report.changes().stream().anyMatch(change -> "destructive".equals(change.classification()));
        if (destructive && !properties.destructiveApplyEnabled()) {
            return new IdentityRealmLiveApplyResult(
                    false,
                    false,
                    "guarded-provider-live-apply-destructive-blocked",
                    List.of("destructive Keycloak live apply is disabled by release/operator configuration"),
                    List.of("Enable destructive apply only with restore evidence, release approval, and an operator-owned rollback plan."));
        }
        KeycloakRealmAdminClient client = keycloakRealmAdminClient == null
                ? new HttpKeycloakRealmAdminClient(properties.keycloakAdminBaseUri(), properties.keycloakAdminToken())
                : keycloakRealmAdminClient;
        try {
            KeycloakRealmAdminClient.ApplySummary summary = client.applyDesiredState(request.dryRunRequest().desiredState());
            List<String> actions = new ArrayList<>();
            actions.add("Verified a minimal Keycloak desired-state slice through Admin REST: realm settings plus configured clients, roles, and groups.");
            actions.add(summary.providerMutationPerformed()
                    ? "Keycloak Admin REST returned success for at least one create/update operation; support-safe audit evidence records only counts and mode."
                    : "Keycloak Admin REST proved the requested minimal slice was already present; no provider mutation was performed.");
            actions.add("No provider IDs, endpoint URLs, bearer credentials, auth headers, or raw Keycloak responses are returned.");
            return new IdentityRealmLiveApplyResult(
                    true,
                    summary.providerMutationPerformed(),
                    summary.providerMutationPerformed() ? "guarded-keycloak-live-apply" : "guarded-keycloak-live-apply-noop",
                    List.of(),
                    actions);
        } catch (KeycloakRealmAdminClientException exception) {
            return new IdentityRealmLiveApplyResult(
                    false,
                    false,
                    "guarded-provider-live-apply-unavailable",
                    List.of("Keycloak Admin REST apply failed before a support-safe mutation receipt could be proven"),
                    List.of("Verify operator-owned Keycloak Admin REST SecretRefs, network reachability, and a fresh dry-run before retrying."));
        }
    }
}
