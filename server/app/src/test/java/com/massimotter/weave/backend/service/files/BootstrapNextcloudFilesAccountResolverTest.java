package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.config.FilesProviderAccountsProperties;
import com.massimotter.weave.backend.config.FilesProviderAccountsProperties.NextcloudAccount;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapNextcloudFilesAccountResolverTest {

    @TempDir
    Path secrets;

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

    @Test
    void provisionedAccountsResolveOnlyWithinTheirOwnOrganizationAndReference() throws Exception {
        Path alpha = secret("alpha", "alpha-token");
        secret("beta", "beta-token");
        var resolver = provisioned(List.of(
                configured("org-a", "profile:files", "alpha"),
                configured("org-b", "profile:files", "beta")));

        assertThat(resolver.resolve("org-a", "profile:files")).get()
                .extracting(NextcloudFilesProperties::actorToken).isEqualTo("alpha-token");
        assertThat(resolver.resolve("org-b", "profile:files")).get()
                .extracting(NextcloudFilesProperties::actorToken).isEqualTo("beta-token");
        assertThat(resolver.resolve("org-c", "profile:files")).isEmpty();
        assertThat(resolver.resolve("org-a", "profile:other")).isEmpty();

        Files.delete(alpha);
        assertThat(resolver.resolve("org-a", "profile:files")).isEmpty();
        assertThat(resolver.resolve("org-b", "profile:files")).isPresent();
    }

    @Test
    void invalidOrInaccessibleSecretsFailClosed() throws Exception {
        Path alpha = secret("alpha", "alpha-token");
        var resolver = provisioned(List.of(configured("org-a", "profile:files", "alpha")));
        Files.setPosixFilePermissions(alpha, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ));
        assertThat(resolver.resolve("org-a", "profile:files")).isEmpty();
        Files.delete(alpha);
        Files.createSymbolicLink(alpha, secrets.resolve("absent"));
        assertThat(resolver.resolve("org-a", "profile:files")).isEmpty();
    }

    @Test
    void duplicateOrTraversingConfigurationsAreRejectedAtStartup() {
        NextcloudAccount configured = configured("org-a", "profile:files", "alpha");
        assertThatThrownBy(() -> provisioned(List.of(configured, configured)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provisioned(List.of(configured("org-a", "profile:files", "../escape"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provisionedAccountCannotShadowAnActiveBootstrapBinding() {
        assertThatThrownBy(() -> new BootstrapNextcloudFilesAccountResolver(
                new ProviderBindingBootstrapProperties(
                        true, "org-a", "nextcloud-webdav", "profile:files"),
                account,
                new FilesProviderAccountsProperties(secrets.toString(), List.of(
                        configured("org-a", "profile:files", "alpha")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BootstrapNextcloudFilesAccountResolver provisioned(List<NextcloudAccount> accounts) {
        return new BootstrapNextcloudFilesAccountResolver(
                new ProviderBindingBootstrapProperties(false, "org-legacy", "nextcloud-webdav", "legacy"),
                account,
                new FilesProviderAccountsProperties(secrets.toString(), accounts));
    }

    private NextcloudAccount configured(String organizationRef, String configurationRef, String secretName) {
        return new NextcloudAccount(
                organizationRef, configurationRef, "https://files.example.test",
                "/remote.php/dav/files", "files-actor",
                "secretref://weave/files/" + secretName);
    }

    private Path secret(String name, String value) throws Exception {
        Path path = secrets.resolve(name);
        Files.writeString(path, value + "\n");
        Files.setPosixFilePermissions(path, Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        return path;
    }
}
