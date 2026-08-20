package com.massimotter.weave.backend.files.application;

import java.util.Objects;

/** Provider- and protocol-independent failure emitted by canonical Files use cases. */
public final class FilesApplicationException extends RuntimeException {

    private final Code code;

    public FilesApplicationException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        NOT_FOUND,
        NOT_A_COLLECTION,
        NOT_A_FILE,
        INVALID_BLOB_REFERENCE,
        CONTENT_INTEGRITY_FAILED
    }
}
