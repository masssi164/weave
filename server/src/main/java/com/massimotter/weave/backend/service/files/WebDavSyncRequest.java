package com.massimotter.weave.backend.service.files;

import java.util.List;
import java.util.Objects;
import javax.xml.namespace.QName;

/** Closed, bounded representation of one supported RFC 6578 sync-collection request. */
public record WebDavSyncRequest(
        String collectionPath,
        String syncToken,
        SyncLevel syncLevel,
        List<QName> properties,
        int limit,
        boolean clientLimitSupplied) {

    public static final int MAXIMUM_PROPERTIES = 16;
    public static final int MAXIMUM_LIMIT = 100;

    public WebDavSyncRequest {
        collectionPath = requireCollectionPath(collectionPath);
        syncToken = Objects.requireNonNull(syncToken, "syncToken");
        if (syncToken.length() > 4_096 || syncToken.chars().anyMatch(WebDavSyncRequest::control)) {
            throw new IllegalArgumentException("syncToken exceeds the supported syntax bound");
        }
        syncLevel = Objects.requireNonNull(syncLevel, "syncLevel");
        properties = List.copyOf(properties == null ? List.of() : properties);
        if (properties.size() > MAXIMUM_PROPERTIES || properties.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("properties exceed the supported bound");
        }
        for (QName property : properties) {
            if (property.getLocalPart().isBlank()) {
                throw new IllegalArgumentException("properties require a local name");
            }
        }
        if (limit < 0 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("limit must be between zero and 100");
        }
    }

    public boolean initial() {
        return syncToken.isEmpty();
    }

    public enum SyncLevel {
        ONE,
        INFINITE
    }

    private static boolean control(int character) {
        return character < 0x20 && character != '\t' && character != '\n' && character != '\r';
    }

    private static String requireCollectionPath(String path) {
        Objects.requireNonNull(path, "collectionPath");
        if (path.isBlank() || path.charAt(0) != '/') {
            throw new IllegalArgumentException("collectionPath must be an absolute product path");
        }
        return FilePathCodec.normalizeProductPath(path);
    }
}
