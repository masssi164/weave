package com.massimotter.weave.backend.chat.config;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.RuntimeProviderStatus;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatProviderRegistryConfiguration {

    @Bean
    ProviderPort chatProviderRegistrySeam(ObjectProvider<ChatProviderPort> chatProviderPort) {
        ChatProviderPort runtime = chatProviderPort.getIfAvailable();
        if (runtime != null) {
            return chatProviderRegistrySeamFor(runtime);
        }
        return StaticProviderPort.pending(
                ProviderModule.MATRIX,
                "weave-native",
                "No Chat runtime adapter is bound; the canonical Chat and Matrix protocol facades remain fail-closed.",
                Set.of("workspace-room-readiness", "message-sync-readiness", "e2ee-status-readiness", "homeserver-discovery"),
                Set.of("room-key-export", "raw-homeserver-errors", "direct-flutter-admin-api", "credential-exposure"),
                List.of("weave-native", "matrix-synapse", "synapse-homeserver", "slack", "microsoft-teams"),
                Map.of("canonicalDomain", "chat", "facade", "/_matrix/client", "mediaCallsCovered", false));
    }

    ProviderPort chatProviderRegistrySeamFor(ChatProviderPort runtime) {
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.MATRIX,
                runtime.providerKey(),
                runtime.configured(),
                runtime.conformanceProfile(),
                "The selected Chat adapter is bound behind the canonical Chat port; runtime reachability is reported by cached capability health.",
                List.of("weave-native", "matrix-synapse", "synapse-homeserver", "slack", "microsoft-teams"));
    }
}
