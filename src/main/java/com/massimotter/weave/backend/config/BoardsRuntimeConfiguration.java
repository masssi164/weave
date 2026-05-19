package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.boards.local.LocalPreviewBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRuntimeGate;
import com.massimotter.weave.backend.boards.port.BoardsPreviewGuard;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BoardsRuntimeConfiguration {

    @Bean
    BoardsPreviewGuard boardsPreviewGuard(
            @Value("${weave.boards.preview.runtime-enabled:false}") boolean enabled) {
        return new BoardsPreviewGuard(enabled);
    }

    @Bean
    BoardsRepository boardsRepository(
            @Value("${weave.boards.preview.provider:local-preview}") String provider,
            @Value("${weave.boards.openproject.provider-runtime-enabled:false}") boolean openProjectRuntimeEnabled,
            @Value("${weave.boards.openproject.read-sync-enabled:false}") boolean openProjectReadSyncEnabled,
            @Value("${weave.boards.openproject.context-authorization-enabled:false}") boolean openProjectContextAuthorizationEnabled,
            @Value("${weave.boards.openproject.audit-consent-enabled:false}") boolean openProjectAuditConsentEnabled,
            @Value("${weave.boards.openproject.provider-writes-enabled:false}") boolean openProjectProviderWritesEnabled,
            @Value("${weave.boards.openproject.auth-mode:disabled}") String openProjectAuthMode) {
        if ("openproject".equalsIgnoreCase(provider)) {
            return new OpenProjectBoardsRepository(new OpenProjectBoardsRuntimeGate(
                    openProjectRuntimeEnabled,
                    openProjectReadSyncEnabled,
                    openProjectContextAuthorizationEnabled,
                    openProjectAuditConsentEnabled,
                    openProjectProviderWritesEnabled,
                    openProjectAuthMode));
        }
        return new LocalPreviewBoardsRepository();
    }
}
