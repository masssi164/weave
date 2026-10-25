package com.massimotter.weave.backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.calendar.config.CalendarProviderHealthConfiguration;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.chat.config.ChatProviderHealthConfiguration;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.health.config.ProviderHealthProperties;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.massimotter.weave.backend.files.config.FilesProviderHealthConfiguration;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.health.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.health.ProviderCapabilityState;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProviderHealthContributionConfigurationTest {
    @Test
    void sharedHealthContextStartsWithNoOptionalDomains() {
        new ApplicationContextRunner().withUserConfiguration(ProviderCapabilityHealthService.class)
                .withBean(ProviderHealthProperties.class, () -> new ProviderHealthProperties(null, null, null, null))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
                    assertThat(context).hasSingleBean(ProviderCapabilityHealthService.class);
                    assertThat(context.getBean(ProviderCapabilityHealthService.class).supportSafeSnapshot().capabilities()).isEmpty();
                });
    }

    @Test
    void installedDomainWithoutConfiguredAdapterStillFailsClosed() {
        new ApplicationContextRunner().withUserConfiguration(ChatProviderHealthConfiguration.class).run(context -> {
            var contribution = context.getBean(ProviderHealthContribution.class);
            assertThat(contribution.configured().getAsBoolean()).isFalse();
            assertThat(contribution.probe().get().state()).isEqualTo(ProviderCapabilityState.UNAVAILABLE);
        });
    }

    @Test
    void filesUsesTheConfiguredAdapterAndDoesNotProbeDuringComposition() {
        FilesProviderPort selected = mock(FilesProviderPort.class);
        FilesProviderPort other = mock(FilesProviderPort.class);
        when(selected.conformanceProfile()).thenReturn(profile("weave-native"));
        when(other.conformanceProfile()).thenReturn(profile("nextcloud-webdav"));
        when(selected.configured()).thenReturn(true);
        when(selected.healthProbe()).thenReturn(ProviderCapabilityProbeResult.available("files-storage-ready"));
        new ApplicationContextRunner().withUserConfiguration(FilesProviderHealthConfiguration.class)
                .withBean(FilesRuntimeProperties.class, () -> new FilesRuntimeProperties("weave-native"))
                .withBean("other", FilesProviderPort.class, () -> other)
                .withBean("selected", FilesProviderPort.class, () -> selected)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProviderHealthContribution.class);
                    var contribution = context.getBean(ProviderHealthContribution.class);
                    assertThat(contribution.domain()).isEqualTo("files");
                    assertThat(contribution.configured().getAsBoolean()).isTrue();
                    verify(selected, never()).healthProbe();
                    assertThat(contribution.probe().get().state()).isEqualTo(ProviderCapabilityState.AVAILABLE);
                    verify(other, never()).healthProbe();
                });
    }

    @Test
    void calendarProbesTheBoundAdapterOnlyWhenRequested() {
        CalendarProviderPort calendar = mock(CalendarProviderPort.class);
        when(calendar.configured()).thenReturn(true);
        when(calendar.healthProbe()).thenReturn(ProviderCapabilityProbeResult.degraded("calendar-storage-rate-limited"));
        new ApplicationContextRunner().withUserConfiguration(CalendarProviderHealthConfiguration.class)
                .withBean(CalendarProviderPort.class, () -> calendar).run(context -> {
                    var contribution = context.getBean(ProviderHealthContribution.class);
                    assertThat(contribution.domain()).isEqualTo("calendar");
                    assertThat(contribution.configured().getAsBoolean()).isTrue();
                    verify(calendar, never()).healthProbe();
                    assertThat(contribution.probe().get().supportSafeCode()).isEqualTo("calendar-storage-rate-limited");
                });
    }

    @Test
    void chatTranslatesReadinessBehindItsOwnContribution() {
        ChatProviderPort chat = mock(ChatProviderPort.class);
        when(chat.configured()).thenReturn(true);
        when(chat.readiness()).thenReturn(ProviderReadiness.ready("chat-native-canonical-jpa-ready"),
                ProviderReadiness.degraded("chat-storage-unavailable"), ProviderReadiness.degraded("chat-storage-interrupted"),
                ProviderReadiness.degraded("chat-storage-rate-limited"));
        new ApplicationContextRunner().withUserConfiguration(ChatProviderHealthConfiguration.class)
                .withBean(ChatProviderPort.class, () -> chat).run(context -> {
                    var contribution = context.getBean(ProviderHealthContribution.class);
                    assertThat(contribution.domain()).isEqualTo("chat");
                    assertThat(contribution.configured().getAsBoolean()).isTrue();
                    verify(chat, never()).readiness();
                    assertThat(contribution.probe().get().supportSafeCode()).isEqualTo("chat-native-canonical-jpa-ready");
                    assertThat(contribution.probe().get().state()).isEqualTo(ProviderCapabilityState.UNAVAILABLE);
                    assertThat(contribution.probe().get().state()).isEqualTo(ProviderCapabilityState.UNAVAILABLE);
                    assertThat(contribution.probe().get().state()).isEqualTo(ProviderCapabilityState.DEGRADED);
                });
    }

    private ProviderConformanceProfile profile(String adapter) {
        return new ProviderConformanceProfile("files", adapter, Set.of(), Map.of(), true, true, true);
    }
}
