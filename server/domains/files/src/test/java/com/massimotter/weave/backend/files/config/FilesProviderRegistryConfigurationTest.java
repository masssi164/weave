package com.massimotter.weave.backend.files.config;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FilesProviderRegistryConfigurationTest {
    private final FilesProviderRegistryConfiguration configuration = new FilesProviderRegistryConfiguration();

    @Test
    void filesRegistryKeepsPermissionCapabilityDetailsOutOfSupportStatus() {
        FilesProviderPort runtime = mock(FilesProviderPort.class);
        when(runtime.configured()).thenReturn(true);
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "weave-native", Set.of("list", "read"),
                Map.of("userGrant", MappingClass.UNSUPPORTED,
                        "effectiveAccess", MappingClass.UNSUPPORTED),
                true, true, true));
        @SuppressWarnings("unchecked")
        ObjectProvider<FilesProviderPort> candidates = mock(ObjectProvider.class);
        when(candidates.orderedStream()).thenReturn(Stream.of(runtime));

        ProviderStatusResponse status = configuration.filesProviderRegistrySeam(
                candidates, new FilesRuntimeProperties("weave-native")).status();

        assertThat(status.readiness()).isEqualTo("configured_pending_cached_health");
        assertThat(status.failClosed()).isTrue();
        assertThat(status.supportSafe()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false)
                .doesNotContainKeys("fieldMappings", "userGrant", "effectiveAccess");
        assertThat(status.summary()).contains("generated User Files API").doesNotContain("/dav/files");
    }
    @Test
    void missingFilesAdapterNamesTheGeneratedUserApi() {
        @SuppressWarnings("unchecked")
        ObjectProvider<FilesProviderPort> noFiles = mock(ObjectProvider.class);
        when(noFiles.orderedStream()).thenReturn(Stream.empty());
        ProviderStatusResponse files = configuration.filesProviderRegistrySeam(
                noFiles, new FilesRuntimeProperties("weave-native")).status();
        assertThat(files.diagnostics()).containsEntry("facade", "/api/files/items");
        assertThat(files.configured()).isFalse();
        assertThat(files.failClosed()).isTrue();
    }

    @Test
    void filesRegistrySelectsOnlyTheExistingConfiguredAdapterKey() {
        FilesProviderPort other = mock(FilesProviderPort.class);
        FilesProviderPort selected = mock(FilesProviderPort.class);
        when(other.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "nextcloud-webdav", Set.of(), Map.of(), true, true, true));
        when(selected.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files", "weave-native", Set.of("list"), Map.of(), true, true, true));
        when(selected.configured()).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<FilesProviderPort> candidates = mock(ObjectProvider.class);
        when(candidates.orderedStream()).thenReturn(Stream.of(other, selected));
        assertThat(configuration.filesProviderRegistrySeam(candidates, new FilesRuntimeProperties("weave-native"))
                .status().providerKey()).isEqualTo("weave-native");
        org.mockito.Mockito.verify(other, org.mockito.Mockito.never()).configured();
    }
    @Test
    void filesRegistryConfigurationComposesWithoutOtherDomainClasses() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader(
                        "com.massimotter.weave.backend.chat", "com.massimotter.weave.backend.calendar",
                        "com.massimotter.weave.backend.boards", "com.massimotter.weave.backend.meetings",
                        "com.massimotter.weave.backend.integrations"))
                .withUserConfiguration(FilesProviderRegistryConfiguration.class)
                .withBean(FilesRuntimeProperties.class, () -> new FilesRuntimeProperties("weave-native"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(com.massimotter.weave.backend.discovery.provider.ProviderPort.class);
                    assertThat(context).hasBean("filesProviderRegistrySeam").doesNotHaveBean("chatProviderRegistrySeam")
                            .doesNotHaveBean("calendarProviderRegistrySeam");
                    var status = context.getBean(com.massimotter.weave.backend.discovery.provider.ProviderPort.class).status();
                    assertThat(status.configured()).isFalse();
                    assertThat(status.failClosed()).isTrue();
                });
    }
}
