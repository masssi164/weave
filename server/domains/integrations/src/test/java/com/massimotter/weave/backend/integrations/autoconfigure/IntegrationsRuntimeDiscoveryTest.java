package com.massimotter.weave.backend.integrations.autoconfigure;

import com.massimotter.weave.backend.integrations.api.InteropController;
import com.massimotter.weave.backend.integrations.application.InteropGatewayService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses production module-local imports, with actual shared ORM prerequisites and no App. */
@Tag("context")
class IntegrationsRuntimeDiscoveryTest {
    @Test
    void discoversActualOwningHandlersAndSingletonServicesWithoutSiblingFeatures() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(IntegrationsRuntimeAutoConfiguration.class)
                            .hasSingleBean(InteropController.class).hasSingleBean(InteropGatewayService.class);
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                            .getHandlerMethods().keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/interop/status");
                    assertThat(paths).noneMatch(value -> value.startsWith("/_matrix") || value.startsWith("/api/calendar") || value.startsWith("/api/files"));
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication", context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService", context.getClassLoader())).isFalse();
                });
    }

    @Test
    void nonWebToolingExcludesFutureServingGraph() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(IntegrationsRuntimeAutoConfiguration.class)
                        .doesNotHaveBean(InteropController.class).doesNotHaveBean(InteropGatewayService.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
