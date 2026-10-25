package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import com.massimotter.weave.backend.chat.provider.weave.NativeChatProviderAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class ChatRuntimeConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingProviderSelectionComposesNativeWithoutMatrixAuthority() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChatProviderPort.class);
            assertThat(context.getBean(ChatProviderPort.class))
                    .isInstanceOf(NativeChatProviderAdapter.class);
            assertThat(context).doesNotHaveBean(MatrixApplicationServiceSecrets.class);
            assertThat(context).doesNotHaveBean(MatrixSynapseChatSouthboundAdapter.class);
        });
    }

    @Test
    void explicitMatrixSelectionPreservesOptionalSouthboundComposition() throws IOException {
        Path asToken = temporaryDirectory.resolve("as-token");
        Path hsToken = temporaryDirectory.resolve("hs-token");
        Files.writeString(asToken, "matrix-as-token-value-1234");
        Files.writeString(hsToken, "matrix-hs-token-value-5678");

        runner()
                .withPropertyValues(
                        "weave.chat.provider=matrix-synapse",
                        "weave.chat.matrix.internal-base-url=http://synapse:8008",
                        "weave.chat.matrix.server-name=matrix.weave.test",
                        "weave.chat.matrix.as-token-file=" + asToken,
                        "weave.chat.matrix.hs-token-file=" + hsToken)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatProviderPort.class);
                    assertThat(context.getBean(ChatProviderPort.class))
                            .isInstanceOf(SynapseBackedCanonicalChatAdapter.class);
                    assertThat(context).hasSingleBean(MatrixApplicationServiceSecrets.class);
                    assertThat(context).hasSingleBean(MatrixSynapseChatSouthboundAdapter.class);
                    assertThat(context).doesNotHaveBean(NativeChatProviderAdapter.class);
                });
    }

    @Test
    void unsupportedProviderSelectionFailsClosed() {
        runner()
                .withPropertyValues("weave.chat.provider=unknown-provider")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ChatRuntimeConfiguration.class)
                .withBean(CanonicalChatStore.class, this::durableStore)
                .withBean(ObjectMapper.class, () ->
                        tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
    }

    private CanonicalChatStore durableStore() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-code-first");
        return store;
    }
}
