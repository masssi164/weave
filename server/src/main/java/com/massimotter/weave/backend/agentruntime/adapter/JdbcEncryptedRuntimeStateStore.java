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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Self-hosted external state adapter: AES-256-GCM envelope encryption before
 * ordered PostgreSQL chunk persistence, with a generation CAS as the wake/write
 * acknowledgement boundary.
 */
public final class JdbcEncryptedRuntimeStateStore implements RuntimeStateStore {
    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM+A256KWP";
    private static final byte[] AAD_DOMAIN =
            "weave.runtime-state.aad/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GENERATION_DOMAIN =
            "weave.runtime-state.generation/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int DATA_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RuntimeStateKeyWrapper keyWrapper;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final int chunkBytes;
    private final long maximumGenerationBytes;

    public JdbcEncryptedRuntimeStateStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            RuntimeStateKeyWrapper keyWrapper,
            SecureRandom secureRandom,
            Clock clock,
            int chunkBytes,
            long maximumGenerationBytes) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.keyWrapper = Objects.requireNonNull(keyWrapper, "keyWrapper");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (chunkBytes < 4_096 || chunkBytes > 4 * 1024 * 1024
                || maximumGenerationBytes < chunkBytes
                || maximumGenerationBytes > 1024L * 1024L * 1024L) {
            throw new IllegalArgumentException("runtime-state size bounds are invalid");
        }
        this.chunkBytes = chunkBytes;
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
        Head head = lockHead(command.runtimeStateStoreRef()).orElseGet(() -> {
            if (command.expectedGeneration() != 0) {
                throw conflict("The runtime-state generation compare-and-swap was rejected");
            }
            jdbc.update("""
                            insert into weave_agent_runtime_state_heads (
                                runtime_state_store_ref, organization_ref, person_ref, cell_ref,
                                current_generation, current_generation_ref, version, audit_ref,
                                created_at, updated_at
                            ) values (?, ?, ?, ?, 0, null, 0, ?, ?, ?)
                            """,
                    command.runtimeStateStoreRef(), command.organizationRef(), command.personRef(),
                    command.cellRef(), command.auditRef(), sqlTimestamp(now), sqlTimestamp(now));
            return lockHead(command.runtimeStateStoreRef()).orElseThrow(() ->
                    unavailable("The external runtime-state head could not be established", null));
        });
        requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());

        Optional<StoredGeneration> replay = findByIdempotency(
                command.runtimeStateStoreRef(), command.idempotencyKey());
        if (replay.isPresent()) {
            RestoredRuntimeState restored = restore(replay.orElseThrow(), head);
            byte[] previous = restored.state();
            try {
                StoredGeneration stored = replay.orElseThrow();
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
        List<byte[]> chunks = chunks(encrypted.ciphertext());
        jdbc.update("""
                        insert into weave_agent_runtime_state_generations (
                            generation_ref, runtime_state_store_ref, generation, previous_generation,
                            runtime_profile_hash, idempotency_key, encryption_algorithm,
                            wrapping_key_ref, wrapped_data_key, nonce, plaintext_bytes,
                            ciphertext_bytes, chunk_count, audit_ref, committed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                generationRef, command.runtimeStateStoreRef(), generation, command.expectedGeneration(),
                command.runtimeProfileHash(), command.idempotencyKey(), ENCRYPTION_ALGORITHM,
                encrypted.wrappedDataKey().keyRef(), encrypted.wrappedDataKey().wrappedKey(),
                encrypted.nonce(), (long) plaintext.length, (long) encrypted.ciphertext().length,
                chunks.size(), command.auditRef(), sqlTimestamp(now));
        for (int index = 0; index < chunks.size(); index++) {
            jdbc.update("""
                            insert into weave_agent_runtime_state_chunks (
                                generation_ref, chunk_ordinal, ciphertext
                            ) values (?, ?, ?)
                            """,
                    generationRef, index, chunks.get(index));
        }
        int updated = jdbc.update("""
                        update weave_agent_runtime_state_heads
                           set current_generation = ?, current_generation_ref = ?,
                               version = version + 1, audit_ref = ?, updated_at = ?
                         where runtime_state_store_ref = ?
                           and current_generation = ? and version = ?
                        """,
                generation, generationRef, command.auditRef(), sqlTimestamp(now),
                command.runtimeStateStoreRef(), command.expectedGeneration(), head.version());
        if (updated != 1) {
            throw conflict("The runtime-state generation compare-and-swap was rejected");
        }
        return new RuntimeStateGeneration(
                generationRef, command.runtimeStateStoreRef(), generation,
                command.runtimeProfileHash(), plaintext.length, chunks.size(),
                encrypted.wrappedDataKey().keyRef(), now);
    }

    @Override
    public Optional<RestoredRuntimeState> current(ReadRuntimeStateCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            return transactions.execute(status -> {
                Optional<Head> selected = findHead(command.runtimeStateStoreRef(), false);
                if (selected.isEmpty()) {
                    return Optional.empty();
                }
                Head head = selected.orElseThrow();
                requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());
                if (head.currentGenerationRef() == null) {
                    return Optional.empty();
                }
                StoredGeneration generation = findGeneration(head.currentGenerationRef())
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
        Optional<Deletion> prior = findDeletion(
                command.organizationRef(), command.personRef(), command.idempotencyKey());
        if (prior.isPresent()) {
            Deletion deletion = prior.orElseThrow();
            if (!deletion.cellRef().equals(command.cellRef())
                    || !deletion.runtimeStateStoreRef().equals(command.runtimeStateStoreRef())) {
                throw conflict("The runtime-state deletion idempotency key was reused");
            }
            return;
        }

        Optional<Head> selected = lockHead(command.runtimeStateStoreRef());
        long deleted = 0;
        if (selected.isPresent()) {
            Head head = selected.orElseThrow();
            requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());
            Long count = jdbc.queryForObject("""
                    select count(*) from weave_agent_runtime_state_generations
                     where runtime_state_store_ref = ?
                    """, Long.class, command.runtimeStateStoreRef());
            deleted = count == null ? 0 : count;
            jdbc.update("""
                    update weave_agent_runtime_state_heads
                       set current_generation = 0, current_generation_ref = null,
                           version = version + 1, audit_ref = ?, updated_at = ?
                     where runtime_state_store_ref = ?
                    """, command.auditRef(), sqlTimestamp(clock.instant()), command.runtimeStateStoreRef());
            jdbc.update("""
                    delete from weave_agent_runtime_state_chunks
                     where generation_ref in (
                         select generation_ref from weave_agent_runtime_state_generations
                          where runtime_state_store_ref = ?
                     )
                    """, command.runtimeStateStoreRef());
            jdbc.update("""
                    delete from weave_agent_runtime_state_generations
                     where runtime_state_store_ref = ?
                    """, command.runtimeStateStoreRef());
            jdbc.update("""
                    delete from weave_agent_runtime_state_heads
                     where runtime_state_store_ref = ?
                    """, command.runtimeStateStoreRef());
        }
        jdbc.update("""
                        insert into weave_agent_runtime_state_deletions (
                            organization_ref, person_ref, cell_ref, runtime_state_store_ref,
                            idempotency_key, deleted_generation_count, audit_ref, completed_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                command.organizationRef(), command.personRef(), command.cellRef(),
                command.runtimeStateStoreRef(), command.idempotencyKey(), deleted,
                command.auditRef(), sqlTimestamp(clock.instant()));
    }

    private static Timestamp sqlTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    @Override
    public StoreReadiness readiness() {
        RuntimeStateKeyWrapper.KeyReadiness keyReadiness = keyWrapper.readiness();
        if (!keyReadiness.ready()) {
            return new StoreReadiness(false, "wrapping-key-unavailable", 0);
        }
        try {
            Long count = jdbc.queryForObject(
                    "select count(*) from weave_agent_runtime_state_generations", Long.class);
            return new StoreReadiness(true, "guarded-file-key", count == null ? 0 : count);
        } catch (DataAccessException failure) {
            return new StoreReadiness(false, "storage-unavailable", 0);
        }
    }

    private RestoredRuntimeState restore(StoredGeneration stored, Head head) {
        if (!stored.runtimeStateStoreRef().equals(head.runtimeStateStoreRef())) {
            throw unavailable("The external runtime-state generation is inconsistent", null);
        }
        if (stored.plaintextBytes() < 0 || stored.plaintextBytes() > maximumGenerationBytes) {
            throw unavailable("The external runtime-state generation metadata is invalid", null);
        }
        long expectedCiphertextBytes = stored.plaintextBytes() + (GCM_TAG_BITS / 8);
        long expectedChunks = (expectedCiphertextBytes + chunkBytes - 1) / chunkBytes;
        if (!ENCRYPTION_ALGORITHM.equals(stored.encryptionAlgorithm())
                || stored.ciphertextBytes() != expectedCiphertextBytes
                || stored.ciphertextBytes() > maximumGenerationBytes + (GCM_TAG_BITS / 8)
                || stored.chunkCount() != expectedChunks
                || stored.nonce() == null || stored.nonce().length != NONCE_BYTES
                || stored.wrappedDataKey() == null || stored.wrappedDataKey().length != 72) {
            throw unavailable("The external runtime-state generation metadata is invalid", null);
        }
        List<Chunk> chunks = jdbc.query("""
                        select chunk_ordinal, ciphertext
                          from weave_agent_runtime_state_chunks
                         where generation_ref = ?
                         order by chunk_ordinal
                        """,
                (result, row) -> new Chunk(result.getInt("chunk_ordinal"), result.getBytes("ciphertext")),
                stored.generationRef());
        if (chunks.size() != stored.chunkCount()) {
            throw unavailable("The external runtime-state generation is incomplete", null);
        }
        byte[] ciphertext = new byte[Math.toIntExact(stored.ciphertextBytes())];
        int offset = 0;
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            if (chunk.ordinal() != index || chunk.ciphertext() == null
                    || chunk.ciphertext().length == 0 || chunk.ciphertext().length > chunkBytes
                    || offset + chunk.ciphertext().length > ciphertext.length) {
                Arrays.fill(ciphertext, (byte) 0);
                throw unavailable("The external runtime-state chunk order is invalid", null);
            }
            System.arraycopy(chunk.ciphertext(), 0, ciphertext, offset, chunk.ciphertext().length);
            offset += chunk.ciphertext().length;
        }
        if (offset != ciphertext.length) {
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

    private List<byte[]> chunks(byte[] ciphertext) {
        List<byte[]> chunks = new ArrayList<>((ciphertext.length + chunkBytes - 1) / chunkBytes);
        for (int offset = 0; offset < ciphertext.length; offset += chunkBytes) {
            chunks.add(Arrays.copyOfRange(ciphertext, offset, Math.min(ciphertext.length, offset + chunkBytes)));
        }
        return chunks;
    }

    private Optional<Head> lockHead(String storeRef) {
        return findHead(storeRef, true);
    }

    private Optional<Head> findHead(String storeRef, boolean lock) {
        String sql = """
                select runtime_state_store_ref, organization_ref, person_ref, cell_ref,
                       current_generation, current_generation_ref, version
                  from weave_agent_runtime_state_heads
                 where runtime_state_store_ref = ?
                """ + (lock ? " for update" : "");
        return jdbc.query(sql, JdbcEncryptedRuntimeStateStore::head, storeRef).stream().findFirst();
    }

    private Optional<StoredGeneration> findByIdempotency(String storeRef, String idempotencyKey) {
        return jdbc.query(GENERATION_SELECT + " where runtime_state_store_ref = ? and idempotency_key = ?",
                JdbcEncryptedRuntimeStateStore::generation, storeRef, idempotencyKey).stream().findFirst();
    }

    private Optional<StoredGeneration> findGeneration(String generationRef) {
        return jdbc.query(GENERATION_SELECT + " where generation_ref = ?",
                JdbcEncryptedRuntimeStateStore::generation, generationRef).stream().findFirst();
    }

    private Optional<Deletion> findDeletion(String organizationRef, String personRef, String idempotencyKey) {
        return jdbc.query("""
                        select cell_ref, runtime_state_store_ref
                          from weave_agent_runtime_state_deletions
                         where organization_ref = ? and person_ref = ? and idempotency_key = ?
                        """,
                (result, row) -> new Deletion(
                        result.getString("cell_ref"), result.getString("runtime_state_store_ref")),
                organizationRef, personRef, idempotencyKey).stream().findFirst();
    }

    private static final String GENERATION_SELECT = """
            select generation_ref, runtime_state_store_ref, generation, previous_generation,
                   runtime_profile_hash, encryption_algorithm, wrapping_key_ref, wrapped_data_key, nonce,
                   plaintext_bytes, ciphertext_bytes, chunk_count, committed_at
              from weave_agent_runtime_state_generations
            """;

    private static Head head(ResultSet result, int row) throws SQLException {
        return new Head(
                result.getString("runtime_state_store_ref"),
                result.getString("organization_ref"),
                result.getString("person_ref"),
                result.getString("cell_ref"),
                result.getLong("current_generation"),
                result.getString("current_generation_ref"),
                result.getLong("version"));
    }

    private static StoredGeneration generation(ResultSet result, int row) throws SQLException {
        return new StoredGeneration(
                result.getString("generation_ref"),
                result.getString("runtime_state_store_ref"),
                result.getLong("generation"),
                result.getLong("previous_generation"),
                result.getString("runtime_profile_hash"),
                result.getString("encryption_algorithm"),
                result.getString("wrapping_key_ref"),
                result.getBytes("wrapped_data_key"),
                result.getBytes("nonce"),
                result.getLong("plaintext_bytes"),
                result.getLong("ciphertext_bytes"),
                result.getInt("chunk_count"),
                result.getTimestamp("committed_at").toInstant());
    }

    private static RuntimeStateGeneration projection(StoredGeneration stored) {
        return new RuntimeStateGeneration(
                stored.generationRef(), stored.runtimeStateStoreRef(), stored.generation(),
                stored.runtimeProfileHash(), stored.plaintextBytes(), stored.chunkCount(),
                stored.wrappingKeyRef(), stored.committedAt());
    }

    private static void requireBinding(
            Head head,
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

    private record Head(
            String runtimeStateStoreRef,
            String organizationRef,
            String personRef,
            String cellRef,
            long currentGeneration,
            String currentGenerationRef,
            long version) {
    }

    private record StoredGeneration(
            String generationRef,
            String runtimeStateStoreRef,
            long generation,
            long previousGeneration,
            String runtimeProfileHash,
            String encryptionAlgorithm,
            String wrappingKeyRef,
            byte[] wrappedDataKey,
            byte[] nonce,
            long plaintextBytes,
            long ciphertextBytes,
            int chunkCount,
            Instant committedAt) {
    }

    private record Chunk(int ordinal, byte[] ciphertext) {
    }

    private record Deletion(String cellRef, String runtimeStateStoreRef) {
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
