package com.massimotter.weave.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class NativeOrganizationClaimsTest {

    @Test
    void readsGroupsAndClientRolesFromExactlyOneSelectedOrganization() {
        Jwt jwt = token(Map.of(
                "organization",
                Map.of(
                        "weave-dogfood",
                        Map.of(
                                "groups", List.of("/members", " /members ", "/capabilities/weaver"),
                                "resource_access",
                                Map.of("weave-app", Map.of("roles", List.of("member", " member ")))))));

        assertThat(NativeOrganizationClaims.groups(jwt))
                .containsExactly("/capabilities/weaver", "/members");
        assertThat(NativeOrganizationClaims.clientRoles(jwt, "weave-app"))
                .containsExactly("member");
    }

    @Test
    void failsClosedForAmbiguousOrganizationsAndTopLevelFallbackClaims() {
        Jwt jwt = token(Map.of(
                "organization",
                Map.of(
                        "weave-dogfood", Map.of("groups", List.of("/members")),
                        "other", Map.of("groups", List.of("/owners"))),
                "groups",
                List.of("/owners"),
                "resource_access",
                Map.of("weave-app", Map.of("roles", List.of("owner")))));

        assertThat(NativeOrganizationClaims.groups(jwt)).isEmpty();
        assertThat(NativeOrganizationClaims.clientRoles(jwt, "weave-app")).isEmpty();
    }

    @Test
    void ignoresMalformedOrganizationProjection() {
        Jwt jwt = token(Map.of("organization", List.of("weave-dogfood")));

        assertThat(NativeOrganizationClaims.groups(jwt)).isEmpty();
        assertThat(NativeOrganizationClaims.clientRoles(jwt, "weave-app")).isEmpty();
    }

    private Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("person-1")
                .issuer("https://auth.weave.test/realms/weave");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
