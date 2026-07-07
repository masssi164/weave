package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.FileAuditEventPublisher;
import com.massimotter.weave.backend.audit.JdbcAuditEventPublisher;
import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
    void auditEventsDefaultToFileBackedPublisher() {
        contextRunner().run(context -> {
            assertThat(context.getBeansOfType(AuditEventPublisher.class))
                    .hasSize(1)
                    .containsValue(context.getBean(FileAuditEventPublisher.class));
            assertThat(context).doesNotHaveBean(JdbcAuditEventPublisher.class);
        });
    }

    @Test
    void jdbcAuditStorageModeDisablesFilePublisherAndEnablesJdbcPublisher() {
        contextRunner()
                .withPropertyValues(
                        "weave.audit.events.storage.mode=jdbc",
                        "weave.persistence.jdbc.url=jdbc:h2:mem:" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1",
                        "weave.persistence.jdbc.username=sa",
                        "weave.persistence.jdbc.password=",
                        "weave.persistence.jdbc.driver-class-name=org.h2.Driver")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventPublisher.class);
                    assertThat(context).hasSingleBean(JdbcAuditEventPublisher.class);
                    assertThat(context).doesNotHaveBean(FileAuditEventPublisher.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(BoardsRuntimeConfiguration.class, WeavePersistenceConfiguration.class)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
    }
}
