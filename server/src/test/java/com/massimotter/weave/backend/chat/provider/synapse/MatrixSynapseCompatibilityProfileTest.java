package com.massimotter.weave.backend.chat.provider.synapse;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixSynapseCompatibilityProfileTest {

    @Test
    void pinnedAndCandidateTargetsExerciseTheSameInProcessClassifierFixture() {
        List<MatrixSynapseCompatibilityProfile> fixtures =
                MatrixSynapseCompatibilityProfile.classifierFixtureTargets();

        assertThat(fixtures)
                .extracting(MatrixSynapseCompatibilityProfile::synapseVersion)
                .containsExactly("1.136.0", "1.156.0");
        assertThat(fixtures).allSatisfy(profile -> {
            assertThat(profile.matrixRoomVersion()).isEqualTo("10");
            assertThat(profile.applicationServiceRegistrationProfile())
                    .isEqualTo("exclusive-user-alias-namespaces-rooms-empty-receive-ephemeral-false-v1");
            assertThat(profile.classifierVersion()).isEqualTo("matrix-synapse-state-v1");
            assertThat(profile.semanticFingerprintVersion()).isEqualTo("matrix-as-event-set-v1");
            assertThat(profile.classify("m.room.canonical_alias", true))
                    .isEqualTo(MatrixSynapseCompatibilityProfile.StateClassification.SUPPORTED_IGNORED);
            assertThat(profile.classify("org.example.future_state", true))
                    .isEqualTo(MatrixSynapseCompatibilityProfile.StateClassification.UNKNOWN_RECOVERABLE);
            assertThat(profile.classify("m.room.canonical_alias", false))
                    .isEqualTo(MatrixSynapseCompatibilityProfile.StateClassification.KNOWN_STATE_KEY_MISSING);
            assertThat(profile.classify("org.example.future_state", false))
                    .isEqualTo(MatrixSynapseCompatibilityProfile.StateClassification.NOT_STATE);
        });
    }

    @Test
    void nextClassifierCanReclassifyOneStateWithoutChangingTheProviderPin() {
        MatrixSynapseCompatibilityProfile pinned = MatrixSynapseCompatibilityProfile.pinned();
        MatrixSynapseCompatibilityProfile next = pinned.withReclassifiedState(
                "matrix-synapse-state-v2", "org.example.future_state");

        assertThat(next.synapseVersion()).isEqualTo(pinned.synapseVersion());
        assertThat(next.semanticFingerprintVersion()).isEqualTo(pinned.semanticFingerprintVersion());
        assertThat(next.classifierVersion()).isEqualTo("matrix-synapse-state-v2");
        assertThat(next.classify("org.example.future_state", true))
                .isEqualTo(MatrixSynapseCompatibilityProfile.StateClassification.SUPPORTED_IGNORED);
    }
}
