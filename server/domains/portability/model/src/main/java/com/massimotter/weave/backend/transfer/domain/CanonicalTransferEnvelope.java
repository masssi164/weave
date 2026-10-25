package com.massimotter.weave.backend.transfer.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;

/** Provider-independent export/import envelope. It is not a JPA serialization or provider DTO. */
public record CanonicalTransferEnvelope<T extends CanonicalTransferItem>(
        TransferFormatVersion transferFormatVersion,
        String canonicalModelVersion,
        String organizationRef,
        List<T> items,
        List<LossRecord> losses,
        String aggregateDigest,
        TransferCheckpoint continuation) {

    public CanonicalTransferEnvelope {
        transferFormatVersion = Objects.requireNonNull(transferFormatVersion, "transferFormatVersion must not be null");
        canonicalModelVersion = required(canonicalModelVersion, "canonical model version");
        organizationRef = required(organizationRef, "organization ref");
        items = List.copyOf(items == null ? List.of() : items);
        losses = List.copyOf(losses == null ? List.of() : losses);
        aggregateDigest = required(aggregateDigest, "aggregate digest");
    }

    public Optional<TransferCheckpoint> nextCheckpoint() {
        return Optional.ofNullable(continuation);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
