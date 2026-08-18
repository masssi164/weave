package com.massimotter.weave.core.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferAccountingTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void allSixFidelityClassesAreMachineCountedWithNoSilentDrop() {
        List<CanonicalTransferObject<String>> objects = EnumSet
                .allOf(FidelityFinding.Classification.class)
                .stream()
                .map(TransferAccountingTest::objectFor)
                .toList();
        TransferCheckpoint checkpoint = TransferCheckpoint
                .initial(UUID.fromString("20000000-0000-0000-0000-000000000001"),
                        CanonicalObjectRef.Domain.FILES,
                        NOW)
                .advance(null, true, NOW.plusSeconds(1));
        CanonicalTransferBatch<String> batch = CanonicalTransferBatch.create(
                "transfer-v1",
                "files-v1",
                "provider-a-v1",
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                checkpoint,
                objects);

        Map<CanonicalObjectRef, Set<String>> expectedFields = new HashMap<>();
        for (CanonicalTransferObject<String> object : objects) {
            expectedFields.put(object.reference(), Set.of("providerField"));
        }

        TransferAccounting.Report report = TransferAccounting.verifyExpectedFields(
                expectedFields, batch);

        assertEquals(6, report.outcomes().size());
        for (FidelityFinding.Classification classification
                : FidelityFinding.Classification.values()) {
            assertEquals(1, report.count(classification));
        }
    }

    @Test
    void missingOrUnexpectedFieldFailsClosed() {
        CanonicalTransferObject<String> object = objectFor(
                FidelityFinding.Classification.PORTABLE);
        TransferCheckpoint checkpoint = TransferCheckpoint
                .initial(UUID.fromString("20000000-0000-0000-0000-000000000002"),
                        CanonicalObjectRef.Domain.FILES,
                        NOW)
                .advance(null, true, NOW.plusSeconds(1));
        CanonicalTransferBatch<String> batch = CanonicalTransferBatch.create(
                "transfer-v1",
                "files-v1",
                "provider-a-v1",
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                checkpoint,
                List.of(object));

        Map<CanonicalObjectRef, Set<String>> incompleteExpectation = Map.of(
                object.reference(), Set.of("anotherField"));

        assertThrows(IllegalStateException.class, () ->
                TransferAccounting.verifyExpectedFields(incompleteExpectation, batch));
    }

    private static CanonicalTransferObject<String> objectFor(
            FidelityFinding.Classification classification) {
        String suffix = classification.name().toLowerCase(java.util.Locale.ROOT);
        CanonicalObjectRef reference = new CanonicalObjectRef(
                "org-1",
                CanonicalObjectRef.Domain.FILES,
                "file",
                "file-" + suffix,
                "files-v1",
                1,
                CanonicalObjectRef.Lifecycle.ACTIVE);
        ArchiveReference archive = classification == FidelityFinding.Classification.ARCHIVE_ONLY
                ? new ArchiveReference(
                        "archive-" + suffix,
                        "application/octet-stream",
                        sha256("archive-" + suffix),
                        suffix.length())
                : null;
        FidelityFinding finding = new FidelityFinding(
                "providerField",
                classification,
                "reason-" + suffix,
                archive);
        return new CanonicalTransferObject<>(
                reference,
                "payload-" + suffix,
                sha256("payload-" + suffix),
                Provenance.imported(NOW, "binding-a"),
                List.of(),
                List.of(finding));
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
