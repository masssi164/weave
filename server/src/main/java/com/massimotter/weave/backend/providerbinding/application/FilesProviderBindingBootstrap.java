package com.massimotter.weave.backend.providerbinding.application;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Reconciles one explicitly configured dogfood Files binding without changing an existing authority decision. */
public final class FilesProviderBindingBootstrap implements ApplicationRunner {

    private final ProviderBindingRepository repository;
    private final ProviderBindingBootstrapProperties properties;
    private final Clock clock;

    public FilesProviderBindingBootstrap(
            ProviderBindingRepository repository,
            ProviderBindingBootstrapProperties properties,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcile();
    }

    public ProviderBinding reconcile() {
        String organizationRef = properties.requiredOrganizationRef();
        String adapterKey = properties.requiredAdapterKey();
        String configurationRef = properties.requiredConfigurationRef();
        return repository.current(organizationRef, "files")
                .map(current -> requireEquivalent(current, adapterKey, configurationRef))
                .orElseGet(() -> repository.activate(
                        organizationRef, "files", 0, adapterKey, configurationRef, Instant.now(clock)));
    }

    private ProviderBinding requireEquivalent(
            ProviderBinding current, String adapterKey, String configurationRef) {
        if (!current.adapterKey().equals(adapterKey) || !current.configurationRef().equals(configurationRef)) {
            throw new ProviderBindingBootstrapConflictException(current);
        }
        return current;
    }

    public static final class ProviderBindingBootstrapConflictException extends RuntimeException {
        public ProviderBindingBootstrapConflictException(ProviderBinding current) {
            super("Files provider binding bootstrap conflicts with active revision " + current.revision());
        }
    }
}
