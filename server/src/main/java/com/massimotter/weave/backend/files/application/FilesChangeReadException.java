package com.massimotter.weave.backend.files.application;

/** Support-safe fail-closed error from the native Files change reader. */
public final class FilesChangeReadException extends RuntimeException {

    public enum Code {
        INVALID_AFTER_REVISION,
        INVALID_LIMIT,
        INVALID_CONTINUATION,
        STREAM_NOT_PROVISIONED,
        CORRUPT_STREAM
    }

    private final Code code;

    FilesChangeReadException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    static FilesChangeReadException invalidContinuation() {
        return new FilesChangeReadException(
                Code.INVALID_CONTINUATION,
                "The Files change continuation is invalid.");
    }
}
