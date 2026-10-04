package com.massimotter.weave.backend.files.application;

/** Fail-closed signal for inconsistent or unsafe adapter-private Files cleanup evidence. */
public final class NativeFilesBlobCleanupException extends RuntimeException {
    public NativeFilesBlobCleanupException(String message) {
        super(message);
    }

    public NativeFilesBlobCleanupException(String message, Throwable cause) {
        super(message, cause);
    }
}
