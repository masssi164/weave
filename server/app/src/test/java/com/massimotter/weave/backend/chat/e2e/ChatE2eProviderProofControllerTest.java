package com.massimotter.weave.backend.chat.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatE2eProviderProofControllerTest {

    private static final String FIRST = "a".repeat(64);
    private static final String SECOND = "b".repeat(64);
    private static final String THIRD = "c".repeat(64);
    private static final String FOURTH = "d".repeat(64);

    @Test
    void acceptsPhaseAwarePreRetryAndPostRetryCorrelationSets() {
        assertThat(ChatE2eProviderProofController.requireCorrelationHashes(List.of(FIRST, SECOND)))
                .containsExactly(FIRST, SECOND);
        assertThat(ChatE2eProviderProofController.requireCorrelationHashes(List.of(FIRST, SECOND, THIRD)))
                .containsExactly(FIRST, SECOND, THIRD);
    }

    @Test
    void rejectsCorrelationSetsThatCannotDescribeEitherProofPhase() {
        assertThatThrownBy(() -> ChatE2eProviderProofController.requireCorrelationHashes(List.of(FIRST)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatE2eProviderProofController.requireCorrelationHashes(
                List.of(FIRST, SECOND, THIRD, FOURTH)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatE2eProviderProofController.requireCorrelationHashes(
                List.of(FIRST, SECOND, SECOND)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
