package com.massimotter.weave.backend.capability;

import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.files.capability.FilesWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DomainCapabilityConfigurationTest {
    @Test
    void ownersBindExistingKeysIndependentlyWithoutInventingOtherDomains() {
        new ApplicationContextRunner()
                .withUserConfiguration(FilesWorkspaceCapabilityConfiguration.class,
                        CalendarWorkspaceCapabilityConfiguration.class)
                .withBean(ProviderCapabilityHealthService.class, () -> mock(ProviderCapabilityHealthService.class))
                .withPropertyValues("weave.workspace.files.enabled=false",
                        "weave.workspace.calendar.enabled=true",
                        "weave.workspace.calendar.dependency-url=https://calendar.weave.test",
                        "weave.workspace.calendar.readiness=degraded")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var contributions = context.getBeansOfType(WorkspaceCapabilityContribution.class).values();
                    assertThat(contributions).extracting(value -> value.definition().domainId())
                            .containsExactlyInAnyOrder("files", "calendar");
                    var calendar = contributions.stream().filter(value -> value.definition().domainId().equals("calendar"))
                            .findFirst().orElseThrow().definition().settings();
                    assertThat(calendar.enabled()).isTrue();
                    assertThat(calendar.dependencyUrl()).isEqualTo("https://calendar.weave.test");
                    assertThat(calendar.readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
                    assertThat(contributions.stream().filter(value -> value.definition().domainId().equals("files"))
                            .findFirst().orElseThrow().definition().settings().enabled()).isFalse();
                });
    }

    @Test
    void anInstalledDomainRetainsItsOwnDefaultWithoutInstallingOthers() {
        new ApplicationContextRunner().withUserConfiguration(CalendarWorkspaceCapabilityConfiguration.class)
                .withBean(ProviderCapabilityHealthService.class, () -> mock(ProviderCapabilityHealthService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkspaceCapabilityContribution.class);
                    var definition = context.getBean(WorkspaceCapabilityContribution.class).definition();
                    assertThat(definition.domainId()).isEqualTo("calendar");
                    assertThat(definition.settings().enabled()).isFalse();
                    assertThat(definition.settings().readiness()).isNull();
                });
    }
}
