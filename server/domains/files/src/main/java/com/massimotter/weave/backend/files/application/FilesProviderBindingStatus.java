package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBindingStatus;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBindingStatus.BindingState;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBindingStatus.Readiness;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingStatusContribution;

/** Files owns binding admissibility and captured adapter readiness; never exposes configuration handles. */
public final class FilesProviderBindingStatus implements ProviderBindingStatusContribution {
    private final ProviderBindingRepository bindings;
    private final FilesProviderResolver files;

    public FilesProviderBindingStatus(ProviderBindingRepository bindings, FilesProviderResolver files) {
        this.bindings = bindings;
        this.files = files;
    }

    @Override
    public String domain() { return "files"; }

    @Override
    public ProviderBindingStatus status(String organization) {
        var binding = bindings.current(organization, domain())
                .filter(value -> value.state() == ProviderBinding.State.ACTIVE
                        && organization.equals(value.organizationRef()) && domain().equals(value.domain()));
        if (binding.isEmpty()) {
            return new ProviderBindingStatus(BindingState.NO_ACTIVE_BINDING, null, null, Readiness.NOT_CONFIGURED);
        }
        ProviderBinding current = binding.orElseThrow();
        Readiness readiness;
        try {
            files.pinned(current, organization, FilesUserApiService.SPACE_REF);
            readiness = Readiness.CONFIGURED;
        } catch (FilesProviderResolver.ProviderUnavailableException
                | FilesProviderResolver.StaleBindingException | ApiErrorException unavailable) {
            readiness = Readiness.UNAVAILABLE;
        }
        return new ProviderBindingStatus(BindingState.ACTIVE, current.revision(), current.adapterKey(), readiness);
    }
}
