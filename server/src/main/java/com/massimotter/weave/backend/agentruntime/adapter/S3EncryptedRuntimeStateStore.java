package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RestoredRuntimeState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeStateGeneration;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * AES-256-GCM envelope encryption before immutable S3/MinIO generation objects.
 * PostgreSQL/JPA owns only binding, CAS head, key metadata, deletion and audit authority.
 *
 * <p>PostgreSQL and S3 do not share a transaction. Readiness therefore performs a complete,
 * read-only reconciliation of relational heads and generations against the paginated immutable
 * object namespace. Ambiguous writes, missing objects, orphans and inconsistent heads fail
 * closed before runtime traffic is served.
 */
public final class S3EncryptedRuntimeStateStore implements RuntimeStateStore {
    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM+A256KWP";
    private static final byte[] AAD_DOMAIN =
            "weave.runtime-state.aad/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GENERATION_DOMAIN =
            "weave.runtime-state.generation/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int DATA_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final RuntimeStateHeadJpaRepository heads;
    private final RuntimeStateGenerationJpaRepository generations;
    private final RuntimeStateDeletionJpaRepository deletions;
    private final TransactionTemplate transactions;
    private final S3Client objectStore;
    private final String bucket;
    private final RuntimeStateKeyWrapper keyWrapper;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final long maximumGenerationBytes;

