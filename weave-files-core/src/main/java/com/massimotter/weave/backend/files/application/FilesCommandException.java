package com.massimotter.weave.backend.files.application;

import java.util.Objects;

/** Provider- and protocol-independent failure emitted by canonical Files mutation use cases. */
public final class FilesCommandException extends RuntimeException {

    private final Code code;

    public FilesCommandException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }

    public enum Code {
        PATH_CONFLICT,
        PARENT_MISSING,
        PARENT_NOT_COLLECTION,
        METADATA_CONFLICT
    }
}
