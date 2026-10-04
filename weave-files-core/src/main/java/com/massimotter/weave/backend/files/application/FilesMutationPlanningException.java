package com.massimotter.weave.backend.files.application;

/** Support-safe failure raised before a native Files mutation plan is committed. */
public final class FilesMutationPlanningException extends RuntimeException {

    public enum Code {
        NOT_FOUND,
        PATH_CONFLICT,
        PARENT_MISSING,
        PARENT_NOT_COLLECTION,
        PRECONDITION_FAILED,
        INVALID_BLOB_BINDING
    }

    private final Code code;

    public FilesMutationPlanningException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }
}
