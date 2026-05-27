package com.massimotter.weave.backend.identity.realm;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmDryRunProviderTest {

    private final KeycloakRealmDryRunProvider provider = new KeycloakRealmDryRunProvider();

    @Test
    void plansRealmImportWithoutEnablingDestructiveApply() {
        IdentityRealmDesiredState desiredState = new IdentityRealmDesiredState(
                "weave-dogfood",
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://weave.local/*"),
                        List.of("owner", "admin", "member", "guest"),
                        List.of("openid", "profile", "email"))),
                List.of("owner", "admin", "member", "guest"),
                List.of("openid", "profile", "email", "weave:workspace"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://weave.local/*"),
                List.of(),
                List.of());

        IdentityRealmDryRunReport report = provider.dryRun(desiredState);

        assertThat(report.providerKey()).isEqualTo("keycloak-realm");
        assertThat(report.operation()).isEqualTo("dry-run");
        assertThat(report.readiness()).isEqualTo("ready-for-admin-review");
        assertThat(report.destructiveApplyAvailable()).isFalse();
        assertThat(report.supportSafe()).isTrue();
        assertThat(report.rawSecretExposed()).isFalse();
        assertThat(report.diff()).contains("plan realm weave-dogfood", "plan clients=1", "plan roles=4");
        assertThat(report.blockers()).isEmpty();
    }

    @Test
    void redactsSecretLikeValuesFromDryRunEvidence() {
        IdentityRealmDesiredState desiredState = new IdentityRealmDesiredState(
                "client_secret=please-do-not-print",
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://x-access-token:abc123@github.com/masssi164/weave.git"),
                        List.of("member"),
                        List.of("openid"))),
                List.of("member"),
                List.of("openid"),
                List.of(),
                List.of(),
                List.of("token leaked by downstream provider"),
                List.of());

        IdentityRealmDryRunReport report = provider.dryRun(desiredState);

        assertThat(report.rawSecretExposed()).isFalse();
        assertThat(String.join("\n", report.diff())).doesNotContain("please-do-not-print", "abc123", "x-access-token");
        assertThat(report.realmId()).isEqualTo("redacted-secret-like-value");
        assertThat(report.warnings()).contains("redacted-secret-like-value");
        assertThat(report.destructiveApplyAvailable()).isFalse();
    }

    @Test
    void blocksIncompleteRealmPlans() {
        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDesiredState(
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        assertThat(report.readiness()).isEqualTo("blocked-until-realm-contract-is-complete");
        assertThat(report.blockers())
                .contains("realm id is required before import planning", "at least one OIDC client must be declared");
        assertThat(report.destructiveApplyAvailable()).isFalse();
    }
}
