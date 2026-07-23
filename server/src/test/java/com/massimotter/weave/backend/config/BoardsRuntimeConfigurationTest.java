package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditEventJpaRepository;
import com.massimotter.weave.backend.audit.FileAuditEventPublisher;
import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BoardsRuntimeConfigurationTest {

    private final BoardsRuntimeConfiguration configuration = new BoardsRuntimeConfiguration();

    @Test
    void defaultsToLocalWorkspaceRepositoryForBoardsFacade() {
        var repository = configuration.boardsRepository(
                "local-workspace", false, false, false, false, false, "disabled");

        assertThat(repository).isInstanceOf(LocalWorkspaceBoardsRepository.class);
    }

    @Test
    void openProjectProviderSelectionStillReturnsFailClosedReadSyncRepository() {
        var repository = configuration.boardsRepository(
                "openproject", false, false, false, false, false, "disabled");

        assertThat(repository).isInstanceOf(OpenProjectBoardsRepository.class);
        assertThat(repository.capabilities().enabled()).isFalse();
    }

    @Test
    void boardsConfigurationDoesNotCreateAPersistenceFallback() {
        new ApplicationContextRunner()
                .withUserConfiguration(BoardsRuntimeConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(AuditEventPublisher.class));
    }

    @Test
    void persistenceCompositionPublishesOnlyTheJpaAuditAuthority() {
        new ApplicationContextRunner()
                .withUserConfiguration(JpaAuditEventPublisher.class)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean(
                        AuditEventJpaRepository.class,
                        () -> mock(AuditEventJpaRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventPublisher.class);
                    assertThat(context).hasSingleBean(JpaAuditEventPublisher.class);
                    assertThat(context).doesNotHaveBean(FileAuditEventPublisher.class);
                });
    }
}
