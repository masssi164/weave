package com.massimotter.weave.backend.calendar.autoconfigure;

import com.massimotter.weave.backend.calendar.api.CalendarController;
import com.massimotter.weave.backend.calendar.api.CalendarUserController;
import com.massimotter.weave.backend.calendar.api.dav.CalDavCalendarController;
import com.massimotter.weave.backend.calendar.application.CalendarFacadeService;
import com.massimotter.weave.backend.calendar.application.CalendarUserApiService;
import com.massimotter.weave.backend.calendar.persistence.NativeCalendarProviderAdapter;
import com.massimotter.weave.backend.calendar.persistence.NativeCalendarRelationalStore;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.calendar.provider.caldav.CalDavCalendarAdapter;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

/** Discovers published imports without App, manual runtime imports or live provider calls. */
@Tag("context")
class CalendarRuntimeDiscoveryTest {
    @Test
    void discoversActualCalendarHandlersAndNativeAdapterWithoutOtherDomains() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                        "weave.compatibility.public-dav.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CalendarRuntimeAutoConfiguration.class)
                            .hasSingleBean(CalendarFacadeService.class).hasSingleBean(CalendarUserApiService.class)
                            .hasSingleBean(CalendarProviderPort.class).hasSingleBean(NativeCalendarProviderAdapter.class)
                            .hasSingleBean(NativeCalendarRelationalStore.class)
                            .hasSingleBean(CalendarController.class).hasSingleBean(CalendarUserController.class)
                            .hasSingleBean(CalDavCalendarController.class);
                    assertThat(context.getBean(WorkspaceCapabilityService.class).installedDefinitions())
                            .extracting(value -> value.domainId()).containsExactly("calendar");
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                            .getHandlerMethods().keySet().stream()
                            .flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/calendar/scopes", "/api/calendar/calendars");
                    assertThat(paths).anyMatch(path -> path.startsWith("/caldav"));
                    assertThat(paths).noneMatch(path -> path.startsWith("/api/files") || path.startsWith("/_matrix"));
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication",
                            context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.files.domain.FilesDomain",
                            context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("org.apache.opendal.Operator", context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.matrix.NativeMatrixCore",
                            context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.chat.persistence.MatrixE2eeRelationalStore",
                            context.getClassLoader())).isFalse();
                });
    }

    @Test
    void calDavBindingConstructsOfflineWithoutASecretOrProviderRequest() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                        "weave.calendar.provider=nextcloud-caldav")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CalendarProviderPort.class)
                        .hasSingleBean(CalDavCalendarAdapter.class).doesNotHaveBean(NativeCalendarProviderAdapter.class));
    }

    @Test
    void schemaAndOperatorContextsDoNotConstructServingProvidersOrHandlers() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CalendarRuntimeAutoConfiguration.class)
                        .doesNotHaveBean(CalendarFacadeService.class).doesNotHaveBean(CalendarController.class)
                        .doesNotHaveBean(CalendarProviderPort.class).doesNotHaveBean(NativeCalendarRelationalStore.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
