package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.readiness.WorkspaceReleaseReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class CoreWorkspaceCapabilityDiscoveryTest {
    @Test
    void importsDiscoverOnePolicyWithoutOptionalDomains() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CoreWorkspaceCapabilityAutoConfiguration.class)
                            .hasSingleBean(WorkspaceCapabilityService.class).hasSingleBean(WorkspaceReleaseReadinessService.class);
                    var policy = context.getBean(WorkspaceCapabilityService.class);
                    assertThat(policy.installedDefinitions()).isEmpty();
                    assertThat(policy.installedSnapshots(null)).isEmpty();
                    assertThat(policy.grantedCapabilities(null)).containsExactlyInAnyOrder(
                            "admin_control_plane.readiness_read","admin.policy.edit","admin.provider.configure");
                });
    }
    @Test
    void schemaAndOperatorContextsDoNotDiscoverServingPolicy() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(WorkspaceCapabilityService.class)
                        .doesNotHaveBean(WorkspaceReleaseReadinessService.class));
    }
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
