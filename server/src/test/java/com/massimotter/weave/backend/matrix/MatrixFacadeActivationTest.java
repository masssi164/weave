package com.massimotter.weave.backend.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.controller.MatrixClientServerProjectionController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MatrixFacadeActivationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(
                    MatrixProtocolCoreService.class,
                    MatrixFacadeClientStateService.class,
                    MatrixClientServerProjectionController.class);

    @Test
    void defaultServerContextDoesNotCreateLegacyFacadeOrLoadJni() {
        context.run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).doesNotHaveBean(MatrixProtocolCoreService.class);
            assertThat(result).doesNotHaveBean(MatrixFacadeClientStateService.class);
            assertThat(result).doesNotHaveBean(MatrixClientServerProjectionController.class);
        });
    }
}
