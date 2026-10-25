package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixSynapseCompatibilityProfile;
import com.massimotter.weave.backend.chat.store.CanonicalChatJpaAuthority;
import com.massimotter.weave.backend.chat.store.JpaCanonicalChatStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Composes Weave-owned durable Chat state independently of provider selection. */
@Configuration(proxyBeanMethods = false)
public class CanonicalChatPersistenceConfiguration {

    @Bean
    CanonicalChatStore canonicalChatStore(
            CanonicalChatJpaAuthority jpa,
            ObjectMapper objectMapper) {
        return new JpaCanonicalChatStore(
                jpa,
                objectMapper,
                Clock.systemUTC(),
                MatrixSynapseCompatibilityProfile.pinned());
    }
}
