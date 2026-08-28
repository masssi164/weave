package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Authenticated Runner identity derived from a verified client certificate. */
public record RunnerWorkloadIdentity(
        RunnerId runnerId,
        String organizationRef,
        String certificateFingerprint,
        Instant certificateValidFrom,
        Instant certificateExpiresAt) {

    private static final Pattern SHA256 = Pattern.compile("sha256:[a-f0-9]{64}");

    public RunnerWorkloadIdentity {
        runnerId = Objects.requireNonNull(runnerId, "runnerId");
        organizationRef = required(organizationRef, 256, "organizationRef");
        certificateFingerprint = Objects.requireNonNull(
                certificateFingerprint,
                "certificateFingerprint");
        if (!SHA256.matcher(certificateFingerprint).matches()) {
            throw new IllegalArgumentException("certificateFingerprint must be a sha256 digest");
        }
        certificateValidFrom = Objects.requireNonNull(certificateValidFrom, "certificateValidFrom");
        certificateExpiresAt = Objects.requireNonNull(certificateExpiresAt, "certificateExpiresAt");
        if (!certificateExpiresAt.isAfter(certificateValidFrom)) {
            throw new IllegalArgumentException("certificate validity window is invalid");
        }
    }

    public void requireUsableAt(Instant instant) {
        Instant now = Objects.requireNonNull(instant, "instant");
        if (now.isBefore(certificateValidFrom) || !now.isBefore(certificateExpiresAt)) {
            throw new RunnerAuthenticationException("Runner client certificate is not currently valid");
        }
    }

    public void requireRunner(RunnerId requestedRunnerId) {
        RunnerId requested = Objects.requireNonNull(requestedRunnerId, "requestedRunnerId");
        if (!runnerId.equals(requested)) {
            throw new RunnerAuthenticationException(
                    "authenticated Runner does not match the requested runnerId");
        }
    }

    private static String required(String value, int maximum, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }

    public static final class RunnerAuthenticationException extends SecurityException {

        private static final long serialVersionUID = 1L;

        public RunnerAuthenticationException(String message) {
            super(message);
        }
    }
}
