package com.massimotter.weave.backend.service;

import tools.jackson.databind.ObjectMapper;
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

    @Test
    void emailRenameKeepsStableAccountAndProfileKey() {
        ProductProfileService service = new ProductProfileService(
                new FileProductProfileOverrideRepository(new ObjectMapper(), tempDir.resolve("profile-overrides.json")));
        Jwt original = profileJwt("alice@example.com");
        Jwt renamedEmail = profileJwt("alice.renamed@example.com");

        service.update(original, new UpdateProductProfileRequest(
                "Alice Stable",
                null,
                null,
                null,
                null,
                null));

        assertThat(service.authenticatedUser(renamedEmail).userId())
                .isEqualTo(service.authenticatedUser(original).userId());
        assertThat(service.authenticatedUser(renamedEmail).primaryIdentityKey())
                .isEqualTo("issuer+subject:https://auth.weave.test/realms/weave#user-123");
        assertThat(service.authenticatedUser(renamedEmail).displayName()).isEqualTo("Alice Stable");
        assertThat(service.authenticatedUser(renamedEmail).emailPrimaryKey()).isFalse();
    }

    @Test
    void migratesLegacySubjectProfileOverrideToPrimaryIdentityKey() throws Exception {
        Path storagePath = tempDir.resolve("profile-overrides.json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), Map.of(
                "user-123", new ProductProfileOverride(
                        "Alice Legacy",
                        null,
                        "fr-FR",
                        "Europe/Paris",
                        Map.of(),
                        "workspace")));

        ProductProfileService service = new ProductProfileService(
                new FileProductProfileOverrideRepository(new ObjectMapper(), storagePath));

        assertThat(service.profile(profileJwt()).displayName()).isEqualTo("Alice Legacy");
        String persisted = java.nio.file.Files.readString(storagePath);
        assertThat(persisted).contains("issuer+subject:https://auth.weave.test/realms/weave#user-123");
        assertThat(persisted).doesNotContain("\"user-123\"");
    }

    private static Jwt profileJwt() {
        return profileJwt("alice@example.com");
    }

    private static Jwt profileJwt(String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("iss", "https://auth.weave.test/realms/weave")
                .claim("preferred_username", "alice")
                .claim("name", "Alice Example")
                .claim("email", email)
                .claim("email_verified", true)
                .claim("locale", "en")
                .claim("timezone", "UTC")
                .claim("azp", "weave-app")
                .claim("aud", List.of("weave-app", "account"))
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                .claim("groups", List.of("team-alpha"))
                .build();
    }
}
