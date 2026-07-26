package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseCompatibilityProfile;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import com.massimotter.weave.backend.chat.store.CanonicalChatJpaAuthority;
import com.massimotter.weave.backend.chat.store.JpaCanonicalChatStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatRuntimeProperties.class)
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public class ChatRuntimeConfiguration {

    @Bean
    MatrixApplicationServiceSecrets matrixApplicationServiceSecrets(ChatRuntimeProperties properties) {
        return new MatrixApplicationServiceSecrets(properties.matrix());
    }

    @Bean
    CanonicalChatStore canonicalChatStore(
            ChatRuntimeProperties properties,
            CanonicalChatJpaAuthority jpa,
            ObjectMapper objectMapper) {
        requireJpa(properties);
        return new JpaCanonicalChatStore(
                jpa,
                objectMapper,
                Clock.systemUTC(),
                MatrixSynapseCompatibilityProfile.pinned());
    }

    @Bean
    MatrixSynapseChatSouthboundAdapter matrixSynapseChatSouthboundAdapter(
            ChatRuntimeProperties properties,
            MatrixApplicationServiceSecrets secrets,
            ObjectMapper objectMapper) {
        return new MatrixSynapseChatSouthboundAdapter(
                properties.matrix(), secrets, objectMapper, Clock.systemUTC());
    }

    @Bean
    ChatProviderPort synapseBackedCanonicalChatAdapter(
            CanonicalChatStore canonicalChatStore,
            MatrixSynapseChatSouthboundAdapter provider,
            ChatRuntimeProperties properties,
            ObjectMapper objectMapper) {
        return new SynapseBackedCanonicalChatAdapter(
                canonicalChatStore, provider, properties.matrix(), objectMapper, Clock.systemUTC());
    }

    private void requireJpa(ChatRuntimeProperties properties) {
        if (!properties.jpaSelected()) {
            throw new IllegalStateException("Matrix/Synapse Chat requires WEAVE_CHAT_STORAGE_MODE=jpa.");
        }
    }
}
