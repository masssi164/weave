package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRuntimeGate;
import com.massimotter.weave.backend.boards.port.BoardsRuntimeGuard;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BoardsRuntimeConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    BoardsRuntimeGuard boardsRuntimeGuard(
            @Value("${weave.boards.workspace.runtime-enabled:${weave.boards.runtime-enabled:false}}") boolean enabled) {
        return new BoardsRuntimeGuard(enabled);
    }


    @Bean
    BoardsRepository boardsRepository(
            @Value("${weave.boards.workspace.provider:${weave.boards.provider:local-workspace}}") String provider,
            @Value("${weave.boards.openproject.provider-runtime-enabled:false}") boolean openProjectRuntimeEnabled,
            @Value("${weave.boards.openproject.read-sync-enabled:false}") boolean openProjectReadSyncEnabled,
            @Value("${weave.boards.openproject.context-authorization-enabled:false}") boolean openProjectContextAuthorizationEnabled,
            @Value("${weave.boards.openproject.audit-consent-enabled:false}") boolean openProjectAuditConsentEnabled,
            @Value("${weave.boards.openproject.provider-writes-enabled:false}") boolean openProjectProviderWritesEnabled,
            @Value("${weave.boards.openproject.auth-mode:disabled}") String openProjectAuthMode,
            @Value("${weave.boards.openproject.base-url:}") String openProjectBaseUrl,
            @Value("${weave.boards.openproject.api-token:}") String openProjectApiToken,
            RestClient.Builder restClientBuilder) {
        if ("openproject".equalsIgnoreCase(provider)) {
            return new OpenProjectBoardsRepository(
                    new OpenProjectBoardsRuntimeGate(
                            openProjectRuntimeEnabled,
                            openProjectReadSyncEnabled,
                            openProjectContextAuthorizationEnabled,
                            openProjectAuditConsentEnabled,
                            openProjectProviderWritesEnabled,
                            openProjectAuthMode),
                    uriOrNull(openProjectBaseUrl),
                    openProjectApiToken,
                    restClientBuilder);
        }
        return new LocalWorkspaceBoardsRepository();
    }

    // Retained for direct unit tests that instantiate this configuration without a Spring RestClient.Builder bean.
    BoardsRepository boardsRepository(
            String provider,
            boolean openProjectRuntimeEnabled,
            boolean openProjectReadSyncEnabled,
            boolean openProjectContextAuthorizationEnabled,
            boolean openProjectAuditConsentEnabled,
            boolean openProjectProviderWritesEnabled,
            String openProjectAuthMode) {
        return boardsRepository(
                provider,
                openProjectRuntimeEnabled,
                openProjectReadSyncEnabled,
                openProjectContextAuthorizationEnabled,
                openProjectAuditConsentEnabled,
                openProjectProviderWritesEnabled,
                openProjectAuthMode,
                "",
                "",
                RestClient.builder());
    }

    private URI uriOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return URI.create(value.trim());
    }
}
