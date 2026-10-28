package com.massimotter.weave.backend.boards.support;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Internal Boards exception that carries only support-safe public details.
 * Provider stack traces, URLs with tokens, and raw upstream messages must stay out of this type.
 */
public class BoardsException extends RuntimeException {

    private final BoardsErrorCode code;
    private final Map<String, String> details;

    public BoardsException(BoardsErrorCode code, String supportSafeMessage) {
        this(code, supportSafeMessage, Map.of());
    }

    public BoardsException(BoardsErrorCode code, String supportSafeMessage, Map<String, String> details) {
        super(requireNonNull(supportSafeMessage, "supportSafeMessage must not be null"));
        this.code = requireNonNull(code, "code must not be null");
        this.details = Map.copyOf(requireNonNull(details, "details must not be null"));
    }

    public BoardsErrorCode code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }
}
