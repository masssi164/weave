package com.massimotter.weave.backend.chat.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ChatE2eProofProviderConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ChatE2eCallbackReplayController.class,
                    ChatE2eProviderProofController.class,
                    MatrixE2eePathProofController.class)
            .withPropertyValues(
                    "weave.chat.e2e-proof.enabled=true",
                    "weave.chat.provider=weave-native");

    @Test
    void doesNotStartMatrixProofControllersForNativeChat() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ChatE2eCallbackReplayController.class);
            assertThat(context).doesNotHaveBean(ChatE2eProviderProofController.class);
            assertThat(context).doesNotHaveBean(MatrixE2eePathProofController.class);
        });
    }
}
