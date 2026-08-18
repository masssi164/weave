package com.massimotter.weave.backend.files.port.persistence;

/** Persistence-level optimistic or uniqueness conflict for canonical Files state. */
public final class FilesRepositoryConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FilesRepositoryConflictException(String message) {
        super(message);
    }

    public FilesRepositoryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