    public S3EncryptedRuntimeStateStore(
            RuntimeStateJpaAuthority authority,
            PlatformTransactionManager transactionManager,
            S3Client objectStore,
            String bucket,
            RuntimeStateKeyWrapper keyWrapper,
            SecureRandom secureRandom,
            Clock clock,
            long maximumGenerationBytes) {
        RuntimeStateJpaAuthority safeAuthority =
                Objects.requireNonNull(authority, "authority");
        this.heads = safeAuthority.heads();
        this.generations = safeAuthority.generations();
        this.deletions = safeAuthority.deletions();
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.objectStore = Objects.requireNonNull(objectStore, "objectStore");
        this.bucket = requireBucket(bucket);
        this.keyWrapper = Objects.requireNonNull(keyWrapper, "keyWrapper");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumGenerationBytes < 4_096 || maximumGenerationBytes > 1024L * 1024L * 1024L) {
            throw new IllegalArgumentException("runtime-state size bounds are invalid");
        }
        this.maximumGenerationBytes = maximumGenerationBytes;
    }

    @Override
    public RuntimeStateGeneration commit(CommitRuntimeStateCommand command) {
        Objects.requireNonNull(command, "command");
        byte[] plaintext = command.state();
        if (plaintext.length > maximumGenerationBytes) {
            Arrays.fill(plaintext, (byte) 0);
            throw new IllegalArgumentException("runtime state exceeds the configured generation limit");
        }
        long generation = Math.addExact(command.expectedGeneration(), 1);
        String generationRef = generationRef(command, generation);
        byte[] authenticatedContext = authenticatedContext(
                command.organizationRef(), command.personRef(), command.cellRef(),
                command.runtimeStateStoreRef(), generationRef, generation, command.runtimeProfileHash());
        EncryptedGeneration encrypted = encrypt(plaintext, authenticatedContext);
        try {
            RuntimeStateGeneration result = transactions.execute(status -> commitTransaction(
                    command, generation, generationRef, plaintext, authenticatedContext, encrypted));
            if (result == null) {
                throw unavailable("The runtime-state generation could not be committed", null);
            }
            return result;
        } catch (RuntimeStateStoreException | IllegalArgumentException failure) {
            throw failure;
        } catch (DataAccessException failure) {
            throw unavailable("The external runtime-state store is unavailable", failure);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(authenticatedContext, (byte) 0);
            encrypted.destroy();
        }
    }

    private RuntimeStateGeneration commitTransaction(
            CommitRuntimeStateCommand command,
            long generation,
            String generationRef,
            byte[] plaintext,
            byte[] authenticatedContext,
            EncryptedGeneration encrypted) {
        Instant now = clock.instant();
        RuntimeStateHeadJpaEntity head = lockHead(command.runtimeStateStoreRef()).orElseGet(() -> {
            if (command.expectedGeneration() != 0) {
                throw conflict("The runtime-state generation compare-and-swap was rejected");
            }
            heads.saveAndFlush(RuntimeStateHeadJpaEntity.create(
                    command.runtimeStateStoreRef(),
                    command.organizationRef(),
                    command.personRef(),
                    command.cellRef(),
                    command.auditRef(),
                    now));
            return lockHead(command.runtimeStateStoreRef()).orElseThrow(() ->
                    unavailable("The external runtime-state head could not be established", null));
        });
        requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());

        Optional<PersistedRuntimeStateGeneration> replay = findByIdempotency(
                command.runtimeStateStoreRef(), command.idempotencyKey());
        if (replay.isPresent()) {
            RestoredRuntimeState restored = restore(replay.orElseThrow(), head);
            byte[] previous = restored.state();
            try {
                PersistedRuntimeStateGeneration stored = replay.orElseThrow();
                if (stored.previousGeneration() != command.expectedGeneration()
                        || !stored.generationRef().equals(generationRef)
                        || !stored.runtimeProfileHash().equals(command.runtimeProfileHash())
                        || !MessageDigest.isEqual(previous, plaintext)) {
                    throw conflict("The runtime-state idempotency key was reused with another command");
                }
                return restored.generation();
            } finally {
                Arrays.fill(previous, (byte) 0);
            }
        }

        if (head.currentGeneration() != command.expectedGeneration()
                || generation != head.currentGeneration() + 1) {
            throw conflict("The runtime-state generation compare-and-swap was rejected");
        }
        putGeneration(generationRef, encrypted.ciphertext());
        generations.saveAndFlush(RuntimeStateGenerationJpaEntity.create(
                generationRef,
                command.runtimeStateStoreRef(),
                generation,
                command.expectedGeneration(),
                command.runtimeProfileHash(),
                command.idempotencyKey(),
                ENCRYPTION_ALGORITHM,
                encrypted.wrappedDataKey().keyRef(),
                encrypted.wrappedDataKey().wrappedKey(),
                encrypted.nonce(),
                plaintext.length,
                encrypted.ciphertext().length,
                1,
                command.auditRef(),
                now));
        try {
            head.advance(
                    command.expectedGeneration(),
                    generation,
                    generationRef,
                    command.auditRef(),
                    now);
            heads.saveAndFlush(head);
        } catch (IllegalStateException staleHead) {
            throw conflict("The runtime-state generation compare-and-swap was rejected");
        }
        return new RuntimeStateGeneration(
                generationRef, command.runtimeStateStoreRef(), generation,
                command.runtimeProfileHash(), plaintext.length, 1,
                encrypted.wrappedDataKey().keyRef(), now);
    }

    @Override
    public Optional<RestoredRuntimeState> current(ReadRuntimeStateCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            return transactions.execute(status -> {
                Optional<RuntimeStateHeadJpaEntity> selected =
                        findHead(command.runtimeStateStoreRef(), false);
                if (selected.isEmpty()) {
                    return Optional.empty();
                }
                RuntimeStateHeadJpaEntity head = selected.orElseThrow();
                requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());
                if (head.currentGenerationRef() == null) {
                    return Optional.empty();
                }
                PersistedRuntimeStateGeneration generation = findGeneration(head.currentGenerationRef())
                        .orElseThrow(() -> unavailable(
                                "The external runtime-state head is inconsistent", null));
                if (generation.generation() != head.currentGeneration()
                        || !generation.generationRef().equals(head.currentGenerationRef())) {
                    throw unavailable("The external runtime-state head is inconsistent", null);
                }
                return Optional.of(restore(generation, head));
            });
        } catch (RuntimeStateStoreException failure) {
            throw failure;
        } catch (DataAccessException failure) {
            throw unavailable("The external runtime-state store is unavailable", failure);
        }
    }

    @Override
    public void deleteRuntimeState(DeleteRuntimeStateCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            transactions.executeWithoutResult(status -> deleteTransaction(command));
        } catch (RuntimeStateStoreException failure) {
            throw failure;
        } catch (DataAccessException failure) {
            throw unavailable("The external runtime-state store is unavailable", failure);
        }
    }

    private void deleteTransaction(DeleteRuntimeStateCommand command) {
        Optional<PersistedRuntimeStateDeletion> prior = findDeletion(
                command.organizationRef(), command.personRef(), command.idempotencyKey());
        if (prior.isPresent()) {
            PersistedRuntimeStateDeletion deletion = prior.orElseThrow();
            if (!deletion.cellRef().equals(command.cellRef())
                    || !deletion.runtimeStateStoreRef().equals(command.runtimeStateStoreRef())) {
                throw conflict("The runtime-state deletion idempotency key was reused");
            }
            return;
        }

        Optional<RuntimeStateHeadJpaEntity> selected =
                lockHead(command.runtimeStateStoreRef());
        long deleted = 0;
        if (selected.isPresent()) {
            RuntimeStateHeadJpaEntity head = selected.orElseThrow();
            requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());
            List<RuntimeStateGenerationJpaEntity> storedGenerations =
                    generations.findByRuntimeStateStoreRefOrderByGeneration(
                            command.runtimeStateStoreRef());
            deleted = storedGenerations.size();
            storedGenerations.stream()
                    .map(RuntimeStateGenerationJpaEntity::generationRef)
                    .forEach(this::deleteGeneration);
            head.clear(command.auditRef(), clock.instant());
            heads.saveAndFlush(head);
            generations.deleteAll(storedGenerations);
            generations.flush();
            heads.delete(head);
            heads.flush();
        }
        deletions.saveAndFlush(RuntimeStateDeletionJpaEntity.create(
                new RuntimeStateDeletionId(
                        command.organizationRef(),
                        command.personRef(),
                        command.idempotencyKey()),
                command.cellRef(),
                command.runtimeStateStoreRef(),
                deleted,
                command.auditRef(),
                clock.instant()));
    }

    @Override
    public StoreReadiness readiness() {
        RuntimeStateKeyWrapper.KeyReadiness keyReadiness = keyWrapper.readiness();
        if (!keyReadiness.ready()) {
            return new StoreReadiness(false, "wrapping-key-unavailable", 0);
        }
        try {
            objectStore.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            List<PersistedRuntimeStateGeneration> storedGenerations = generations.findAll().stream()
                    .map(RuntimeStateGenerationJpaEntity::stored)
                    .toList();
            if (!relationalHeadsAreConsistent(storedGenerations)
                    || !objectNamespaceIsConsistent(storedGenerations)) {
                return new StoreReadiness(false, "cross-store-inconsistent", storedGenerations.size());
            }
            return new StoreReadiness(true, "reconciled", storedGenerations.size());
        } catch (DataAccessException | S3Exception | SdkClientException failure) {
            return new StoreReadiness(false, "storage-unavailable", 0);
        }
    }

    private boolean relationalHeadsAreConsistent(
            List<PersistedRuntimeStateGeneration> storedGenerations) {
        Map<String, PersistedRuntimeStateGeneration> byReference = new HashMap<>();
        Set<String> storesWithGenerations = new HashSet<>();
        for (PersistedRuntimeStateGeneration generation : storedGenerations) {
            if (byReference.putIfAbsent(generation.generationRef(), generation) != null) {
                return false;
            }
            storesWithGenerations.add(generation.runtimeStateStoreRef());
        }
        Set<String> headStores = new HashSet<>();
        for (RuntimeStateHeadJpaEntity head : heads.findAll()) {
            if (!headStores.add(head.runtimeStateStoreRef())
                    || head.currentGeneration() <= 0
                    || head.currentGenerationRef() == null) {
                return false;
            }
            PersistedRuntimeStateGeneration current = byReference.get(head.currentGenerationRef());
            if (current == null
                    || !current.runtimeStateStoreRef().equals(head.runtimeStateStoreRef())
                    || current.generation() != head.currentGeneration()) {
                return false;
            }
        }
        return headStores.equals(storesWithGenerations);
    }

    private boolean objectNamespaceIsConsistent(
            List<PersistedRuntimeStateGeneration> storedGenerations) {
        Map<String, Long> expected = new HashMap<>();
        for (PersistedRuntimeStateGeneration generation : storedGenerations) {
            if (generation.ciphertextBytes() < 0
                    || expected.putIfAbsent(
                            objectKey(generation.generationRef()),
                            generation.ciphertextBytes()) != null) {
                return false;
            }
        }

        Map<String, Long> observed = new HashMap<>();
        Set<String> continuationTokens = new HashSet<>();
        String continuationToken = null;
        do {
            ListObjectsV2Response response = objectStore.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix("runtime-state/generations/")
                    .continuationToken(continuationToken)
                    .build());
            for (software.amazon.awssdk.services.s3.model.S3Object object : response.contents()) {
                if (object.key() == null
                        || object.size() == null
                        || object.size() < 0
                        || observed.putIfAbsent(object.key(), object.size()) != null) {
                    return false;
                }
            }
            if (!response.isTruncated()) {
                continuationToken = null;
            } else {
                continuationToken = response.nextContinuationToken();
                if (continuationToken == null
                        || continuationToken.isBlank()
                        || !continuationTokens.add(continuationToken)) {
                    return false;
                }
            }
        } while (continuationToken != null);
        return observed.equals(expected);
    }

    private RestoredRuntimeState restore(
            PersistedRuntimeStateGeneration stored,
            RuntimeStateHeadJpaEntity head) {
        if (!stored.runtimeStateStoreRef().equals(head.runtimeStateStoreRef())) {
            throw unavailable("The external runtime-state generation is inconsistent", null);
        }
        if (stored.plaintextBytes() < 0 || stored.plaintextBytes() > maximumGenerationBytes) {
            throw unavailable("The external runtime-state generation metadata is invalid", null);
        }
        long expectedCiphertextBytes = stored.plaintextBytes() + (GCM_TAG_BITS / 8);
        if (!ENCRYPTION_ALGORITHM.equals(stored.encryptionAlgorithm())
                || stored.ciphertextBytes() != expectedCiphertextBytes
                || stored.ciphertextBytes() > maximumGenerationBytes + (GCM_TAG_BITS / 8)
                || stored.chunkCount() != 1
                || stored.nonce() == null || stored.nonce().length != NONCE_BYTES
                || stored.wrappedDataKey() == null || stored.wrappedDataKey().length != 72) {
            throw unavailable("The external runtime-state generation metadata is invalid", null);
        }
        byte[] ciphertext = getGeneration(stored.generationRef());
        if (ciphertext.length != stored.ciphertextBytes()) {
            Arrays.fill(ciphertext, (byte) 0);
            throw unavailable("The external runtime-state generation is incomplete", null);
        }
        byte[] context = authenticatedContext(
                head.organizationRef(), head.personRef(), head.cellRef(), head.runtimeStateStoreRef(),
                stored.generationRef(), stored.generation(), stored.runtimeProfileHash());
        byte[] dataKey = keyWrapper.unwrap(
                new RuntimeStateKeyWrapper.WrappedDataKey(stored.wrappingKeyRef(), stored.wrappedDataKey()),
                context);
        byte[] plaintext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, stored.nonce()));
            cipher.updateAAD(context);
            plaintext = cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException failure) {
            throw unavailable("The external runtime-state generation failed authentication", failure);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(context, (byte) 0);
        }
        if (plaintext.length != stored.plaintextBytes()) {
            Arrays.fill(plaintext, (byte) 0);
            throw unavailable("The external runtime-state generation size is invalid", null);
        }
        RuntimeStateGeneration projection = projection(stored);
        RestoredRuntimeState restored = new RestoredRuntimeState(projection, plaintext);
        Arrays.fill(plaintext, (byte) 0);
        return restored;
    }

    private EncryptedGeneration encrypt(byte[] plaintext, byte[] authenticatedContext) {
        byte[] dataKey = new byte[DATA_KEY_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(dataKey);
        secureRandom.nextBytes(nonce);
        try {
            RuntimeStateKeyWrapper.WrappedDataKey wrapped = keyWrapper.wrap(dataKey, authenticatedContext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(authenticatedContext);
            return new EncryptedGeneration(wrapped, nonce, cipher.doFinal(plaintext));
        } catch (RuntimeStateStoreException failure) {
            throw failure;
        } catch (GeneralSecurityException failure) {
            throw unavailable("The runtime-state generation could not be encrypted", failure);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    private void putGeneration(String generationRef, byte[] ciphertext) {
        try {
            objectStore.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(generationRef))
                            .contentType("application/vnd.weave.runtime-state.encrypted")
                            .metadata(java.util.Map.of("generation-ref", generationRef))
                            .ifNoneMatch("*")
                            .build(),
                    RequestBody.fromBytes(ciphertext));
        } catch (S3Exception | SdkClientException failure) {
            throw unavailable("The external runtime-state object could not be committed", failure);
        }
    }

    private byte[] getGeneration(String generationRef) {
        try {
            ResponseBytes<GetObjectResponse> response = objectStore.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(generationRef))
                    .build());
            return response.asByteArray();
        } catch (S3Exception | SdkClientException failure) {
            throw unavailable("The external runtime-state object is unavailable", failure);
        }
    }

    private void deleteGeneration(String generationRef) {
        try {
            objectStore.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(generationRef))
                    .build());
        } catch (S3Exception | SdkClientException failure) {
            throw unavailable("The external runtime-state object could not be deleted", failure);
        }
    }

    private static String objectKey(String generationRef) {
        if (generationRef == null || !generationRef.matches("state-generation:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("runtime-state generation reference is invalid");
        }
        return "runtime-state/generations/" + generationRef.substring("state-generation:".length()) + ".bin";
    }

    private static String requireBucket(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
            throw new IllegalArgumentException("runtime-state object-store bucket is invalid");
        }
        return value;
    }

    private Optional<RuntimeStateHeadJpaEntity> lockHead(String storeRef) {
        return findHead(storeRef, true);
    }

    private Optional<RuntimeStateHeadJpaEntity> findHead(
            String storeRef,
            boolean lock) {
        return lock ? heads.lockByStoreRef(storeRef) : heads.findById(storeRef);
    }

    private Optional<PersistedRuntimeStateGeneration> findByIdempotency(
            String storeRef,
            String idempotencyKey) {
        return generations
                .findByRuntimeStateStoreRefAndIdempotencyKey(
                        storeRef,
                        idempotencyKey)
                .map(RuntimeStateGenerationJpaEntity::stored);
    }

    private Optional<PersistedRuntimeStateGeneration> findGeneration(String generationRef) {
        return generations.findById(generationRef)
                .map(RuntimeStateGenerationJpaEntity::stored);
    }

    private Optional<PersistedRuntimeStateDeletion> findDeletion(
            String organizationRef,
            String personRef,
            String idempotencyKey) {
        return deletions.findById(new RuntimeStateDeletionId(
                        organizationRef,
                        personRef,
                        idempotencyKey))
                .map(RuntimeStateDeletionJpaEntity::deletion);
    }

    private static RuntimeStateGeneration projection(PersistedRuntimeStateGeneration stored) {
        return new RuntimeStateGeneration(
                stored.generationRef(), stored.runtimeStateStoreRef(), stored.generation(),
                stored.runtimeProfileHash(), stored.plaintextBytes(), stored.chunkCount(),
                stored.wrappingKeyRef(), stored.committedAt());
    }

    private static void requireBinding(
            RuntimeStateHeadJpaEntity head,
            String organizationRef,
            String personRef,
            String cellRef) {
        if (!head.organizationRef().equals(organizationRef)
                || !head.personRef().equals(personRef)
                || !head.cellRef().equals(cellRef)) {
            throw conflict("The external runtime-state binding does not match this cell");
        }
    }

    private static byte[] authenticatedContext(
            String organizationRef,
            String personRef,
            String cellRef,
            String storeRef,
            String generationRef,
            long generation,
            String profileHash) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeField(output, AAD_DOMAIN);
                writeField(output, organizationRef.getBytes(StandardCharsets.UTF_8));
                writeField(output, personRef.getBytes(StandardCharsets.UTF_8));
                writeField(output, cellRef.getBytes(StandardCharsets.UTF_8));
                writeField(output, storeRef.getBytes(StandardCharsets.UTF_8));
                writeField(output, generationRef.getBytes(StandardCharsets.US_ASCII));
                output.writeLong(generation);
                writeField(output, profileHash.getBytes(StandardCharsets.US_ASCII));
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory runtime-state context encoding failed", impossible);
        }
    }

    private static String generationRef(CommitRuntimeStateCommand command, long generation) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(GENERATION_DOMAIN);
            update(digest, command.organizationRef());
            update(digest, command.personRef());
            update(digest, command.cellRef());
            update(digest, command.runtimeStateStoreRef());
            update(digest, Long.toString(generation));
            update(digest, command.runtimeProfileHash());
            update(digest, command.idempotencyKey());
            return "state-generation:" + HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (encoded.length >>> 24));
        digest.update((byte) (encoded.length >>> 16));
        digest.update((byte) (encoded.length >>> 8));
        digest.update((byte) encoded.length);
        digest.update(encoded);
    }

    private static void writeField(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static RuntimeStateStoreException conflict(String message) {
        return new RuntimeStateStoreException(message);
    }

    private static RuntimeStateStoreException unavailable(String message, Throwable cause) {
        return cause == null
                ? new RuntimeStateStoreException(message)
                : new RuntimeStateStoreException(message, cause);
    }

    private record EncryptedGeneration(
            RuntimeStateKeyWrapper.WrappedDataKey wrappedDataKey,
            byte[] nonce,
            byte[] ciphertext) {
        private void destroy() {
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(wrappedDataKey.wrappedKey(), (byte) 0);
        }
    }
}
