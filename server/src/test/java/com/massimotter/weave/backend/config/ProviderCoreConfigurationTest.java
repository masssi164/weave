package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProviderCoreConfigurationTest {

    private final ProviderCoreConfiguration configuration = new ProviderCoreConfiguration();

    @Test
    void chatRegistryReportsTheBoundNativeAdapterWithoutInflatingReadiness() {
        ChatProviderPort runtime = mock(ChatProviderPort.class);
        when(runtime.providerKey()).thenReturn("weave-native");
        when(runtime.configured()).thenReturn(true);
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "chat",
                "weave-native",
                java.util.Set.of("timeline", "send"),
                java.util.Map.of(),
                true,
                true,
                true));

        ProviderStatusResponse status = configuration.chatProviderRegistrySeamFor(runtime).status();

        assertThat(status.providerKey()).isEqualTo("weave-native");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.readiness()).isEqualTo("configured_pending_cached_health");
        assertThat(status.candidates()).contains("weave-native", "matrix-synapse");
        assertThat(status.diagnostics())
                .containsEntry("runtimeBindingObserved", true)
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
    }

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
    void providerStatusNamesActiveUserApiRatherThanDisabledPublicDav() {
        @SuppressWarnings("unchecked")
        ObjectProvider<FilesProviderPort> noFiles = mock(ObjectProvider.class);
        when(noFiles.orderedStream()).thenReturn(Stream.empty());
        ProviderStatusResponse files = configuration.filesProviderRegistrySeam(
                noFiles, new FilesRuntimeProperties("weave-native")).status();
        assertThat(files.diagnostics()).containsEntry("facade", "/api/files/items");

        @SuppressWarnings("unchecked")
        ObjectProvider<CalendarProviderPort> noCalendar = mock(ObjectProvider.class);
        ProviderStatusResponse calendar = configuration.calendarProviderRegistrySeam(noCalendar).status();
        assertThat(calendar.diagnostics()).containsEntry("facade", "/api/calendar/calendars");

        CalendarProviderPort runtime = mock(CalendarProviderPort.class);
        when(runtime.configured()).thenReturn(true);
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "calendar", "weave-native", Set.of("query", "read"), Map.of(), true, true, true));
        when(noCalendar.getIfAvailable()).thenReturn(runtime);
        ProviderStatusResponse boundCalendar = configuration.calendarProviderRegistrySeam(noCalendar).status();
        assertThat(boundCalendar.summary()).contains("generated User Calendar API").doesNotContain("/caldav");
    }

    @Test
    void boardsRegistryReportsTheActuallyBoundLocalWorkspaceAdapter() {
        ProviderStatusResponse status = configuration
                .boardsProviderRegistrySeamFor(new LocalWorkspaceBoardsRepository())
                .status();

        assertThat(status.providerKey()).isEqualTo("local-workspace");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.configured()).isTrue();
        assertThat(status.providerRealityLevel()).isEqualTo(ProviderRealityLevel.CONFIGURED);
        assertThat(status.candidates()).contains("openproject-primary", "local-workspace");
        assertThat(status.diagnostics())
                .containsEntry("runtimeBindingObserved", true)
                .containsEntry("runtimeAdapterKind", "local-workspace")
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
        assertThat(status.summary()).doesNotContain("primary workspace-sync provider");
    }

    @Test
    void liveKitSfuAdapterReportsDirectCredentialModeSupportSafely() {
        ProviderStatusResponse status = configuration.liveKitSfuProviderRegistrySeam(
                new LiveKitSfuProviderProperties(
                        true,
                        "https://livekit.internal",
                        "secret-api-key",
                        "secret-api-secret",
                        ""))
                .status();

        assertThat(status.module().contractName()).isEqualTo("meetings");
        assertThat(status.providerKey()).isEqualTo("livekit");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.readiness()).isEqualTo("configured");
        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.failClosed()).isTrue();
        assertThat(status.supportSafe()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("livekitUrlConfigured", true)
                .containsEntry("apiKeyConfigured", true)
                .containsEntry("apiSecretConfigured", true)
                .containsEntry("directCredentialModeConfigured", true)
                .containsEntry("tokenEndpointModeConfigured", false)
                .containsEntry("secretsReturned", false);
        assertThat(status.toString())
                .doesNotContain("secret-api-key")
                .doesNotContain("secret-api-secret")
                .doesNotContain("https://livekit.internal");
    }

    @Test
    void liveKitSfuAdapterCanUseTokenEndpointModeWithoutReturningEndpointValue() {
        ProviderStatusResponse status = configuration.liveKitSfuProviderRegistrySeam(
                new LiveKitSfuProviderProperties(
                        true,
                        "https://livekit.internal",
                        "",
                        "",
                        "https://token-broker.internal/livekit"))
                .status();

        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.configured()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("tokenEndpointConfigured", true)
                .containsEntry("directCredentialModeConfigured", false)
                .containsEntry("tokenEndpointModeConfigured", true)
                .containsEntry("secretsReturned", false);
        assertThat(status.toString())
                .doesNotContain("https://livekit.internal")
                .doesNotContain("https://token-broker.internal/livekit");
    }
}
