package com.massimotter.weave.backend.boards.vikunja;

import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Support-safe Vikunja error mapper. It keeps raw provider messages, URLs, and
 * tokens out of the Weave Boards/Tasks boundary while preserving enough context
 * for diagnostics and future API error envelopes.
 */
public final class VikunjaErrorMapper {

    public BoardsException toBoardsException(int httpStatus, String operation) {
        String safeOperation = requireText(operation, "operation");
        BoardsErrorCode code = codeFor(httpStatus);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("provider", "vikunja");
        details.put("operation", safeOperation);
        if (httpStatus > 0) {
            details.put("httpStatus", Integer.toString(httpStatus));
        }
        return new BoardsException(code, messageFor(code), details);
    }

    private BoardsErrorCode codeFor(int httpStatus) {
        return switch (httpStatus) {
            case 0 -> BoardsErrorCode.OFFLINE;
            case 401 -> BoardsErrorCode.UNAUTHORIZED;
            case 403 -> BoardsErrorCode.FORBIDDEN;
            case 404 -> BoardsErrorCode.NOT_FOUND;
            case 409 -> BoardsErrorCode.CONFLICT;
            case 422 -> BoardsErrorCode.VALIDATION;
            case 429 -> BoardsErrorCode.RATE_LIMITED;
            case 502, 503, 504 -> BoardsErrorCode.PROVIDER_UNAVAILABLE;
            default -> httpStatus >= 500
                    ? BoardsErrorCode.PROVIDER_UNAVAILABLE
                    : BoardsErrorCode.UNKNOWN;
        };
    }

    private String messageFor(BoardsErrorCode code) {
        return switch (code) {
            case UNAUTHORIZED -> "Vikunja rejected the configured Boards/Tasks credentials.";
            case FORBIDDEN -> "Vikunja denied access to this Boards/Tasks resource.";
            case NOT_FOUND -> "The requested Boards/Tasks resource was not found in Vikunja.";
            case CONFLICT -> "The Boards/Tasks resource changed in Vikunja. Refresh and try again.";
            case RATE_LIMITED -> "Vikunja rate-limited the Boards/Tasks request. Try again later.";
            case OFFLINE -> "Vikunja could not be reached for this Boards/Tasks request.";
            case VALIDATION -> "Vikunja rejected the Boards/Tasks request as invalid.";
            case PROVIDER_UNAVAILABLE -> "Vikunja is currently unavailable for Boards/Tasks.";
            case UNKNOWN -> "Vikunja returned an unexpected Boards/Tasks error.";
            case UNSUPPORTED_CAPABILITY -> "The selected Boards/Tasks capability is not supported by Vikunja.";
        };
    }

    private String requireText(String value, String name) {
        requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
