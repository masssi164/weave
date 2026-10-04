package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapNextcloudFilesAccountResolverTest {

    private final NextcloudFilesProperties account = new NextcloudFilesProperties(
            "https://files.example.test", "/remote.php/dav/files",
            "backend-service-account", "org-a-service", "secret-value");

    @Test
    void legacyCredentialsBelongOnlyToTheirExplicitOrganizationAndProfile() {
        var resolver = new BootstrapNextcloudFilesAccountResolver(
                new ProviderBindingBootstrapProperties(
                        true, "org-a", "nextcloud-webdav", "profile:nextcloud-a"), account);

        assertThat(resolver.available()).isTrue();
        assertThat(resolver.resolve("org-a", "profile:nextcloud-a")).contains(account);
        assertThat(resolver.resolve("org-b", "profile:nextcloud-a")).isEmpty();
        assertThat(resolver.resolve("org-a", "profile:nextcloud-b")).isEmpty();
    }

    @Test
    void credentialsDoNotRouteWithoutExplicitBootstrapBinding() {
        var resolver = new BootstrapNextcloudFilesAccountResolver(
                new ProviderBindingBootstrapProperties(
                        false, "org-a", "nextcloud-webdav", "profile:nextcloud-a"), account);

        assertThat(resolver.available()).isFalse();
        assertThat(resolver.resolve("org-a", "profile:nextcloud-a")).isEmpty();
    }
}
