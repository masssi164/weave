package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseCompatibilityProfile;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseChatSouthboundAdapter;
import com.massimotter.weave.backend.chat.provider.synapse.SynapseBackedCanonicalChatAdapter;
import com.massimotter.weave.backend.chat.store.JdbcCanonicalChatStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatRuntimeProperties.class)
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public class ChatRuntimeConfiguration {

    @Bean
    MatrixApplicationServiceSecrets matrixApplicationServiceSecrets(ChatRuntimeProperties properties) {
        requireJdbc(properties);
        return new MatrixApplicationServiceSecrets(properties.matrix());
    }

    @Bean
    CanonicalChatStore canonicalChatStore(
            ChatRuntimeProperties properties,
            JdbcTemplate weaveJdbcTemplate,
            ObjectMapper objectMapper) {
        requireJdbc(properties);
        return new JdbcCanonicalChatStore(
                weaveJdbcTemplate,
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

    private void requireJdbc(ChatRuntimeProperties properties) {
        if (!properties.jdbcSelected()) {
            throw new IllegalStateException("Matrix/Synapse Chat requires WEAVE_CHAT_STORAGE_MODE=jdbc.");
        }
    }
}
