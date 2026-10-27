package com.massimotter.weave.backend.boards.config;

import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.discovery.provider.RuntimeProviderStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BoardsProviderRegistryConfiguration {

    @Bean
    ProviderPort boardsProviderRegistrySeam(BoardsRepository boardsRepository) {
        return boardsProviderRegistrySeamFor(boardsRepository);
    }

    ProviderPort boardsProviderRegistrySeamFor(BoardsRepository runtime) {
        BoardProviderCapabilities capabilities = runtime.capabilities();
        String providerKey = boardsProviderKey(capabilities.provider());
        return RuntimeProviderStatus.fixed(
                ProviderModule.BOARDS,
                providerKey,
                capabilities.enabled(),
                "The selected Boards adapter is bound behind the canonical Boards repository and workspace facade.",
                capabilities.supported().stream()
                        .map(capability -> capability.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                capabilities.unsupported().stream()
                        .map(capability -> capability.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                List.of("local-workspace", "openproject-primary", "microsoft-planner", "jira", "vikunja", "nextcloud-deck"),
                capabilities.enabled() ? ProviderRealityLevel.CONFIGURED : ProviderRealityLevel.CONTRACT_ONLY,
                Map.of(
                        "canonicalDomain", "boards-tasks",
                        "runtimeAdapterKind", providerKey,
                        "facade", "/api/boards/workspace",
                        "supportSafeSummary", capabilities.supportSafeSummary()));
    }

    private String boardsProviderKey(ProviderKind provider) {
        return switch (provider) {
            case IN_MEMORY -> "local-workspace";
            case OPEN_PROJECT -> "openproject-primary";
            default -> provider.contractName();
        };
    }
}
