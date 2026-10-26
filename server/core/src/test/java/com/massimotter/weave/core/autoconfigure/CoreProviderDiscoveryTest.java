package com.massimotter.weave.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.massimotter.weave.backend.discovery.application.PlatformContractService;
import com.massimotter.weave.backend.discovery.api.PlatformController;
import com.massimotter.weave.backend.api.error.ApiErrorException;

import com.massimotter.weave.backend.discovery.api.ProviderRegistryController;
import com.massimotter.weave.backend.discovery.application.AdminProviderRegistryService;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.health.api.ProviderCapabilityHealthController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Tag("context")
class CoreProviderDiscoveryTest {
    @Test
    void actualImportsDiscoverSharedHandlersWithoutOptionalMetadataOrImplementations() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CoreProviderDiscoveryAutoConfiguration.class)
                            .hasSingleBean(PlatformContractService.class).hasSingleBean(PlatformController.class)
                            .hasSingleBean(com.massimotter.weave.backend.admin.application.AdminControlPlaneService.class)
                            .hasSingleBean(com.massimotter.weave.backend.admin.api.AdminControlPlaneController.class)
                            .hasSingleBean(ProviderRegistry.class).hasSingleBean(AdminProviderRegistryService.class)
                            .hasSingleBean(ProviderRegistryController.class)
                            .hasSingleBean(ProviderCapabilityHealthController.class);
                    assertThat(context.getBean(ProviderCategoryCatalog.class).categoryKeys()).isEmpty();
                    var status = context.getBean(ProviderRegistry.class).status();
                    assertThat(status.providers()).isEmpty();
                    assertThat(status.categories()).extracting(value -> value.category()).containsExactly("admin-control-plane");
                    assertThat(status.canonicalDomainRegistry().domains()).extracting(value -> value.key())
                            .containsExactly("people", "spaces", "health");
                    assertThat(context.getBean(PlatformContractService.class).config().protocols().matrixClientServerBaseUrl()).isNull();
                    assertThat(context.getBean(PlatformContractService.class).status("core-only").matrix()).isNull();
                    assertThat(context.getBean(com.massimotter.weave.backend.admin.application.AdminCapabilityCatalog.class)
                            .knownCapabilities()).doesNotContain("files.read", "manuals.read", "chat.send");
                    assertThat(context.getBean(com.massimotter.weave.backend.admin.application.SuiteDomainCatalog.class).groups()).isEmpty();
                    assertThat(context.getBeansOfType(com.massimotter.weave.backend.admin.api.McpServerBindingResponse.class)).isEmpty();
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                            .getHandlerMethods().keySet().stream()
                            .flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/admin/providers/status", "/api/admin/provider-capability-health", "/api/platform/config", "/api/platform/status", "/api/admin/control-plane");
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication",
                            context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.files.application.FilesUserApiService",
                            context.getClassLoader())).isFalse();
                });
    }

    @Test
    void nonWebContextsDoNotInstallServingDiscoveryOrHealthHandlers() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(com.massimotter.weave.backend.admin.application.AdminControlPlaneService.class)
                        .doesNotHaveBean(com.massimotter.weave.backend.admin.api.AdminControlPlaneController.class)
                        .doesNotHaveBean(ProviderRegistry.class)
                        .doesNotHaveBean(PlatformContractService.class).doesNotHaveBean(PlatformController.class)
                        .doesNotHaveBean(ProviderRegistryController.class)
                        .doesNotHaveBean(ProviderCapabilityHealthController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
