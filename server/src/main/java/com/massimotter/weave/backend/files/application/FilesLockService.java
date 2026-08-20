package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class FilesLockService {

    private static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    private final FilesAuthorityRepository repository;
    private final Clock clock;

    public FilesLockService(FilesAuthorityRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public GrantedLock acquire(String organizationRef, String spaceRef, FilePath path, String ownerRef, Duration timeout) {
        Instant now = Instant.now(clock);
        Duration bounded = timeout == null || timeout.isNegative() || timeout.isZero()
                ? MAX_TIMEOUT
                : timeout.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : timeout;
        String token = "opaquelocktoken:" + UUID.randomUUID();
        FileLockRecord stored;
        try {
            stored = repository.acquireLock(new FileLockRecord(
                    organizationRef, spaceRef, path, tokenDigest(token), ownerRef, 1, now.plus(bounded), now), now);
        } catch (LockConflictException exception) {
            throw new FileLockedException(path);
        }
        return new GrantedLock(path, token, stored.fence(), stored.expiresAt());
    }

    public void requireUnlocked(
            String organizationRef, String spaceRef, FilePath path, String presentedToken, String actorRef) {
        repository.activeLocks(organizationRef, spaceRef, Instant.now(clock)).stream()
                .filter(lock -> applies(lock.path(), path))
                .findFirst()
                .ifPresent(lock -> {
            if (presentedToken == null
                    || !MessageDigest.isEqual(
                            lock.tokenDigest().getBytes(StandardCharsets.US_ASCII),
                            tokenDigest(presentedToken).getBytes(StandardCharsets.US_ASCII))
                    || !lock.ownerRef().equals(actorRef)) {
                throw new FileLockedException(path);
            }
        });
    }

    private boolean applies(FilePath lockedPath, FilePath requestPath) {
        String locked = lockedPath.value();
        String request = requestPath.value();
        return request.equals(locked)
                || request.startsWith(locked.endsWith("/") ? locked : locked + "/")
                || locked.startsWith(request.endsWith("/") ? request : request + "/");
    }

    public void release(
            String organizationRef, String spaceRef, FilePath path, String presentedToken, String actorRef) {
        if (presentedToken == null || presentedToken.isBlank()) {
            throw new FileLockedException(path);
        }
        try {
            repository.releaseLock(
                    organizationRef, spaceRef, path, tokenDigest(presentedToken), actorRef, Instant.now(clock));
        } catch (LockConflictException exception) {
            throw new FileLockedException(path);
        }
    }

    public GrantedLock refresh(
            String organizationRef,
            String spaceRef,
            FilePath path,
            String presentedToken,
            String actorRef) {
        FileLockRecord active = repository.activeLock(organizationRef, spaceRef, path, Instant.now(clock))
                .orElseThrow(() -> new FileLockedException(path));
        if (presentedToken == null
                || !MessageDigest.isEqual(
                        active.tokenDigest().getBytes(StandardCharsets.US_ASCII),
                        tokenDigest(presentedToken).getBytes(StandardCharsets.US_ASCII))
                || !active.ownerRef().equals(actorRef)) {
            throw new FileLockedException(path);
        }
        return new GrantedLock(path, presentedToken, active.fence(), active.expiresAt());
    }

    public boolean unlocked(String organizationRef, String spaceRef, FilePath path) {
        return repository.activeLock(organizationRef, spaceRef, path, Instant.now(clock)).isEmpty();
    }

    public void move(
            String organizationRef,
            String spaceRef,
            FilePath source,
            FilePath destination,
            String presentedToken,
            String actorRef) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        if (repository.activeLock(organizationRef, spaceRef, source, Instant.now(clock)).isEmpty()) {
            return;
        }
        try {
            repository.moveLock(
                    organizationRef, spaceRef, source, destination, tokenDigest(presentedToken), actorRef,
                    Instant.now(clock));
        } catch (LockConflictException exception) {
            throw new FileLockedException(source);
        }
    }

    public String tokenDigest(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("lock token must not be blank");
        }
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record GrantedLock(FilePath path, String token, long fence, Instant expiresAt) {
    }

    public static final class FileLockedException extends RuntimeException {
        public FileLockedException(FilePath path) {
            super("file path is locked: " + path.value());
        }
    }
}
