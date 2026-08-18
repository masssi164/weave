package com.massimotter.weave.core.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalTransferBatchTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void aggregateDigestIsIndependentOfObjectOrderAndSourceAdapter() {
        CanonicalTransferObject<String> first = object("file-1", "alpha", List.of());
        CanonicalTransferObject<String> second = object(
                "file-2", "beta", List.of(first.reference()));
        TransferCheckpoint checkpoint = TransferCheckpoint
                .initial(UUID.fromString("10000000-0000-0000-0000-000000000001"),
                        CanonicalObjectRef.Domain.FILES,
                        NOW)
                .advance("cursor-1", false, NOW.plusSeconds(1));

        CanonicalTransferBatch<String> a = CanonicalTransferBatch.create(
                "transfer-v1",
                "files-v1",
                "provider-a-v3",
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                checkpoint,
                List.of(first, second));
        CanonicalTransferBatch<String> b = CanonicalTransferBatch.create(
                "transfer-v1",
                "files-v1",
                "provider-b-v8",
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                checkpoint,
                List.of(second, first));

        assertEquals(a.aggregateSha256(), b.aggregateSha256());
        assertEquals("provider-a-v3", a.sourceAdapterProfileVersion());
        assertEquals("provider-b-v8", b.sourceAdapterProfileVersion());
        assertEquals("transfer-v1", a.transferFormatVersion());
        assertEquals("files-v1", a.canonicalModelVersion());
    }

    @Test
    void duplicateCanonicalObjectsAreRejected() {
        CanonicalTransferObject<String> object = object("file-1", "alpha", List.of());
        TransferCheckpoint checkpoint = TransferCheckpoint
                .initial(UUID.fromString("10000000-0000-0000-0000-000000000002"),
                        CanonicalObjectRef.Domain.FILES,
                        NOW)
                .advance(null, true, NOW.plusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> CanonicalTransferBatch.create(
                "transfer-v1",
                "files-v1",
                "provider-a-v1",
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                checkpoint,
                List.of(object, object)));
    }

    @Test
    void suppliedDigestCannotLieAboutCanonicalContent() {
        CanonicalTransferObject<String> object = object("file-1", "alpha", List.of());
        TransferCheckpoint checkpoint = TransferCheckpoint
                .initial(UUID.fromString("10000000-0000-0000-0000-000000000003"),
                        CanonicalObjectRef.Domain.FILES,
                        NOW)
                .advance(null, true, NOW.plusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> new CanonicalTransferBatch<>(
                "transfer-v1",
                "files-v1",
                "provider-a-v1",
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                checkpoint.sequence(),
                checkpoint,
                List.of(object),
                "0".repeat(64)));
    }

    private static CanonicalTransferObject<String> object(
            String objectId,
            String payload,
            List<CanonicalObjectRef> dependencies) {
        CanonicalObjectRef reference = new CanonicalObjectRef(
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                "file",
                objectId,
                "files-v1",
                1,
                CanonicalObjectRef.Lifecycle.ACTIVE);
        return new CanonicalTransferObject<>(
                reference,
                payload,
                sha256(payload),
                Provenance.nativeObject(NOW),
                dependencies,
                List.of(new FidelityFinding(
                        "content",
                        FidelityFinding.Classification.PORTABLE,
                        "equivalent-content",
                        null)));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
