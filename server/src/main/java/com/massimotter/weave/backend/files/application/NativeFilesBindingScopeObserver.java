package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap.ReconciledBindingObserver;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import java.time.Instant;
import java.util.Objects;

/** Provisions the default native Files stream only from the explicit binding bootstrap lifecycle. */
public final class NativeFilesBindingScopeObserver implements ReconciledBindingObserver {

    private final NativeFilesScopeProvisioner provisioner;

    public NativeFilesBindingScopeObserver(NativeFilesScopeProvisioner provisioner) {
        this.provisioner = Objects.requireNonNull(provisioner, "provisioner must not be null");
    }

    @Override
    public void reconciled(ProviderBinding binding, Instant reconciledAt) {
        ProviderBinding required = Objects.requireNonNull(binding, "binding must not be null");
        if (!"weave-native".equals(required.adapterKey())) {
            return;
        }
        provisioner.provisionScope(
                new FilesScope(
                        required.organizationRef(),
                        NativeFilesScopeProvisioner.DEFAULT_SPACE_REF),
                Objects.requireNonNull(reconciledAt, "reconciledAt must not be null"));
    }
}
