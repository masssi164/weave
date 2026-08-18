package com.massimotter.weave.core.transfer;

/** Southbound anti-corruption port that reads provider state as typed canonical objects. */
public interface ProviderSourceConnector<T> {

    ConnectorDescriptor descriptor();

    SourcePage<T> read(TransferScope scope, TransferCheckpoint checkpoint);
}
