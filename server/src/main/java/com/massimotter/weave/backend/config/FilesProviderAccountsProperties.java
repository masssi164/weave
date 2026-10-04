package com.massimotter.weave.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Private, operator-provisioned Files accounts; bindings contain only configuration references. */
@ConfigurationProperties(prefix = "weave.files.provider-accounts")
public record FilesProviderAccountsProperties(String secretRoot, List<NextcloudAccount> nextcloud) {

    public FilesProviderAccountsProperties {
        nextcloud = nextcloud == null ? List.of() : List.copyOf(nextcloud);
    }

    public record NextcloudAccount(
            String organizationRef,
            String configurationRef,
            String baseUrl,
            String webdavRootPath,
            String actorUsername,
            String credentialRef) {
    }
}
