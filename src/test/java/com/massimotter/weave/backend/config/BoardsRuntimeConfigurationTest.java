package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.boards.local.LocalPreviewBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoardsRuntimeConfigurationTest {

    private final BoardsRuntimeConfiguration configuration = new BoardsRuntimeConfiguration();

    @Test
    void defaultsToLocalPreviewRepositoryForHiddenBoardsFacade() {
        var repository = configuration.boardsRepository(
                "local-preview",
                false,
                false,
                false,
                false,
                false,
                "disabled");

        assertThat(repository).isInstanceOf(LocalPreviewBoardsRepository.class);
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
}
