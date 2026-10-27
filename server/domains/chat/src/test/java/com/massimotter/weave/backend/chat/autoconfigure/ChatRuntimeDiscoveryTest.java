package com.massimotter.weave.backend.chat.autoconfigure;

import com.massimotter.weave.backend.chat.api.ChatController;
import com.massimotter.weave.backend.chat.api.matrix.MatrixClientServerProjectionController;
import com.massimotter.weave.backend.chat.application.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.chat.provider.weave.NativeChatProviderAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceReadiness;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;

/** Actual Boot imports discovery with the production graph, independent of App scanning/fixtures. */
@Tag("context")
class ChatRuntimeDiscoveryTest {
    @Test
    void actualNativeHandlersAndStoreAreDiscoveredExactlyOnce() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChatRuntimeAutoConfiguration.class)
                            .hasSingleBean(com.massimotter.weave.backend.discovery.port.MatrixPlatformContribution.class)
                            .hasSingleBean(ChatDomainFacadeService.class).hasSingleBean(ChatProviderPort.class)
                            .hasSingleBean(NativeChatProviderAdapter.class).hasSingleBean(CanonicalChatStore.class)
                            .hasSingleBean(ChatController.class).hasSingleBean(MatrixClientServerProjectionController.class)
                            .hasSingleBean(MatrixProtocolCoreService.class);
                    assertThat(context.getBean(WorkspaceCapabilityService.class).installedDefinitions())
                            .extracting(value -> value.domainId()).containsExactly("chat");
                    var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                            .getHandlerMethods().keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
                    assertThat(paths).contains("/api/chat/readiness", "/_matrix/client/**");
                    assertThat(paths).noneMatch(path -> path.contains("meeting-capsules") || path.endsWith("/decisions")
                            || path.startsWith("/api/calendar") || path.startsWith("/api/files"));
                    assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication",
                            context.getClassLoader())).isFalse();
                    assertThat(ClassUtils.isPresent("org.apache.opendal.Operator", context.getClassLoader())).isFalse();
                });
    }

    @Test
    void selectedSynapseGraphIsOfflineButRealStartupRejectsMissingTokens() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                        "weave.chat.provider=matrix-synapse", "weave.chat.matrix.internal-base-url=http://127.0.0.1:1",
                        "weave.chat.matrix.server-name=matrix.example.invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChatProviderPort.class)
                            .hasSingleBean(SynapseBackedCanonicalChatAdapter.class)
                            .hasSingleBean(MatrixApplicationServiceSecrets.class)
                            .hasSingleBean(MatrixApplicationServiceReadiness.class)
                            .doesNotHaveBean(NativeChatProviderAdapter.class);
                    assertThatThrownBy(() -> context.getBean(MatrixApplicationServiceReadiness.class).run(null))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("as_token file is not configured");
                });
    }

    @Test
    void nonWebSchemaContextDoesNotConstructServingGraphOrProtocol() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ChatRuntimeAutoConfiguration.class)
                        .doesNotHaveBean(ChatDomainFacadeService.class).doesNotHaveBean(ChatController.class)
                        .doesNotHaveBean(com.massimotter.weave.backend.discovery.port.MatrixPlatformContribution.class)
                        .doesNotHaveBean(ChatProviderPort.class).doesNotHaveBean(MatrixProtocolCoreService.class)
                        .doesNotHaveBean(MatrixApplicationServiceSecrets.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
