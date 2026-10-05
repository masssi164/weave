package com.massimotter.weave.backend.files.domain;

import java.time.Instant;
import java.util.Objects;

/** Weave-owned member visibility and Space relationship for one Files object. */
public record FilesUserResource(
        String organizationRef,
        String fileId,
        long bindingRevision,
        String spaceRef,
        String parentFileId,
        String path,
        FilesDomain.Kind kind,
        String ownerPrincipalRef,
        State state,
        Instant createdAt,
        Instant modifiedAt) {

    public enum State { ACTIVE, TOMBSTONED }

    public FilesUserResource {
        organizationRef = required(organizationRef, "organizationRef");
        fileId = required(fileId, "fileId");
        spaceRef = required(spaceRef, "spaceRef");
        parentFileId = required(parentFileId, "parentFileId");
        path = required(path, "path");
        ownerPrincipalRef = required(ownerPrincipalRef, "ownerPrincipalRef");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(modifiedAt, "modifiedAt");
        if (bindingRevision < 1 || !path.startsWith("/") || "/".equals(path)) {
            throw new IllegalArgumentException("Files resource requires a binding and non-root path");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
