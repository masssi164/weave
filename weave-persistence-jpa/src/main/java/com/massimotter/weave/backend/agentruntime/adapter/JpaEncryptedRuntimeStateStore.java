package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RestoredRuntimeState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeStateGeneration;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreException;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateDeletionEntity;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateDeletionId;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateDeletionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateGenerationEntity;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateGenerationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateHeadEntity;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateHeadJpaRepository;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Self-hosted external state adapter: AES-256-GCM envelope encryption before ordered PostgreSQL
 * chunk persistence, with a generation CAS as the wake/write acknowledgement boundary.
 */
public class JpaEncryptedRuntimeStateStore implements RuntimeStateStore {
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
  private final RuntimeStateKeyWrapper keyWrapper;
  private final SecureRandom secureRandom;
  private final Clock clock;
  private final int chunkBytes;
  private final long maximumGenerationBytes;

  public JpaEncryptedRuntimeStateStore(
      RuntimeStateHeadJpaRepository heads,
      RuntimeStateGenerationJpaRepository generations,
      RuntimeStateDeletionJpaRepository deletions,
      PlatformTransactionManager transactionManager,
      RuntimeStateKeyWrapper keyWrapper,
      SecureRandom secureRandom,
      Clock clock,
      int chunkBytes,
      long maximumGenerationBytes) {
    this.heads = Objects.requireNonNull(heads, "heads");
    this.generations = Objects.requireNonNull(generations, "generations");
    this.deletions = Objects.requireNonNull(deletions, "deletions");
    this.transactions =
        new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
    this.keyWrapper = Objects.requireNonNull(keyWrapper, "keyWrapper");
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (chunkBytes < 4_096
        || chunkBytes > 4 * 1024 * 1024
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
    byte[] authenticatedContext =
        authenticatedContext(
            command.organizationRef(),
            command.personRef(),
            command.cellRef(),
            command.runtimeStateStoreRef(),
            generationRef,
            generation,
            command.runtimeProfileHash());
    EncryptedGeneration encrypted = encrypt(plaintext, authenticatedContext);
    try {
      RuntimeStateGeneration result =
          transactions.execute(
              status ->
                  commitTransaction(
                      command,
                      generation,
                      generationRef,
                      plaintext,
                      authenticatedContext,
                      encrypted));
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
    RuntimeStateHeadEntity head =
        lockHead(command.runtimeStateStoreRef())
            .orElseGet(
                () -> {
                  if (command.expectedGeneration() != 0) {
                    throw conflict("The runtime-state generation compare-and-swap was rejected");
                  }
                  heads.saveAndFlush(
                      new RuntimeStateHeadEntity(
                          command.runtimeStateStoreRef(),
                          command.organizationRef(),
                          command.personRef(),
                          command.cellRef(),
                          command.auditRef(),
                          now));
                  return lockHead(command.runtimeStateStoreRef())
                      .orElseThrow(
                          () ->
                              unavailable(
                                  "The external runtime-state head could not be established",
                                  null));
                });
    requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());

    Optional<StoredGeneration> replay =
        findByIdempotency(command.runtimeStateStoreRef(), command.idempotencyKey());
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
    generations.saveAndFlush(
        new RuntimeStateGenerationEntity(
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
            command.auditRef(),
            now,
            chunks));
    try {
      head.advance(
          command.expectedGeneration(), generation, generationRef, command.auditRef(), now);
      heads.saveAndFlush(head);
    } catch (IllegalStateException staleHead) {
      throw conflict("The runtime-state generation compare-and-swap was rejected");
    }
    return new RuntimeStateGeneration(
        generationRef,
        command.runtimeStateStoreRef(),
        generation,
        command.runtimeProfileHash(),
        plaintext.length,
        chunks.size(),
        encrypted.wrappedDataKey().keyRef(),
        now);
  }

  @Override
  public Optional<RestoredRuntimeState> current(ReadRuntimeStateCommand command) {
    Objects.requireNonNull(command, "command");
    try {
      return transactions.execute(
          status -> {
            Optional<RuntimeStateHeadEntity> selected =
                findHead(command.runtimeStateStoreRef(), false);
            if (selected.isEmpty()) {
              return Optional.empty();
            }
            RuntimeStateHeadEntity head = selected.orElseThrow();
            requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());
            if (head.currentGenerationRef() == null) {
              return Optional.empty();
            }
            StoredGeneration generation =
                findGeneration(head.currentGenerationRef())
                    .orElseThrow(
                        () -> unavailable("The external runtime-state head is inconsistent", null));
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
    Optional<Deletion> prior =
        findDeletion(command.organizationRef(), command.personRef(), command.idempotencyKey());
    if (prior.isPresent()) {
      Deletion deletion = prior.orElseThrow();
      if (!deletion.cellRef().equals(command.cellRef())
          || !deletion.runtimeStateStoreRef().equals(command.runtimeStateStoreRef())) {
        throw conflict("The runtime-state deletion idempotency key was reused");
      }
      return;
    }

    Optional<RuntimeStateHeadEntity> selected = lockHead(command.runtimeStateStoreRef());
    long deleted = 0;
    if (selected.isPresent()) {
      RuntimeStateHeadEntity head = selected.orElseThrow();
      requireBinding(head, command.organizationRef(), command.personRef(), command.cellRef());
      List<RuntimeStateGenerationEntity> stored =
          generations.findByRuntimeStateStoreRefOrderByGenerationAsc(
              command.runtimeStateStoreRef());
      deleted = stored.size();
      head.clear(command.auditRef(), clock.instant());
      heads.saveAndFlush(head);
      generations.deleteAll(stored);
      generations.flush();
      heads.delete(head);
      heads.flush();
    }
    deletions.saveAndFlush(
        new RuntimeStateDeletionEntity(
            command.organizationRef(),
            command.personRef(),
            command.idempotencyKey(),
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
      return new StoreReadiness(true, "guarded-file-key", generations.count());
    } catch (DataAccessException failure) {
      return new StoreReadiness(false, "storage-unavailable", 0);
    }
  }

  private RestoredRuntimeState restore(StoredGeneration stored, RuntimeStateHeadEntity head) {
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
        || stored.nonce() == null
        || stored.nonce().length != NONCE_BYTES
        || stored.wrappedDataKey() == null
        || stored.wrappedDataKey().length != 72) {
      throw unavailable("The external runtime-state generation metadata is invalid", null);
    }
    List<byte[]> chunks = stored.chunks();
    if (chunks.size() != stored.chunkCount()) {
      throw unavailable("The external runtime-state generation is incomplete", null);
    }
    byte[] ciphertext = new byte[Math.toIntExact(stored.ciphertextBytes())];
    int offset = 0;
    for (int index = 0; index < chunks.size(); index++) {
      byte[] chunk = chunks.get(index);
      if (chunk == null
          || chunk.length == 0
          || chunk.length > chunkBytes
          || offset + chunk.length > ciphertext.length) {
        Arrays.fill(ciphertext, (byte) 0);
        throw unavailable("The external runtime-state chunk order is invalid", null);
      }
      System.arraycopy(chunk, 0, ciphertext, offset, chunk.length);
      offset += chunk.length;
    }
    if (offset != ciphertext.length) {
      Arrays.fill(ciphertext, (byte) 0);
      throw unavailable("The external runtime-state generation is incomplete", null);
    }
    byte[] context =
        authenticatedContext(
            head.organizationRef(),
            head.personRef(),
            head.cellRef(),
            head.runtimeStateStoreRef(),
            stored.generationRef(),
            stored.generation(),
            stored.runtimeProfileHash());
    byte[] dataKey =
        keyWrapper.unwrap(
            new RuntimeStateKeyWrapper.WrappedDataKey(
                stored.wrappingKeyRef(), stored.wrappedDataKey()),
            context);
    byte[] plaintext;
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(dataKey, "AES"),
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
      RuntimeStateKeyWrapper.WrappedDataKey wrapped =
          keyWrapper.wrap(dataKey, authenticatedContext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(dataKey, "AES"),
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
      chunks.add(
          Arrays.copyOfRange(ciphertext, offset, Math.min(ciphertext.length, offset + chunkBytes)));
    }
    return chunks;
  }

  private Optional<RuntimeStateHeadEntity> lockHead(String storeRef) {
    return heads.lockByStoreRef(storeRef);
  }

  private Optional<RuntimeStateHeadEntity> findHead(String storeRef, boolean lock) {
    return lock ? heads.lockByStoreRef(storeRef) : heads.findById(storeRef);
  }

  private Optional<StoredGeneration> findByIdempotency(String storeRef, String idempotencyKey) {
    return generations
        .findByRuntimeStateStoreRefAndIdempotencyKey(storeRef, idempotencyKey)
        .map(JpaEncryptedRuntimeStateStore::stored);
  }

  private Optional<StoredGeneration> findGeneration(String generationRef) {
    return generations.findById(generationRef).map(JpaEncryptedRuntimeStateStore::stored);
  }

  private Optional<Deletion> findDeletion(
      String organizationRef, String personRef, String idempotencyKey) {
    return deletions
        .findById(new RuntimeStateDeletionId(organizationRef, personRef, idempotencyKey))
        .map(entity -> new Deletion(entity.cellRef(), entity.runtimeStateStoreRef()));
  }

  private static StoredGeneration stored(RuntimeStateGenerationEntity entity) {
    return new StoredGeneration(
        entity.generationRef(),
        entity.runtimeStateStoreRef(),
        entity.generation(),
        entity.previousGeneration(),
        entity.runtimeProfileHash(),
        entity.encryptionAlgorithm(),
        entity.wrappingKeyRef(),
        entity.wrappedDataKey(),
        entity.nonce(),
        entity.plaintextBytes(),
        entity.ciphertextBytes(),
        entity.chunkCount(),
        entity.committedAt(),
        entity.chunks());
  }

  private static RuntimeStateGeneration projection(StoredGeneration stored) {
    return new RuntimeStateGeneration(
        stored.generationRef(),
        stored.runtimeStateStoreRef(),
        stored.generation(),
        stored.runtimeProfileHash(),
        stored.plaintextBytes(),
        stored.chunkCount(),
        stored.wrappingKeyRef(),
        stored.committedAt());
  }

  private static void requireBinding(
      RuntimeStateHeadEntity head, String organizationRef, String personRef, String cellRef) {
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
      throw new IllegalStateException(
          "in-memory runtime-state context encoding failed", impossible);
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
      Instant committedAt,
      List<byte[]> chunks) {}

  private record Deletion(String cellRef, String runtimeStateStoreRef) {}

  private record EncryptedGeneration(
      RuntimeStateKeyWrapper.WrappedDataKey wrappedDataKey, byte[] nonce, byte[] ciphertext) {
    private void destroy() {
      Arrays.fill(nonce, (byte) 0);
      Arrays.fill(ciphertext, (byte) 0);
      Arrays.fill(wrappedDataKey.wrappedKey(), (byte) 0);
    }
  }
}
