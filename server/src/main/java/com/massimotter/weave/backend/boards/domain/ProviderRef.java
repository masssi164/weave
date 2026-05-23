package com.massimotter.weave.backend.boards.domain;

import java.net.URI;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record ProviderRef(
        ProviderKind provider,
        String externalId,
        URI externalUrl,
        String version,
        String etag,
        Instant lastSyncedAt) {

    public ProviderRef {
        provider = requireNonNull(provider, "provider must not be null");
        externalId = BoardsContract.requireText(externalId, "externalId");
        externalUrl = BoardsContract.optionalUri(externalUrl);
        lastSyncedAt = BoardsContract.optionalInstant(lastSyncedAt);
    }

    public static ProviderRef of(ProviderKind provider, String externalId) {
        return new ProviderRef(provider, externalId, null, null, null, null);
    }
}
