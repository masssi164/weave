package com.massimotter.weave.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OrganizationIdentityContextResolverTest {

    private final OrganizationIdentityContextResolver resolver =
            OrganizationIdentityContextResolver.configured(
                    new ContextAuthorizationProperties(
                            "configured_primary",
                            "configured_fallback",
                            "configured-default",
                            null,
                            null,
                            null,
                            null,
                            null));

    @Test
    void usesConfiguredPrimaryThenFallbackThenDefault() {
        assertThat(
                        resolver.resolve(
                                        jwt(
                                                Map.of(
                                                        "configured_primary", "primary",
                                                        "configured_fallback", "fallback")))
                                .organizationId())
                .isEqualTo("primary");
        assertThat(
                        resolver.resolve(jwt(Map.of("configured_fallback", "fallback")))
                                .organizationId())
                .isEqualTo("fallback");
        assertThat(resolver.resolve(jwt(Map.of())).organizationId())
                .isEqualTo("configured-default");
    }

    @Test
    void ignoresHistoricalTenantAliases() {
        assertThat(
                        resolver.resolve(
                                        jwt(
                                                Map.of(
                                                        "weave_tenant", "legacy-weave",
                                                        "tenant", "legacy-tenant",
                                                        "tid", "legacy-tid",
                                                        "org_id", "legacy-org")))
                                .organizationId())
                .isEqualTo("configured-default");
    }

    @Test
    void readsOnlyTheSelectedNativeOrganizationGroupProjection() {
        OrganizationIdentityContext context = resolver.resolve(jwt(Map.of(
                "organization",
                Map.of(
                        "weave-dogfood",
                        Map.of(
                                "groups", List.of("/members", "/capabilities/weaver"),
                                "resource_access",
                                Map.of("weave-app", Map.of("roles", List.of("member"))))),
                "groups",
                List.of("/owners"),
                "resource_access",
                Map.of("weave-app", Map.of("roles", List.of("admin"))),
                "weave_groups",
                List.of("/admins"))));

        assertThat(context.groups())
                .containsExactly("/capabilities/weaver", "/members");
        assertThat(context.providerRoleMappings())
                .containsExactly(
                        "group_claim:/capabilities/weaver",
                        "group_claim:/members",
                        "role_claim:member");
    }

    @Test
    void rejectsAmbiguousOrganizationGroupProjection() {
        OrganizationIdentityContext context = resolver.resolve(jwt(Map.of(
                "organization",
                Map.of(
                        "weave-dogfood",
                        Map.of("groups", List.of("/members")),
                        "other",
                        Map.of("groups", List.of("/owners"))))));

        assertThat(context.groups()).isEmpty();
        assertThat(context.providerRoleMappings()).isEmpty();
    }

    @Test
    void ignoresTopLevelClientRolesWithoutSelectedOrganizationProjection() {
        OrganizationIdentityContext context = resolver.resolve(jwt(Map.of(
                "resource_access",
                Map.of("weave-app", Map.of("roles", List.of("owner"))))));

        assertThat(context.roles()).isEmpty();
        assertThat(context.providerRoleMappings()).isEmpty();
    }

    @Test
    void verifiedProviderMemberUsesTheSameCanonicalIdentityWithoutImpersonation() {
        var token = jwt(Map.of("organization", Map.of("weave-dogfood", Map.of(
                "groups", List.of("/members", "/capabilities/weaver"),
                "resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))));
        var observed = resolver.resolveVerifiedMember(token.getIssuer().toString(), token.getSubject(),
                List.of("member"), List.of("/members", "/capabilities/weaver", "/members"));
        assertThat(observed).isEqualTo(resolver.resolve(token));
        assertThat(observed.organizationId()).isEqualTo("configured-default");
        assertThat(observed.contextRoles()).isEmpty();
        assertThat(observed.roles()).containsExactly("member");
    }

    @Test
    void verifiedProviderContextRejectsMissingUnknownOrAmbiguousRoles() {
        for (List<String> roles : List.of(List.<String>of(), List.of("superadmin"), List.of("owner", "member"))) {
            assertThatThrownBy(() -> resolver.resolveVerifiedMember(
                    "https://auth.example.invalid/realms/weave", "person-1", roles, List.of("/members")))
                    .isInstanceOf(ApiErrorException.class);
        }
        assertThatThrownBy(() -> resolver.resolveVerifiedMember(
                "https://auth.example.invalid/realms/weave", "person-1", null, List.of()))
                .isInstanceOf(ApiErrorException.class);
    }

    @Test
    void verifiedProviderContextDoesNotSynthesizeMissingIdentityOrGroups() {
        for (String subject : List.of("", "subject with spaces", "subject#other", "subject" + (char) 0)) {
            assertThatThrownBy(() -> resolver.resolveVerifiedMember(
                    "https://auth.example.invalid/realms/weave", subject, List.of("member"), List.of("/members")))
                    .isInstanceOf(ApiErrorException.class);
        }
        assertThatThrownBy(() -> resolver.resolveVerifiedMember(
                "", "person-1", List.of("member"), List.of()))
                .isInstanceOf(ApiErrorException.class);
        assertThatThrownBy(() -> resolver.resolveVerifiedMember(
                "https://auth.example.invalid/realms/weave", "person-1", List.of("member"), null))
                .isInstanceOf(ApiErrorException.class);
        assertThatThrownBy(() -> resolver.resolveVerifiedMember(
                "https://auth.example.invalid/realms/weave", "person-1", List.of("member"), List.of("/members\n")))
                .isInstanceOf(ApiErrorException.class);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .issuer("https://auth.example.invalid/realms/weave")
                        .subject("person-1")
                        .claim("resource_access", Map.of());
        claims.forEach(builder::claim);
        return builder.build();
    }
}
