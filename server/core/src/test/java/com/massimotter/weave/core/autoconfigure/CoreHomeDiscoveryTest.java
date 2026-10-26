package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.home.api.WorkspaceController;
import com.massimotter.weave.backend.capability.api.AdminWorkspaceController;
import com.massimotter.weave.backend.home.application.WorkspaceHomeService;
import com.massimotter.weave.backend.home.port.WorkspaceHomeActivitySource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("context")
class CoreHomeDiscoveryTest {
    @Test
    void actualImportsDiscoverSharedHomeWithoutOptionalImplementationsOrSections() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CoreHomeAutoConfiguration.class)
                            .hasSingleBean(WorkspaceHomeService.class).hasSingleBean(WorkspaceController.class)
                            .hasSingleBean(AdminWorkspaceController.class).doesNotHaveBean(WorkspaceHomeActivitySource.class);
                    assertThat(context.getBean(WorkspaceHomeService.class).snapshot(null).sections())
                            .extracting(section -> section.key()).containsExactly("workspace-health");
                });
    }

    @Test
    void nonWebToolingDoesNotDiscoverWorkspaceHandlersOrHomeComposition() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CoreHomeAutoConfiguration.class)
                        .doesNotHaveBean(WorkspaceHomeService.class).doesNotHaveBean(WorkspaceController.class)
                        .doesNotHaveBean(AdminWorkspaceController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
