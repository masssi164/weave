package com.massimotter.weave.backend.devops.autoconfigure;

import com.massimotter.weave.backend.devops.api.DevopsController;
import com.massimotter.weave.backend.devops.application.DevopsFacadeService;
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

/** Discovers production imports resources; no App or manually imported domain configuration. */
@Tag("context")
class DevopsRuntimeDiscoveryTest {
    @Test
    void discoversOnlyOwningFutureRuntimeAndRealHandler() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DevopsRuntimeAutoConfiguration.class)
                            .hasSingleBean(DevopsFacadeService.class).hasSingleBean(DevopsController.class);
                    assertThat(context.getBean(WorkspaceCapabilityService.class).installedDefinitions())
                            .extracting(value -> value.domainId()).containsExactly("devops");
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                            .getHandlerMethods().keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/workspaces/{workspaceId}/channels/{channelId}/devops/summary");
                    assertThat(paths).noneMatch(path -> path.startsWith("/_matrix") || path.startsWith("/api/calendar") || path.startsWith("/api/files"));
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication", context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService", context.getClassLoader())).isFalse();
                });
    }

    @Test
    void nonWebToolingDoesNotConstructFutureServingGraph() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DevopsRuntimeAutoConfiguration.class)
                        .doesNotHaveBean(DevopsFacadeService.class).doesNotHaveBean(DevopsController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
