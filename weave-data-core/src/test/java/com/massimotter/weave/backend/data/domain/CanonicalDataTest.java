package com.massimotter.weave.backend.data.domain;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Checkpoint;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.LossClass;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ProvenanceKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class CanonicalDataTest {

    @Test
    void exposesEveryBindingLossClass() {
        assertEquals(
                EnumSet.of(
                        LossClass.PORTABLE,
                        LossClass.LOSSY,
                        LossClass.UNSUPPORTED,
                        LossClass.MANUAL_REVIEW,
                        LossClass.VENDOR_LOCKED,
                        LossClass.ARCHIVE_ONLY),
                EnumSet.allOf(LossClass.class));
    }

    @Test
    void rejectsBlankCanonicalIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectId(Domain.FILES, "  "));
    }

    @Test
    void requiresSourceReferenceForImportedProvenance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Provenance(ProvenanceKind.IMPORTED, null, Instant.EPOCH));
    }

    @Test
    void checkpointRequiresCursorUntilComplete() {
        assertThrows(IllegalArgumentException.class, () -> new Checkpoint(1, null, false));
        assertEquals(new Checkpoint(1, null, true), new Checkpoint(1, null, true));
    }
}
