package com.massimotter.weave.core.transfer;

/** Southbound anti-corruption port that applies canonical batches to a provider target. */
public interface ProviderTargetConnector<T> {

    ConnectorDescriptor descriptor();

    TargetPreflight preflight(CanonicalTransferBatch<T> batch);

    TargetApplyReceipt apply(CanonicalTransferBatch<T> batch, String idempotencyKey);

    TargetVerification verify(TargetApplyReceipt receipt);
}
