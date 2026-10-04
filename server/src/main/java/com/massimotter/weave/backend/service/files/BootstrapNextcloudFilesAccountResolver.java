package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Restricts the legacy single Nextcloud credential to its explicit bootstrap binding only. */
@Component
public final class BootstrapNextcloudFilesAccountResolver implements NextcloudFilesAccountResolver {

    private final ProviderBindingBootstrapProperties bootstrap;
    private final NextcloudFilesProperties account;

    public BootstrapNextcloudFilesAccountResolver(
            ProviderBindingBootstrapProperties bootstrap,
            NextcloudFilesProperties account) {
        this.bootstrap = bootstrap;
        this.account = account;
    }

    @Override
    public Optional<NextcloudFilesProperties> resolve(String organizationRef, String configurationRef) {
        if (!available()
                || !bootstrap.organizationRef().trim().equals(organizationRef)
                || !bootstrap.configurationRef().trim().equals(configurationRef)) {
            return Optional.empty();
        }
        return Optional.of(account);
    }

    @Override
    public boolean available() {
        return bootstrap.enabled()
                && "nextcloud-webdav".equals(bootstrap.adapterKey())
                && bootstrap.organizationRef() != null
                && !bootstrap.organizationRef().isBlank()
                && bootstrap.configurationRef() != null
                && !bootstrap.configurationRef().isBlank()
                && account.isConfigured();
    }
}
