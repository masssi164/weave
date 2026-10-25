package com.massimotter.weave.backend.transfer.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.CanonicalTransferItem;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.ObjectMetadata;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;
import com.massimotter.weave.backend.transfer.domain.TransferRun;

/** Deterministic hashes for canonical envelopes and provider idempotency keys. */
public final class CanonicalTransferDigests {
    private CanonicalTransferDigests() {
    }

    public static String aggregate(
            TransferFormatVersion transferFormatVersion,
            String canonicalModelVersion,
            String organizationRef,
            List<? extends CanonicalTransferItem> items,
            List<LossRecord> losses) {
        StringBuilder material = new StringBuilder();
        append(material, Integer.toString(transferFormatVersion.value()));
        append(material, canonicalModelVersion);
        append(material, organizationRef);

        List<CanonicalTransferItem> orderedItems = new ArrayList<>(items);
        orderedItems.sort(Comparator.comparing(item -> item.metadata().id().value()));
        for (CanonicalTransferItem item : orderedItems) {
            ObjectMetadata metadata = item.metadata();
            append(material, metadata.id().value());
            append(material, metadata.domain().name());
            append(material, metadata.objectKind());
            append(material, Long.toString(metadata.revision()));
            append(material, metadata.lifecycle().name());
            append(material, metadata.provenance().name());
            append(material, metadata.observedAt().toString());
            append(material, metadata.payloadDigest());
            List<CanonicalObjectId> dependencies = new ArrayList<>(metadata.dependencies());
            dependencies.sort(Comparator.comparing(CanonicalObjectId::value));
            for (CanonicalObjectId dependency : dependencies) {
                append(material, dependency.value());
            }
            append(material, "end-item");
        }

        List<LossRecord> orderedLosses = new ArrayList<>(losses);
        orderedLosses.sort(Comparator
                .comparing((LossRecord loss) -> loss.objectId().value())
                .thenComparing(LossRecord::field)
                .thenComparing(loss -> loss.classification().name())
                .thenComparing(LossRecord::reason));
        for (LossRecord loss : orderedLosses) {
            append(material, loss.objectId().value());
            append(material, loss.field());
            append(material, loss.classification().name());
            append(material, loss.reason());
        }
        return sha256(material.toString());
    }

    public static String idempotencyKey(TransferRun.Id runId, long batchNumber, String aggregateDigest) {
        if (batchNumber < 1) {
            throw new IllegalArgumentException("batchNumber must be positive");
        }
        StringBuilder material = new StringBuilder();
        append(material, runId.value());
        append(material, Long.toString(batchNumber));
        append(material, aggregateDigest);
        return "weave-transfer:" + sha256(material.toString());
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            throw new IllegalArgumentException("digest material must not contain null values");
        }
        target.append(value.length()).append(':').append(value).append('|');
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
