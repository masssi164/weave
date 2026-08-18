package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.chat.provider.weave.NativeChatProviderAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatRuntimeProperties.class)
public class ChatRuntimeConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "weave.chat.provider",
            havingValue = ChatRuntimeProperties.WEAVE_NATIVE_PROVIDER,
            matchIfMissing = true)
    ChatProviderPort nativeChatProviderAdapter(CanonicalChatStore canonicalChatStore) {
        return new NativeChatProviderAdapter(canonicalChatStore, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(
            name = "weave.chat.provider",
            havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
    MatrixApplicationServiceSecrets matrixApplicationServiceSecrets(ChatRuntimeProperties properties) {
        return new MatrixApplicationServiceSecrets(properties.matrix());
    }

    @Bean
    @ConditionalOnProperty(
            name = "weave.chat.provider",
            havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
    MatrixSynapseChatSouthboundAdapter matrixSynapseChatSouthboundAdapter(
            ChatRuntimeProperties properties,
            MatrixApplicationServiceSecrets secrets,
            ObjectMapper objectMapper) {
        return new MatrixSynapseChatSouthboundAdapter(
                properties.matrix(), secrets, objectMapper, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(
            name = "weave.chat.provider",
            havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
    ChatProviderPort synapseBackedCanonicalChatAdapter(
            CanonicalChatStore canonicalChatStore,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatRuntimeProperties properties,
            ObjectMapper objectMapper) {
        return new SynapseBackedCanonicalChatAdapter(
                canonicalChatStore, provider, properties.matrix(), objectMapper, Clock.systemUTC());
    }

}
