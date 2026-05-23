package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.model.ProductProfileResponse;
import com.massimotter.weave.backend.model.UpdateProductProfileRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class ProductProfileServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void persistsProfileOverridesAcrossRepositoryRecreation() {
        Path storagePath = tempDir.resolve("profile-overrides.json");
        Jwt jwt = profileJwt();

        ProductProfileService service = new ProductProfileService(
                new FileProductProfileOverrideRepository(new ObjectMapper(), storagePath));
        service.update(jwt, new UpdateProductProfileRequest(
                "Alice Durable",
                "weave-avatar://user/alice",
                "de-DE",
                "Europe/Berlin",
                Map.of("reducedMotion", "true"),
                "private"));

        ProductProfileService recreatedService = new ProductProfileService(
                new FileProductProfileOverrideRepository(new ObjectMapper(), storagePath));
        ProductProfileResponse persistedProfile = recreatedService.profile(jwt);

        assertThat(persistedProfile.displayName()).isEqualTo("Alice Durable");
        assertThat(persistedProfile.avatar()).isEqualTo("weave-avatar://user/alice");
        assertThat(persistedProfile.locale()).isEqualTo("de-DE");
        assertThat(persistedProfile.timezone()).isEqualTo("Europe/Berlin");
        assertThat(persistedProfile.accessibilityPreferences()).containsEntry("reducedMotion", "true");
        assertThat(persistedProfile.profileVisibility()).isEqualTo("private");
        assertThat(recreatedService.authenticatedUser(jwt).displayName()).isEqualTo("Alice Durable");
    }

    private static Jwt profileJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("preferred_username", "alice")
                .claim("name", "Alice Example")
                .claim("email", "alice@example.com")
                .claim("email_verified", true)
                .claim("locale", "en")
                .claim("timezone", "UTC")
                .claim("azp", "weave-app")
                .claim("aud", List.of("weave-app", "account"))
                .claim("realm_access", Map.of("roles", List.of("member")))
                .claim("groups", List.of("team-alpha"))
                .build();
    }
}
