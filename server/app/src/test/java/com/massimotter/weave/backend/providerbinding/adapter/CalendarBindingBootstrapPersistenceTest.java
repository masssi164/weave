package com.massimotter.weave.backend.providerbinding.adapter;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.CalendarBindingBootstrapConfiguration;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.service.calendar.CalendarProviderBindingReadiness;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarBindingBootstrapPersistenceTest {
    @Test void initialAuthorityIsDurableAndRetiredHistoryCannotBeReactivatedByStartup() throws Exception {
        var source = JpaTestDatabase.dataSource("calendar-binding-bootstrap");
        if (Boolean.getBoolean("weave.test.postgres")) {
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
            JpaTestDatabase.validateSchema(source);
        } else {
            JpaTestDatabase.initializeSchema(source);
        }
        var repository = ProviderBindingJpaTestFactory.create(source);
        var provider = mock(CalendarProviderPort.class);
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "calendar", "weave-native", Set.of(), Map.of(), true, true, true));
        when(provider.configured()).thenReturn(true);
        var readiness = new CalendarProviderBindingReadiness(repository, List.of(provider));
        var context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
        new ApplicationContextRunner().withUserConfiguration(CalendarBindingBootstrapConfiguration.class)
                .withBean(ProviderBindingRepository.class, () -> repository)
                .withBean(ContextAuthorizationProperties.class, () -> context)
                .withBean(CalendarProviderBindingReadiness.class, () -> readiness)
                .withPropertyValues("weave.provider-bindings.bootstrap.calendar.enabled=true",
                        "weave.provider-bindings.bootstrap.calendar.organization-ref=tenant-default",
                        "weave.provider-bindings.bootstrap.calendar.adapter-key=weave-native",
                        "weave.provider-bindings.bootstrap.calendar.configuration-ref=" + CalendarProviderBindingReadiness.CONFIGURATION_REF)
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var startup = application.getBean(ApplicationRunner.class);
                    startup.run(null);
                    var first = repository.current("tenant-default", "calendar").orElseThrow();
                    startup.run(null);
                    assertThat(repository.current("tenant-default", "calendar")).contains(first);
                    assertThat(readiness.require("tenant-default").binding()).isEqualTo(first);
                    var rows = JpaTestDatabase.repository(source, ProviderBindingJpaRepository.class);
                    new TransactionTemplate(JpaTestDatabase.transactionManager(source)).executeWithoutResult(status -> {
                        var current = rows.findById(new ProviderBindingId("tenant-default", "calendar", 1)).orElseThrow();
                        current.retire();
                        rows.saveAndFlush(current);
                    });
                    assertThat(repository.current("tenant-default", "calendar")).isEmpty();
                    assertThatThrownBy(() -> startup.run(null)).isInstanceOf(JpaProviderBindingRepository.StaleProviderBindingException.class);
                    assertThat(repository.revision("tenant-default", "calendar", 1).orElseThrow().state()).isEqualTo(ProviderBinding.State.RETIRED);
                    assertThat(repository.revision("tenant-default", "calendar", 2)).isEmpty();
                });
    }
}
