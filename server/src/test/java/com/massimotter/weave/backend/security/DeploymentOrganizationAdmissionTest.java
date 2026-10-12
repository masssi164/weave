package com.massimotter.weave.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class DeploymentOrganizationAdmissionTest {
    private final DeploymentOrganizationAdmission admission = HumanJwtTestSupport.organizationAdmission();

    @Test
    void bindsNativeCoordinateToTheCanonicalDeploymentTenant() {
        assertThat(admission.allows(token(Map.of("organization", current())))).isTrue();
        assertThat(admission.allows(token(Map.of("organization", current(),
                "weave_tenant_id", "tenant-default", "tenant_id", "tenant-default")))).isTrue();
        assertThat(admission.canonicalOrganizationId()).isEqualTo("tenant-default");
    }

    @Test
    void rejectsWrongMissingMultipleAndMalformedNativeOrganizations() {
        for (Object organization : List.of(
                Map.of("wrong-alias", Map.of("id", HumanJwtTestSupport.ORGANIZATION_ID)),
                Map.of(HumanJwtTestSupport.ORGANIZATION_ALIAS, Map.of("id", "wrong-id")),
                Map.of(HumanJwtTestSupport.ORGANIZATION_ALIAS, Map.of("groups", List.of("/admins"))),
                Map.of("first", Map.of("id", "one"), "second", Map.of("id", "two")),
                Map.of(), List.of("malformed"))) {
            assertThat(admission.allows(token(Map.of("organization", organization)))).isFalse();
            assertThat(admission.allowsReconciliation(token(Map.of("organization", organization)))).isFalse();
        }
        assertThat(admission.allows(token(Map.of()))).isFalse();
    }

    @Test
    void rejectsConflictingAndMalformedTenantClaimsEvenWhenNativeOrganizationMatches() {
        for (String claim : List.of("weave_tenant_id", "tenant_id")) {
            for (Object value : List.of("other", "", " tenant-default ", List.of("tenant-default"))) {
                Jwt token = token(Map.of("organization", current(), claim, value));
                assertThat(admission.allows(token)).isFalse();
                assertThat(admission.allowsReconciliation(token)).isFalse();
            }
        }
    }

    @Test
    void bootstrapMayLackOrganizationButNeverOverrideTheTenantOrMissingConfiguration() {
        assertThat(admission.allowsReconciliation(token(Map.of()))).isTrue();
        assertThat(admission.allowsReconciliation(token(Map.of("tenant_id", "other")))).isFalse();
        var unconfigured = new DeploymentOrganizationAdmission(
                new DeploymentOrganizationProperties(null, null),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null));
        assertThat(unconfigured.allows(token(Map.of("organization", current())))).isFalse();
        assertThat(unconfigured.allowsReconciliation(token(Map.of()))).isFalse();
    }

    private Map<String, Object> current() {
        return HumanJwtTestSupport.organizationWithRole("admin");
    }

    private Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("test").header("alg", "none").subject("test-member")
                .claims(values -> values.putAll(claims)).build();
    }
}
