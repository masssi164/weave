package com.massimotter.weave.core.transfer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One bounded page emitted by a provider source connector. */
public record SourcePage<T>(
        List<CanonicalTransferObject<T>> objects,
        TransferCheckpoint nextCheckpoint) {

    public SourcePage {
        objects = List.copyOf(TransferValidation.require(objects, "objects"));
        nextCheckpoint = TransferValidation.require(nextCheckpoint, "nextCheckpoint");

        Set<String> objectKeys = new HashSet<>();
        for (CanonicalTransferObject<T> object : objects) {
            TransferValidation.require(object, "object");
            if (object.reference().domain() != nextCheckpoint.domain()) {
                throw new IllegalArgumentException("source page contains a different domain");
            }
            if (!objectKeys.add(object.reference().stableKey())) {
                throw new IllegalArgumentException(
                        "source page contains duplicate object " + object.reference().stableKey());
            }
        }
    }
}
