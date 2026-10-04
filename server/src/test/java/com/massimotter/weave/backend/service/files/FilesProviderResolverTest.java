package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilesProviderResolverTest {

    @Test
    void resolvesActiveProviderPerOrganizationAndPinsItsRevision() {
        ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
        FilesProviderPort nextcloud = adapter("nextcloud-webdav", true);
        FilesProviderPort nativeFiles = adapter("weave-native", true);
        when(bindings.current("org-a", "files"))
                .thenReturn(Optional.of(binding("org-a", 3, "nextcloud-webdav")));
        when(bindings.current("org-b", "files"))
                .thenReturn(Optional.of(binding("org-b", 7, "weave-native")));
        FilesProviderResolver resolver = new FilesProviderResolver(bindings, List.of(nextcloud, nativeFiles));

        assertThat(resolver.current("org-a", "workspace-default")).isSameAs(nextcloud);
        assertThat(resolver.current("org-b", "workspace-default")).isSameAs(nativeFiles);
        ArgumentCaptor<FilesRequestScope> nextcloudScope = ArgumentCaptor.forClass(FilesRequestScope.class);
        ArgumentCaptor<FilesRequestScope> nativeScope = ArgumentCaptor.forClass(FilesRequestScope.class);
        verify(nextcloud).scoped(nextcloudScope.capture());
        verify(nativeFiles).scoped(nativeScope.capture());
        assertThat(nextcloudScope.getValue()).isEqualTo(new FilesRequestScope("org-a", "workspace-default", 3));
        assertThat(nativeScope.getValue()).isEqualTo(new FilesRequestScope("org-b", "workspace-default", 7));
    }

    @Test
    void stagingAndUnknownOrUnconfiguredAdaptersNeverBecomeLiveRoutes() {
        ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
        FilesProviderPort active = adapter("nextcloud-webdav", true);
        FilesProviderPort candidate = adapter("weave-native", true);
        when(bindings.current("org-a", "files"))
                .thenReturn(Optional.of(binding("org-a", 1, "nextcloud-webdav")));
        FilesProviderResolver resolver = new FilesProviderResolver(bindings, List.of(active, candidate));

        assertThat(resolver.current("org-a", "workspace-default")).isSameAs(active);
        org.mockito.Mockito.verify(candidate, org.mockito.Mockito.never())
                .scoped(org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> resolver.current("org-b", "workspace-default"))
                .isInstanceOf(FilesProviderResolver.ProviderUnavailableException.class);
        when(bindings.current("org-a", "files"))
                .thenReturn(Optional.of(binding("org-a", 2, "unknown")));
        assertThatThrownBy(() -> resolver.current("org-a", "workspace-default"))
                .isInstanceOf(FilesProviderResolver.ProviderUnavailableException.class);
        when(bindings.current("org-a", "files"))
                .thenReturn(Optional.of(binding("org-a", 3, "unconfigured")));
        FilesProviderResolver unconfigured = new FilesProviderResolver(bindings, List.of(adapter("unconfigured", false)));
        assertThatThrownBy(() -> unconfigured.current("org-a", "workspace-default"))
                .isInstanceOf(FilesProviderResolver.ProviderUnavailableException.class);
    }

    @Test
    void stalePinnedMutationDoesNotDispatchToEitherProvider() {
        ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
        ProviderBinding pinned = binding("org-a", 1, "nextcloud-webdav");
        when(bindings.current("org-a", "files"))
                .thenReturn(Optional.of(binding("org-a", 2, "weave-native")));
        FilesProviderPort nextcloud = adapter("nextcloud-webdav", true);
        FilesProviderPort nativeFiles = adapter("weave-native", true);
        FilesProviderResolver resolver = new FilesProviderResolver(bindings, List.of(nextcloud, nativeFiles));

        assertThatThrownBy(() -> resolver.pinned(pinned, "org-a", "workspace-default"))
                .isInstanceOf(FilesProviderResolver.StaleBindingException.class);
        assertThatThrownBy(() -> resolver.pinned(binding("org-b", 2, "weave-native"),
                "org-a", "workspace-default"))
                .isInstanceOf(FilesProviderResolver.StaleBindingException.class);
        org.mockito.Mockito.verify(nextcloud, org.mockito.Mockito.never())
                .scoped(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(nativeFiles, org.mockito.Mockito.never())
                .scoped(org.mockito.ArgumentMatchers.any());
    }

    private ProviderBinding binding(String organizationRef, long revision, String adapterKey) {
        return new ProviderBinding(organizationRef, "files", revision, adapterKey, "test:files", State.ACTIVE,
                Instant.parse("2026-10-04T00:00:00Z"));
    }

    private FilesProviderPort adapter(String key, boolean configured) {
        FilesProviderPort adapter = mock(FilesProviderPort.class);
        when(adapter.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", key, Set.of("list"), Map.of(), true, true, true));
        when(adapter.configured()).thenReturn(configured);
        when(adapter.scoped(org.mockito.ArgumentMatchers.any())).thenReturn(adapter);
        return adapter;
    }
}
