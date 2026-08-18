package com.massimotter.weave.core.transfer;

import java.time.Instant;
import java.util.UUID;

/** Resumable source position. Persistence is supplied by an application adapter. */
public record TransferCheckpoint(
        UUID transferRunId,
        CanonicalObjectRef.Domain domain,
        long sequence,
        String sourceCursor,
        boolean complete,
        Instant updatedAt) {

    public TransferCheckpoint {
        transferRunId = TransferValidation.require(transferRunId, "transferRunId");
        domain = TransferValidation.require(domain, "domain");
        sourceCursor = TransferValidation.optionalText(sourceCursor);
        updatedAt = TransferValidation.require(updatedAt, "updatedAt");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (!complete && sequence > 0 && sourceCursor == null) {
            throw new IllegalArgumentException("an incomplete advanced checkpoint requires sourceCursor");
        }
    }

    public static TransferCheckpoint initial(
            UUID transferRunId,
            CanonicalObjectRef.Domain domain,
            Instant now) {
        return new TransferCheckpoint(transferRunId, domain, 0, null, false, now);
    }

    public TransferCheckpoint advance(String nextCursor, boolean nextComplete, Instant now) {
        if (complete) {
            throw new IllegalStateException("a completed checkpoint cannot advance");
        }
        return new TransferCheckpoint(
                transferRunId,
                domain,
                sequence + 1,
                nextCursor,
                nextComplete,
                now);
    }
}
