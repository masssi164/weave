package com.massimotter.weave.backend.boards.config;

import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.discovery.provider.RuntimeProviderStatus;
import com.massimotter.weave.backend.discovery.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BoardsProviderRegistryConfiguration {

    @Bean
    ProviderPort boardsProviderRegistrySeam(ObjectProvider<BoardsRepository> boardsRepository) {
        BoardsRepository runtime = boardsRepository.getIfAvailable();
        if (runtime == null) {
            return StaticProviderPort.pending(
                    ProviderModule.BOARDS,
                    "local-workspace",
                    "No Boards runtime adapter is bound; the canonical Boards facade remains fail-closed.",
                    Set.of("project-list", "board-list", "task-list", "task-create", "task-move", "task-complete"),
                    Set.of("direct-member-provider-api", "provider-writes-without-audit", "raw-provider-errors"),
                    List.of("local-workspace", "openproject-primary", "microsoft-planner", "jira", "vikunja", "nextcloud-deck"),
                    Map.of("runtimeBindingObserved", false, "facade", "/api/boards/workspace"));
        }
        return boardsProviderRegistrySeamFor(runtime);
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
