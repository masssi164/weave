package com.massimotter.weave.backend.identity.realm;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmDryRunProviderTest {

    private final KeycloakRealmDryRunProvider provider = new KeycloakRealmDryRunProvider();

    @Test
    void plansRealmImportWithoutEnablingDestructiveApply() {
        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(null, desiredState(), "operator review"));

        assertThat(report.providerKey()).isEqualTo("keycloak-realm");
        assertThat(report.operation()).isEqualTo("dry-run");
        assertThat(report.readiness()).isEqualTo("degraded");
        assertThat(report.destructiveApplyAvailable()).isFalse();
        assertThat(report.supportSafe()).isTrue();
        assertThat(report.rawSecretExposed()).isFalse();
        assertThat(report.diff()).contains("plan realm=weave-dogfood", "plan clients=1", "plan groups=1", "plan featureMappings=2");
        assertThat(report.changes()).extracting(IdentityRealmDryRunReport.ChangeRecord::classification)
                .contains("safe", "risky");
        assertThat(report.blockers()).isEmpty();
    }

    @Test
    void reportsNoOpWhenCurrentStateAlreadyMatchesDesiredState() {
        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(desiredState(), desiredState(), "no-op contract"));

        assertThat(report.readiness()).isEqualTo("degraded");
        assertThat(report.changes()).allSatisfy(change -> assertThat(change.action()).isIn("no-op", "update"));
        assertThat(report.changes()).noneMatch(IdentityRealmDryRunReport.ChangeRecord::applyBlocked);
        assertThat(report.changes()).extracting(IdentityRealmDryRunReport.ChangeRecord::classification).contains("risky");
    }

    @Test
    void detectsUpdatesAndDestructiveRemovalsDeterministically() {
        IdentityRealmDesiredState current = new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient("weave-app", true, List.of("https://weave.test/callback", "https://old.example/callback"), List.of("admin"), List.of("openid")),
                        new IdentityRealmDesiredState.RealmClient("legacy-app", true, List.of("https://legacy.example/callback"), List.of("member"), List.of("openid"))),
                List.of("admin", "member", "guest"),
                List.of("/weave-board-editors"),
                List.of("openid", "profile", "email", "offline_access"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://old.example/callback"),
                List.of(new IdentityRealmDesiredState.FeatureMapping("boards", List.of("member"), List.of("/weave-board-editors"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("weaver-runtime"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("subject:owner:current", "last-admin recovery", true, List.of("owner"))),
                List.of("subject:owner:current"),
                "sub",
                List.of(),
                List.of());
        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(current, desiredState(), "compare"));

        assertThat(report.readiness()).isEqualTo("policy-blocked");
        assertThat(report.changes()).extracting(IdentityRealmDryRunReport.ChangeRecord::action).contains("update", "delete", "create");
        assertThat(report.changes()).filteredOn(change -> change.classification().equals("destructive"))
                .extracting(IdentityRealmDryRunReport.ChangeRecord::path)
                .contains("/clients/legacy-app", "/scopes/offline_access");
        assertThat(report.blockers()).contains("destructive realm changes are blocked in this dry-run-only slice");
        assertThat(provider.dryRun(new IdentityRealmDryRunRequest(current, desiredState(), "compare")).diff()).isEqualTo(report.diff());
    }

    @Test
    void unknownRolesGroupsScopesAndFeaturesFailClosed() {
        IdentityRealmDesiredState desiredState = new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient("weave-app", true, List.of("https://weave.test/callback"), List.of("super-admin"), List.of("openid"))),
                List.of("owner", "super-admin"),
                List.of("unknown-group"),
                List.of("openid", "unknown-scope"),
                List.of(),
                List.of(),
                List.of(new IdentityRealmDesiredState.FeatureMapping("unknown-feature", List.of("super-admin"), List.of("unknown-group"), List.of("unknown-scope"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("weaver-runtime"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("subject:owner:current", "last-admin recovery", true, List.of("owner"))),
                List.of("subject:owner:current"),
                "sub",
                List.of(),
                List.of());

        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(null, desiredState, "fail closed"));

        assertThat(report.readiness()).isEqualTo("admin-action-required");
        assertThat(report.blockers()).contains(
                "unknown roles deny by default until mapped: super-admin",
                "unknown groups deny by default until mapped: unknown-group",
                "unknown scopes deny by default until mapped: unknown-scope",
                "unknown feature mapping deny by default until mapped: unknown-feature");
        assertThat(report.changes()).filteredOn(IdentityRealmDryRunReport.ChangeRecord::applyBlocked)
                .extracting(IdentityRealmDryRunReport.ChangeRecord::reasonCode)
                .contains("unknown-roles-deny-by-default", "unknown-groups-deny-by-default", "unknown-scopes-deny-by-default", "unknown-feature-mapping-deny-by-default");
    }

    @Test
    void redactsSecretLikeValuesFromDryRunEvidence() {
        IdentityRealmDesiredState desiredState = new IdentityRealmDesiredState(
                "client_secret=please-do-not-print",
                "token leaked by downstream provider",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://x-access-token:abc123@github.com/masssi164/weave.git"),
                        List.of("member"),
                        List.of("openid"))),
                List.of("member"),
                List.of(),
                List.of("openid"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("weaver-runtime"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("subject:owner:current", "last-admin recovery", true, List.of("owner"))),
                List.of("subject:owner:current"),
                "sub",
                List.of("bearer abc123 appeared in provider output"),
                List.of());

        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(null, desiredState, "redaction"));

        assertThat(report.rawSecretExposed()).isFalse();
        assertThat(String.join("\n", report.diff())).doesNotContain("please-do-not-print", "abc123", "x-access-token");
        assertThat(report.realmId()).isEqualTo("redacted-secret-like-value");
        assertThat(report.warnings()).contains("redacted-secret-like-value");
        assertThat(report.destructiveApplyAvailable()).isFalse();
    }

    @Test
    void blocksEmailPrimaryKeyAndRepresentsRecoveryProtections() {
        IdentityRealmDesiredState unsafe = new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient("weave-app", true, List.of("https://weave.test/callback"), List.of("owner"), List.of("openid"))),
                List.of("owner"),
                List.of(),
                List.of("openid"),
                List.of(),
                List.of(),
                List.of(),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("weaver-runtime"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("owner@example.invalid", "break-glass", true, List.of("owner"))),
                List.of("owner@example.invalid"),
                "email",
                List.of(),
                List.of());

        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(null, unsafe, "identity safety"));

        assertThat(report.readiness()).isEqualTo("admin-action-required");
        assertThat(report.blockers()).contains(
                "primary identity key must be immutable subject claim, not email or username",
                "last-admin protection must not use email as primary subject: owner@example.invalid",
                "break-glass identity must use immutable subject reference, not email: owner@example.invalid");
        assertThat(report.diff()).contains("plan serviceAccounts=1", "plan breakGlassIdentities=1", "plan lastAdminSubjectRefs=1", "plan primarySubjectClaim=email");
        assertThat(report.destructiveApplyAvailable()).isFalse();
    }

    @Test
    void blocksIncompleteRealmPlans() {
        IdentityRealmDryRunReport report = provider.dryRun(new IdentityRealmDryRunRequest(null, new IdentityRealmDesiredState(
                "",
                null,
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "sub",
                List.of(),
                List.of()), "invalid"));

        assertThat(report.readiness()).isEqualTo("admin-action-required");
        assertThat(report.blockers())
                .contains("realm id is required before import planning", "at least one OIDC client must be declared");
        assertThat(report.destructiveApplyAvailable()).isFalse();
    }

    private IdentityRealmDesiredState desiredState() {
        return new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://weave.test/callback", "https://weave.test/*"),
                        List.of("owner", "admin", "member", "guest"),
                        List.of("openid", "profile", "email"))),
                List.of("owner", "admin", "member", "guest"),
                List.of("/weave-board-editors"),
                List.of("openid", "profile", "email", "weave:workspace", "agent-runtime.admin"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://weave.test/callback"),
                List.of(
                        new IdentityRealmDesiredState.FeatureMapping("boards", List.of("member"), List.of("/weave-board-editors"), List.of("openid")),
                        new IdentityRealmDesiredState.FeatureMapping("agent-runtime-control", List.of("admin"), List.of(), List.of("agent-runtime.admin"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("weaver-runtime"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("subject:owner:current", "last-admin recovery", true, List.of("owner"))),
                List.of("subject:owner:current"),
                "sub",
                List.of(),
                List.of());
    }
}
