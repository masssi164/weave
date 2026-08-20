package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
    void filesRegistryPublishesTheRuntimeObservedBoundedContentProfileAndLimits() {
        FilesProviderPort runtime = mock(
                FilesProviderPort.class,
                Mockito.withSettings().extraInterfaces(FilesStreamingContentPort.class));
        FilesStreamingContentPort streaming = (FilesStreamingContentPort) runtime;
        when(runtime.configured()).thenReturn(true);
        AtomicBoolean healthy = new AtomicBoolean(true);
        when(runtime.readiness()).thenAnswer(ignored -> healthy.get()
                ? com.massimotter.weave.backend.portability.ProviderReadiness.ready("files-native-ready")
                : com.massimotter.weave.backend.portability.ProviderReadiness.degraded("files-native-streaming-not-ready"));
        Mockito.doAnswer(ignored -> {
                    if (!healthy.get()) {
                        throw new IllegalStateException("streaming readiness is degraded");
                    }
                    return null;
                })
                .when(streaming)
                .requireStreamingReady();
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files",
                "weave-native",
                java.util.Set.of("files.content_streaming_read", "files.content_streaming_write"),
                java.util.Map.of(),
                true,
                true,
                true));
        when(streaming.contentProfile()).thenReturn(new FilesStreamingContentPort.ContentProfile(
                12_345L,
                4_096,
                3,
                2));

        ProviderPort registry = configuration.filesProviderRegistrySeamFor(runtime);
        ProviderStatusResponse status = registry.status();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> profile =
                (java.util.Map<String, Object>) status.diagnostics().get("capabilityProfile");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> limits =
                (java.util.Map<String, Object>) profile.get("limits");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> capabilities =
                (java.util.Map<String, Object>) profile.get("capabilities");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> read =
                (java.util.Map<String, Object>) capabilities.get("files.content_streaming_read");

        assertThat(profile)
                .containsEntry("apiVersion", "weave.capability-profile/v1")
                .containsEntry("adapterVersion", "weave-native/bounded-content-v1")
                .containsEntry("conformanceVersion", "weave.files-bounded-content/v1")
                .containsKeys("observedAt", "expiresAt");
        assertThat(limits)
                .containsEntry("maximumContentBytes", 12_345L)
                .containsEntry("transferBufferBytes", 4_096)
                .containsEntry("maximumIngressConcurrency", 3)
                .containsEntry("maximumEgressConcurrency", 2);
        assertThat(read)
                .containsEntry("status", "native")
                .containsEntry("fidelity", "F0")
                .containsEntry("verified", true)
                .containsEntry(
                        "evidenceRef",
                        "weave:docs/evidence/native-files-bounded-streaming.md");

        healthy.set(false);
        ProviderStatusResponse degraded = registry.status();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> degradedProfile =
                (java.util.Map<String, Object>) degraded.diagnostics().get("capabilityProfile");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> degradedCapabilities =
                (java.util.Map<String, Object>) degradedProfile.get("capabilities");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> degradedRead =
                (java.util.Map<String, Object>) degradedCapabilities.get("files.content_streaming_read");
        assertThat(degradedRead)
                .containsEntry("status", "blocked")
                .containsEntry("fidelity", "F4")
                .containsEntry("verified", false);
        assertThat(degraded.supportedCapabilities())
                .doesNotContain("files.content_streaming_read", "files.content_streaming_write");
    }

    @Test
    void filesRegistryDoesNotAdvertiseContentOperationsWithoutABoundRuntime() {
        @SuppressWarnings("unchecked")
        ObjectProvider<FilesProviderPort> runtimes = mock(ObjectProvider.class);

        ProviderStatusResponse status = configuration.filesProviderRegistrySeam(runtimes).status();

        assertThat(status.supportedCapabilities())
                .contains("list")
                .doesNotContain(
                        "read",
                        "write",
                        "files.content_streaming_read",
                        "files.content_streaming_write");
    }

    @Test
    void filesRegistryPublishesBlockedProfileForAnUnqualifiedSelectedAdapter() {
        FilesProviderPort runtime = mock(FilesProviderPort.class);
        when(runtime.configured()).thenReturn(true);
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "files",
                "nextcloud-webdav",
                java.util.Set.of("list", "read", "write"),
                java.util.Map.of(),
                true,
                true,
                true));

        ProviderStatusResponse status = configuration.filesProviderRegistrySeamFor(runtime).status();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> profile =
                (java.util.Map<String, Object>) status.diagnostics().get("capabilityProfile");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> capabilities =
                (java.util.Map<String, Object>) profile.get("capabilities");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> read =
                (java.util.Map<String, Object>) capabilities.get("files.content_streaming_read");
        assertThat(read)
                .containsEntry("status", "blocked")
                .containsEntry("fidelity", "F4")
                .containsEntry("verified", false);
        assertThat(profile).doesNotContainKey("limits");
        assertThat(status.supportedCapabilities())
                .contains("list")
                .doesNotContain(
                        "read",
                        "write",
                        "files.content_streaming_read",
                        "files.content_streaming_write");
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
