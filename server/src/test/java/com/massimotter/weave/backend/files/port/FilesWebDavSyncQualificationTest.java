package com.massimotter.weave.backend.files.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.port.FilesWebDavSyncQualification.Proof;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FilesWebDavSyncQualificationTest {

    private static final Instant OBSERVED = Instant.parse("2026-08-23T19:00:00Z");

    @Test
    void blockedAndPartialEvidenceNeverQualify() {
        assertThat(FilesWebDavSyncQualification.blocked(OBSERVED)
                        .qualifiedAt(OBSERVED.plusSeconds(1)))
                .isFalse();

        FilesWebDavSyncQualification partial = FilesWebDavSyncQualification.verifiedNative(
                OBSERVED,
                EnumSet.complementOf(EnumSet.of(Proof.IF_STATE_TOKEN)),
                FilesWebDavSyncQualification.EVIDENCE_REF);
        assertThat(partial.qualifiedAt(OBSERVED.plusSeconds(1))).isFalse();
    }

    @Test
    void allProofsQualifyOnlyInsideTheFreshnessWindow() {
        FilesWebDavSyncQualification qualification = FilesWebDavSyncQualification.verifiedNative(
                OBSERVED,
                EnumSet.allOf(Proof.class),
                FilesWebDavSyncQualification.EVIDENCE_REF);

        assertThat(qualification.qualifiedAt(OBSERVED.minusNanos(1))).isFalse();
        assertThat(qualification.qualifiedAt(OBSERVED)).isTrue();
        assertThat(qualification.qualifiedAt(OBSERVED.plusSeconds(59))).isTrue();
        assertThat(qualification.qualifiedAt(OBSERVED.plusSeconds(60))).isFalse();
    }

    @Test
    void proofAndEvidenceInputsAreImmutableAndFailClosed() {
        EnumSet<Proof> proofs = EnumSet.allOf(Proof.class);
        FilesWebDavSyncQualification qualification = FilesWebDavSyncQualification.verifiedNative(
                OBSERVED, proofs, FilesWebDavSyncQualification.EVIDENCE_REF);
        proofs.clear();

        assertThat(qualification.proofs()).containsExactlyInAnyOrder(Proof.values());
        assertThatThrownBy(() -> qualification.proofs().add(Proof.IF_STATE_TOKEN))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> FilesWebDavSyncQualification.verifiedNative(
                        OBSERVED, Set.of(Proof.INITIAL_SYNC), " padded "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
