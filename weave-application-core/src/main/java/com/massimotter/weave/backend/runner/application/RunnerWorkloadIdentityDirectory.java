package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable certificate-to-Runner mapping used after the TLS client certificate is verified. */
public interface RunnerWorkloadIdentityDirectory {

    Pattern SHA256 = Pattern.compile("sha256:[a-f0-9]{64}");
    Pattern SERIAL = Pattern.compile("[0-9a-f]{1,128}");
    Pattern REVOCATION_REASON = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    RegistrationDisposition register(CertificateRegistration registration);

    Optional<RunnerWorkloadIdentity> resolveActive(String certificateFingerprint, Instant at);

    RevocationDisposition revoke(CertificateRevocation revocation);

    enum RegistrationDisposition {
        CREATED,
        IDEMPOTENT_REPLAY
    }

    enum RevocationDisposition {
        APPLIED,
        IDEMPOTENT_REPLAY
    }

    record CertificateRegistration(
            UUID certificateId,
            RunnerId runnerId,
            String organizationRef,
            String certificateFingerprint,
            String subjectDn,
            String serialNumber,
            Instant validFrom,
            Instant expiresAt,
            Instant registeredAt) {

        public CertificateRegistration {
            certificateId = Objects.requireNonNull(certificateId, "certificateId");
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            organizationRef = bounded(required(organizationRef, "organizationRef"), 256, "organizationRef");
            certificateFingerprint = fingerprint(certificateFingerprint);
            subjectDn = bounded(required(subjectDn, "subjectDn"), 1024, "subjectDn");
            serialNumber = required(serialNumber, "serialNumber").toLowerCase(java.util.Locale.ROOT);
            if (!SERIAL.matcher(serialNumber).matches()) {
                throw new IllegalArgumentException("serialNumber must be lowercase hexadecimal");
            }
            validFrom = Objects.requireNonNull(validFrom, "validFrom");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
            if (!expiresAt.isAfter(validFrom)) {
                throw new IllegalArgumentException("certificate validity window is invalid");
            }
            if (registeredAt.isAfter(expiresAt)) {
                throw new IllegalArgumentException("registeredAt must not follow certificate expiry");
            }
        }
    }

    record CertificateRevocation(
            String certificateFingerprint,
            String reasonCode,
            Instant revokedAt) {

        public CertificateRevocation {
            certificateFingerprint = fingerprint(certificateFingerprint);
            reasonCode = required(reasonCode, "reasonCode");
            if (!REVOCATION_REASON.matcher(reasonCode).matches()) {
                throw new IllegalArgumentException("reasonCode has an invalid format");
            }
            revokedAt = Objects.requireNonNull(revokedAt, "revokedAt");
        }
    }

    private static String fingerprint(String value) {
        String normalized = required(value, "certificateFingerprint");
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException("certificateFingerprint must be a sha256 digest");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }

    private static String bounded(String value, int maximum, String field) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }
}
