package com.massimotter.weave.backend.runner.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Strict HTTP semantics shared by the Runner claim controller and its OpenAPI contract. */
public final class RunnerClaimHttpSemantics {

    public static final int DEFAULT_WAIT_SECONDS = 25;
    public static final int MAXIMUM_WAIT_SECONDS = 30;
    public static final int EMPTY_RETRY_AFTER_SECONDS = 1;
    public static final String CACHE_CONTROL_VALUE = "no-store";

    private RunnerClaimHttpSemantics() {}

    public static ClaimPreference parsePrefer(List<String> values) {
        throw new UnsupportedOperationException(
                "strict Prefer parsing is the current red TDD boundary");
    }

    public static <T> ClaimHttpResponse<T> respond(
            Optional<T> lease,
            ClaimPreference preference) {
        throw new UnsupportedOperationException(
                "deterministic claim response headers are the current red TDD boundary");
    }

    public record ClaimPreference(int waitSeconds) {

        public ClaimPreference {
            if (waitSeconds < 0 || waitSeconds > MAXIMUM_WAIT_SECONDS) {
                throw new IllegalArgumentException("waitSeconds must be between zero and 30");
            }
        }

        public String appliedHeader() {
            return "wait=" + waitSeconds;
        }
    }

    public record ClaimHttpResponse<T>(
            int status,
            Map<String, String> headers,
            Optional<T> body) {

        public ClaimHttpResponse {
            if (status != 200 && status != 204) {
                throw new IllegalArgumentException("claim response status must be 200 or 204");
            }
            headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
            body = Objects.requireNonNull(body, "body");
            if ((status == 200) != body.isPresent()) {
                throw new IllegalArgumentException("only HTTP 200 may contain a task lease");
            }
        }
    }
}
