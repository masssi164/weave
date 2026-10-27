package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilesPlatformDiagnosticsTest {
    @Test
    void shellBlockWinsOverReadinessOverrideButDisabledModuleRemainsUnavailable() {
        var capabilities = mock(WorkspaceCapabilityService.class);
        var observation = new FilesPlatformDiagnostics(capabilities);
        var blockedAuth = new PlatformStatusResponse.DiagnosticStatus("blocked", "blocked", "Auth", null);
        when(capabilities.settings("files")).thenReturn(Optional.of(
                new CapabilitySettings(true, "https://provider.example", WorkspaceCapabilityReadiness.READY)));
        assertThat(observation.status(blockedAuth).readiness()).isEqualTo("blocked");
        assertThat(observation.status(blockedAuth).action()).contains("first-party auth contract");
        when(capabilities.settings("files")).thenReturn(Optional.of(new CapabilitySettings(false, null, null)));
        assertThat(observation.status(blockedAuth).readiness()).isEqualTo("unavailable");
    }

    @Test
    void installedDeclarationIsRequiredAndConfiguredRouteIsMetadataOnly() {
        var capabilities = mock(WorkspaceCapabilityService.class);
        var observation = new FilesPlatformDiagnostics(capabilities);
        var readyAuth = new PlatformStatusResponse.DiagnosticStatus("up", "ready", "Auth", null);
        when(capabilities.settings("files")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> observation.status(readyAuth)).isInstanceOf(IllegalStateException.class);
        when(capabilities.settings("files")).thenReturn(Optional.of(
                new CapabilitySettings(true, "https://offline.invalid", null)));
        assertThat(observation.status(readyAuth).readiness()).isEqualTo("ready");
        assertThat(observation.status(readyAuth).message()).contains("configured public dependency route");
    }
}
