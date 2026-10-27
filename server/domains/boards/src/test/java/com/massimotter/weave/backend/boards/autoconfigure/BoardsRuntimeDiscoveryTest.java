package com.massimotter.weave.backend.boards.autoconfigure;

import com.massimotter.weave.backend.boards.api.BoardsController;
import com.massimotter.weave.backend.boards.application.BoardsFacadeService;
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
class BoardsRuntimeDiscoveryTest {
    @Test
    void discoversOnlyOwningFutureRuntimeAndRealHandler() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(BoardsRuntimeAutoConfiguration.class)
                            .hasSingleBean(BoardsFacadeService.class).hasSingleBean(BoardsController.class);
                    assertThat(context.getBean(WorkspaceCapabilityService.class).installedDefinitions())
                            .extracting(value -> value.domainId()).containsExactly("boards");
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                            .getHandlerMethods().keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/boards/workspace");
                    assertThat(paths).noneMatch(path -> path.startsWith("/_matrix") || path.startsWith("/api/calendar") || path.startsWith("/api/files"));
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication", context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService", context.getClassLoader())).isFalse();
                });
    }

    @Test
    void nonWebToolingDoesNotConstructFutureServingGraph() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(BoardsRuntimeAutoConfiguration.class)
                        .doesNotHaveBean(BoardsFacadeService.class).doesNotHaveBean(BoardsController.class));
    }

    @Test
    void constructingGatedOpenProjectAdapterDoesNotContactProviderOrReadSecrets() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                        "weave.boards.workspace.provider=openproject")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(
                            com.massimotter.weave.backend.boards.port.BoardsRepository.class);
                    assertThat(context.getBean(com.massimotter.weave.backend.boards.port.BoardsRepository.class))
                            .isInstanceOf(com.massimotter.weave.backend.boards.provider.openproject.OpenProjectBoardsRepository.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
