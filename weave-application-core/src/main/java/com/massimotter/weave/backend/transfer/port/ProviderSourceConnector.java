package com.massimotter.weave.backend.transfer.port;

import java.util.List;
import java.util.Optional;

import com.massimotter.weave.backend.transfer.domain.CanonicalTransferItem;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;

/** Reads provider state into typed canonical values without exposing provider DTOs. */
public interface ProviderSourceConnector<T extends CanonicalTransferItem> {

    SourceBatch<T> read(String organizationRef, Optional<TransferCheckpoint> after);

    record SourceBatch<T extends CanonicalTransferItem>(
            List<T> items,
            List<LossRecord> losses,
            TransferCheckpoint nextCheckpoint,
            boolean complete) {
        public SourceBatch {
            items = List.copyOf(items == null ? List.of() : items);
            losses = List.copyOf(losses == null ? List.of() : losses);
            if (!complete && nextCheckpoint == null) {
                throw new IllegalArgumentException("incomplete source batch requires a next checkpoint");
            }
            if (!complete && items.isEmpty()) {
                throw new IllegalArgumentException("incomplete source batch must make observable progress");
            }
        }
    }
}
