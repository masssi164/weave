package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.audit.FileAuditEventPublisher;
import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BoardsRuntimeConfigurationTest {

    private final BoardsRuntimeConfiguration configuration = new BoardsRuntimeConfiguration();

    @Test
    void defaultsToLocalWorkspaceRepositoryForBoardsFacade() {
        var repository = configuration.boardsRepository(
                "local-workspace",
                false,
                false,
                false,
                false,
                false,
                "disabled");

        assertThat(repository).isInstanceOf(LocalWorkspaceBoardsRepository.class);
    }

    @Test
    void openProjectProviderSelectionStillReturnsFailClosedReadSyncRepository() {
        var repository = configuration.boardsRepository(
                "openproject",
                false,
                false,
                false,
                false,
                false,
                "disabled");

        assertThat(repository).isInstanceOf(OpenProjectBoardsRepository.class);
        assertThat(repository.capabilities().enabled()).isFalse();
    }

    @Test
    void boardsConfigurationDoesNotCreateAPersistenceFallback() {
        new ApplicationContextRunner()
                .withUserConfiguration(BoardsRuntimeConfiguration.class)
                .withPropertyValues("weave.audit.events.storage.mode=jpa")
                .withBean(RestClient.Builder.class, RestClient::builder)
                .run(context -> assertThat(context).doesNotHaveBean(AuditEventPublisher.class));
    }

    @Test
    void persistenceCompositionPublishesOnlyTheJpaAuditAuthority() {
        new ApplicationContextRunner()
                .withUserConfiguration(WeavePersistenceConfiguration.class)
                .withPropertyValues("weave.audit.events.storage.mode=jpa")
                .withBean(AuditEventJpaRepository.class, () -> mock(AuditEventJpaRepository.class))
                .withBean(ObjectMapper.class, () -> tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build())
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventPublisher.class);
                    assertThat(context).hasSingleBean(JpaAuditEventPublisher.class);
                    assertThat(context).doesNotHaveBean(FileAuditEventPublisher.class);
                });
    }
}
