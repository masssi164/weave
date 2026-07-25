package com.massimotter.weave.backend.identity.realm;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRealmDryRunFixtureContractTest {

    private static final ObjectMapper OBJECT_MAPPER = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
    private final KeycloakRealmDryRunProvider provider = new KeycloakRealmDryRunProvider();

    @Test
    void fixtureMatrixCoversNoOpCreateUpdateRiskyDestructiveAndInvalidStates() throws Exception {
        assertFixture("no-op", "ready", List.of("no-op"), List.of());
        assertFixture("create", "ready", List.of("create"), List.of());
        assertFixture("update", "ready", List.of("update", "create"), List.of());
        assertFixture("risky", "degraded", List.of("create"), List.of("risky"));
        assertFixture("destructive", "policy-blocked", List.of("delete"), List.of("destructive"));
        assertFixture("invalid", "admin-action-required", List.of("create"), List.of("risky"));
    }

    @Test
    void fixtureReportsAreDeterministicAndSupportSafe() throws Exception {
        IdentityRealmDryRunRequest request = fixture("create");
        IdentityRealmDryRunReport first = provider.dryRun(request);
        IdentityRealmDryRunReport second = provider.dryRun(request);

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(first.supportSafe()).isTrue();
        assertThat(first.rawSecretExposed()).isFalse();
        assertThat(OBJECT_MAPPER.writeValueAsString(first))
                .doesNotContain("client_secret", "x-access-token", "Authorization", "Bearer ", "private_key", "credentialUrl");
    }

    private void assertFixture(
            String name,
            String readiness,
            List<String> expectedActions,
            List<String> expectedClassifications) throws IOException {
        IdentityRealmDryRunReport report = provider.dryRun(fixture(name));

        assertThat(report.readiness()).isEqualTo(readiness);
        assertThat(report.destructiveApplyAvailable()).isFalse();
        assertThat(report.supportSafe()).isTrue();
        assertThat(report.rawSecretExposed()).isFalse();
        assertThat(report.changes()).extracting(IdentityRealmDryRunReport.ChangeRecord::action).containsAll(expectedActions);
        if (!expectedClassifications.isEmpty()) {
            assertThat(report.changes()).extracting(IdentityRealmDryRunReport.ChangeRecord::classification).containsAll(expectedClassifications);
        }
        assertThat(report.readinessChecks()).extracting(IdentityRealmDryRunReport.ReadinessCheck::key)
                .contains("realm-contract", "fail-closed-policy", "apply-safety");
    }

    private IdentityRealmDryRunRequest fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/identity-realm-dry-run/" + name + ".json")) {
            assertThat(input).as("fixture %s", name).isNotNull();
            return OBJECT_MAPPER.readValue(input, IdentityRealmDryRunRequest.class);
        }
    }
}
