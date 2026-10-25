package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.FilesBindingStatusResponse.BindingState;
import com.massimotter.weave.backend.model.admin.FilesBindingStatusResponse.Readiness;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.files.FilesProviderResolver;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminProviderRegistryServiceTest {
    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    private final FilesProviderResolver files = mock(FilesProviderResolver.class);
    private final AdminProviderRegistryService service = new AdminProviderRegistryService(
            registry, capabilities, HumanJwtTestSupport.organizationAdmission(), bindings, files);

    @BeforeEach
    void configuredSnapshot() {
        when(registry.status()).thenReturn(new ProviderRegistryResponse("registry", "legacy-configuration",
                true, true, true, false, true, Instant.EPOCH, null, null,
                List.of(), List.of(), List.of(), null, null));
    }

    @Test
    void projectsActualConfiguredOrganizationBindingWithoutPrivateReferences() {
        ProviderBinding binding = binding("tenant-default");
        when(bindings.current("tenant-default", "files")).thenReturn(Optional.of(binding));
        var response = service.status(admin());
        assertThat(response.organizationId()).isEqualTo("tenant-default");
        assertThat(response.filesBinding().bindingState()).isEqualTo(BindingState.ACTIVE);
        assertThat(response.filesBinding().bindingRevision()).isEqualTo(7);
        assertThat(response.filesBinding().adapterKey()).isEqualTo("weave-native");
        assertThat(response.filesBinding().readiness()).isEqualTo(Readiness.CONFIGURED);
        assertThat(response.filesBinding().toString()).doesNotContain("private-configuration");
        verify(bindings).current("tenant-default", "files");
        verifyNoMoreInteractions(bindings);
        verify(files).pinned(binding, "tenant-default", "workspace-default");
    }

    @Test
    void missingBindingCannotBeInferredFromLegacySelections() {
        var response = service.status(admin());
        assertThat(response.filesBinding().bindingState()).isEqualTo(BindingState.NO_ACTIVE_BINDING);
        assertThat(response.filesBinding().readiness()).isEqualTo(Readiness.NOT_CONFIGURED);
        verifyNoInteractions(files);
    }

    @Test
    void staleOrUnavailableAdapterNeverReportsRuntimeReadiness() {
        ProviderBinding binding = binding("tenant-default");
        when(bindings.current("tenant-default", "files")).thenReturn(Optional.of(binding));
        when(files.pinned(binding, "tenant-default", "workspace-default"))
                .thenThrow(new FilesProviderResolver.ProviderUnavailableException());
        assertThat(service.status(admin()).filesBinding().readiness()).isEqualTo(Readiness.UNAVAILABLE);
    }

    @Test
    void corruptCrossOrganizationRepositoryResultIsNeverExposed() {
        when(bindings.current("tenant-default", "files")).thenReturn(Optional.of(binding("other-tenant")));
        assertThat(service.status(admin()).filesBinding().bindingState()).isEqualTo(BindingState.NO_ACTIVE_BINDING);
        verifyNoInteractions(files);
    }

    @Test
    void wrongOrganizationStopsBeforePrivateRegistryOrBindingReads() {
        Jwt wrong = Jwt.withTokenValue("wrong").header("alg", "none").subject("admin")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("admin"))
                .claim("tenant_id", "other-tenant").build();
        assertThatThrownBy(() -> service.status(wrong)).isInstanceOf(ApiErrorException.class);
        verifyNoInteractions(registry, bindings, files, capabilities);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("admin").header("alg", "none").subject("admin")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("admin")).build();
    }

    private ProviderBinding binding(String organization) {
        return new ProviderBinding(organization, "files", 7, "weave-native", "private-configuration",
                ProviderBinding.State.ACTIVE, Instant.EPOCH);
    }
}
