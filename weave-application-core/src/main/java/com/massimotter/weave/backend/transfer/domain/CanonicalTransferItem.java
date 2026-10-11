package com.massimotter.weave.backend.transfer.domain;

/**
 * Marker contract implemented by domain-owned typed transfer objects.
 * The shared kernel owns metadata and transfer mechanics, not domain payload shape.
 */
public interface CanonicalTransferItem {
    TransferPrimitives.ObjectMetadata metadata();
}
