package com.massimotter.weave.backend.boards.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import org.junit.jupiter.api.Test;

class BoardsProviderRegistryConfigurationTest {
    private final BoardsProviderRegistryConfiguration configuration = new BoardsProviderRegistryConfiguration();

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
}
