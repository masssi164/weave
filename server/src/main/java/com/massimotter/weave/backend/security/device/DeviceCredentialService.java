package com.massimotter.weave.backend.security.device;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeviceCredentialService {

    private static final Duration DEFAULT_LIFETIME = Duration.ofDays(90);

    private final DeviceCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public DeviceCredentialService(DeviceCredentialRepository repository) {
        this(repository, passwordEncoder(), new SecureRandom(), Clock.systemUTC());
    }

    DeviceCredentialService(
            DeviceCredentialRepository repository,
            PasswordEncoder passwordEncoder,
            SecureRandom secureRandom,
            Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public IssuedDeviceCredential issue(
            String domain,
            String tenantId,
            String principalRef,
            String subject,
            String username,
            String clientType,
            String label,
            Set<String> capabilities) {
        String normalizedDomain = normalizeDomain(domain);
        String credentialId = normalizedDomain + "_device_" + UUID.randomUUID();
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        Instant issuedAt = clock.instant();
        DeviceCredential credential = new DeviceCredential(
                credentialId,
                normalizedDomain,
                tenantId,
                principalRef,
                subject,
                username,
                defaultText(clientType, normalizedDomain + "-dav"),
                defaultText(label, "Weave " + normalizedDomain + " client"),
                capabilities,
                passwordEncoder.encode(secret),
                issuedAt,
                issuedAt.plus(DEFAULT_LIFETIME),
                null);
        return new IssuedDeviceCredential(repository.save(credential), secret);
    }

    public List<DeviceCredential> list(String domain, String principalRef) {
        return repository.findByDomainAndPrincipal(normalizeDomain(domain), principalRef);
    }

    public DeviceCredential requireOwned(String domain, String credentialId, String principalRef) {
        DeviceCredential credential = repository.findById(credentialId).orElse(null);
        if (credential == null
                || !credential.domain().equals(normalizeDomain(domain))
                || !credential.principalRef().equals(principalRef)) {
            throw new DeviceCredentialException(DeviceCredentialException.Reason.NOT_FOUND);
        }
        return credential;
    }

    public DeviceCredential revoke(String domain, String credentialId, String principalRef) {
        DeviceCredential credential = requireOwned(domain, credentialId, principalRef);
        if (credential.revokedAt() != null) {
            return credential;
        }
        return repository.save(credential.revoke(clock.instant()));
    }

    public DeviceCredential authenticate(String domain, String credentialId, String secret) {
        DeviceCredential credential = repository.findById(credentialId).orElse(null);
        if (credential == null
                || !credential.domain().equals(normalizeDomain(domain))
                || !credential.activeAt(clock.instant())
                || secret == null
                || secret.length() > 256
                || !passwordEncoder.matches(secret, credential.secretHash())) {
            throw new DeviceCredentialException(DeviceCredentialException.Reason.INVALID);
        }
        return credential;
    }

    private static PasswordEncoder passwordEncoder() {
        Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder(
                "",
                16,
                600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        encoder.setEncodeHashAsBase64(true);
        return encoder;
    }

    private String normalizeDomain(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("device credential domain must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("files", "calendar").contains(normalized)) {
            throw new IllegalArgumentException("device credential domain is not supported");
        }
        return normalized;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
