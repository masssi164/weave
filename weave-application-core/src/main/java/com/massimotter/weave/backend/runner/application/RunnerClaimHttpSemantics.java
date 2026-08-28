package com.massimotter.weave.backend.runner.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict HTTP semantics shared by the Runner claim controller and its OpenAPI contract. */
public final class RunnerClaimHttpSemantics {

    public static final int DEFAULT_WAIT_SECONDS = 25;
    public static final int MAXIMUM_WAIT_SECONDS = 30;
    public static final int EMPTY_RETRY_AFTER_SECONDS = 1;
    public static final String CACHE_CONTROL_VALUE = "no-store";

    private static final Pattern WAIT_PREFERENCE =
            Pattern.compile("wait=(0|[1-9]|[12][0-9]|30)");

    private RunnerClaimHttpSemantics() {}

    public static ClaimPreference parsePrefer(List<String> values) {
        List<String> headers = List.copyOf(Objects.requireNonNull(values, "values"));
        if (headers.isEmpty()) {
            return new ClaimPreference(DEFAULT_WAIT_SECONDS);
        }
        if (headers.size() != 1) {
            throw new IllegalArgumentException("exactly one Prefer header is supported");
        }
        String value = Objects.requireNonNull(headers.getFirst(), "Prefer header");
        Matcher matcher = WAIT_PREFERENCE.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Prefer must use the exact form wait=N where N is between zero and 30");
        }
        return new ClaimPreference(Integer.parseInt(matcher.group(1)));
    }

    public static <T> ClaimHttpResponse<T> respond(
            Optional<T> lease,
            ClaimPreference preference) {
        Optional<T> body = Objects.requireNonNull(lease, "lease");
        ClaimPreference applied = Objects.requireNonNull(preference, "preference");
        if (body.isPresent()) {
            return new ClaimHttpResponse<>(
                    200,
                    Map.of(
                            "Cache-Control", CACHE_CONTROL_VALUE,
                            "Preference-Applied", applied.appliedHeader()),
                    body);
        }
        return new ClaimHttpResponse<>(
                204,
                Map.of(
                        "Cache-Control", CACHE_CONTROL_VALUE,
                        "Preference-Applied", applied.appliedHeader(),
                        "Retry-After", Integer.toString(EMPTY_RETRY_AFTER_SECONDS)),
                body);
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
