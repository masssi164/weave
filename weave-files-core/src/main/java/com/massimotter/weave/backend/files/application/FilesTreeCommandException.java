package com.massimotter.weave.backend.files.application;

import java.util.Objects;

/** Provider- and protocol-independent failure emitted by canonical Files tree mutations. */
public final class FilesTreeCommandException extends RuntimeException {

    private final Code code;

    public FilesTreeCommandException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        NOT_FOUND,
        PARENT_MISSING,
        PARENT_NOT_COLLECTION,
        PRECONDITION_FAILED,
        TREE_CONFLICT,
        INVALID_BLOB_REFERENCE,
        CONTENT_INTEGRITY_FAILED,
        METADATA_CONFLICT
    }
}
