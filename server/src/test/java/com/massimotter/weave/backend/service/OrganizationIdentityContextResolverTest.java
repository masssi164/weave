package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
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
