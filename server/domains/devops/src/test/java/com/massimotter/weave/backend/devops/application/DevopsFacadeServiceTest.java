package com.massimotter.weave.backend.devops.application;

import com.massimotter.weave.backend.devops.provider.DisabledDevopsProvider;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DevopsFacadeServiceTest {
    @Test
    void disabledProvidersDoNotAdvertiseAWorkingRepositoryOrPipeline() {
        var source = DisabledDevopsProvider.gitlab(ProviderModule.SOURCE_CONTROL, Set.of("repository-list"));
        var issues = DisabledDevopsProvider.gitlab(ProviderModule.ISSUE_TRACKER, Set.of("open-issues"));
        var ci = DisabledDevopsProvider.gitlab(ProviderModule.CI, Set.of("latest-pipeline"));
        var releases = DisabledDevopsProvider.gitlab(ProviderModule.RELEASE, Set.of("release-list"));
        var summary = new DevopsFacadeService(List.of(source), List.of(issues), List.of(ci), List.of(releases))
                .summary("workspace", "channel");
        assertThat(summary.providerReadiness()).hasSize(4).allSatisfy(status -> {
            assertThat(status.state()).isEqualTo(ProviderState.NOT_CONFIGURED);
            assertThat(status.configured()).isFalse();
            assertThat(status.failClosed()).isTrue();
        });
        assertThat(summary.repositories()).isEmpty();
        assertThat(summary.pipelines()).isEmpty();
        assertThat(summary.releases()).isEmpty();
        assertThat(summary.readOnly()).isTrue();
        assertThat(summary.supportSafe()).isTrue();
    }
}
