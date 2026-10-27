package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBindingStatus.BindingState;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBindingStatus.Readiness;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FilesProviderBindingStatusTest {
    private final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    private final FilesProviderResolver files = mock(FilesProviderResolver.class);
    private final FilesProviderBindingStatus status = new FilesProviderBindingStatus(bindings, files);

    @Test
    void noActiveBindingDoesNotConsultAnAdapterOrExposeConfiguration() {
        var response = status.status("organization");
        assertThat(response.bindingState()).isEqualTo(BindingState.NO_ACTIVE_BINDING);
        assertThat(response.readiness()).isEqualTo(Readiness.NOT_CONFIGURED);
        assertThat(response.bindingRevision()).isNull();
        assertThat(response.adapterKey()).isNull();
        verifyNoInteractions(files);
    }

    @ParameterizedTest
    @MethodSource("inadmissibleBindings")
    void foreignDomainOrganizationOrInactiveBindingNeverReachesAnAdapter(ProviderBinding binding) {
        when(bindings.current("organization", "files")).thenReturn(Optional.of(binding));
        assertThat(status.status("organization").bindingState()).isEqualTo(BindingState.NO_ACTIVE_BINDING);
        verifyNoInteractions(files);
    }

    @Test
    void probesTheCapturedRevisionAndCanonicalSpaceWithoutExposingItsPrivateHandle() {
        var binding = binding("organization", "files", ProviderBinding.State.ACTIVE);
        when(bindings.current("organization", "files")).thenReturn(Optional.of(binding));
        var response = status.status("organization");
        assertThat(response.bindingState()).isEqualTo(BindingState.ACTIVE);
        assertThat(response.bindingRevision()).isEqualTo(7);
        assertThat(response.adapterKey()).isEqualTo("weave-native");
        assertThat(response.readiness()).isEqualTo(Readiness.CONFIGURED);
        assertThat(response.toString()).doesNotContain("private-configuration");
        verify(files).pinned(binding, "organization", FilesUserApiService.SPACE_REF);
        verifyNoMoreInteractions(files);
    }

    @ParameterizedTest
    @MethodSource("unavailableFailures")
    void aStaleOrDeniedProbeNeverClaimsReadinessOrLeaksItsError(RuntimeException failure) {
        var binding = binding("organization", "files", ProviderBinding.State.ACTIVE);
        when(bindings.current("organization", "files")).thenReturn(Optional.of(binding));
        when(files.pinned(binding, "organization", FilesUserApiService.SPACE_REF)).thenThrow(failure);
        var response = status.status("organization");
        assertThat(response.readiness()).isEqualTo(Readiness.UNAVAILABLE);
        assertThat(response.toString()).doesNotContain("private-configuration", "private-provider-error");
    }

    static Stream<ProviderBinding> inadmissibleBindings() {
        return Stream.of(binding("other-organization", "files", ProviderBinding.State.ACTIVE),
                binding("organization", "calendar", ProviderBinding.State.ACTIVE),
                binding("organization", "files", ProviderBinding.State.RETIRED),
                binding("organization", "files", ProviderBinding.State.REVOKED));
    }

    static Stream<RuntimeException> unavailableFailures() {
        return Stream.of(new FilesProviderResolver.StaleBindingException(),
                new FilesProviderResolver.ProviderUnavailableException(),
                new ApiErrorException(HttpStatus.FORBIDDEN, "private-provider-error", "private-provider-error", Map.of()));
    }

    private static ProviderBinding binding(String organization, String domain, ProviderBinding.State state) {
        return new ProviderBinding(organization, domain, 7, "weave-native", "private-configuration", state, Instant.EPOCH);
    }
}
