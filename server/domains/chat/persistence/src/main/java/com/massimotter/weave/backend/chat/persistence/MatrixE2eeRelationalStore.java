package com.massimotter.weave.backend.chat.persistence;

import com.massimotter.weave.backend.chat.port.MatrixE2eePersistence;
import com.massimotter.weave.backend.chat.persistence.MatrixE2eeJpaModel.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** ORM authority for normalized public Matrix envelopes, never client cryptographic secrets. */
public class MatrixE2eeRelationalStore implements MatrixE2eePersistence {
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private static final String DEVICE_SCOPE = "e.tenantId=?1 and e.userId=?2 and e.deviceId=?3";
    private static final String USER_SCOPE = "e.tenantId=?1 and e.userId=?2";
    private static final String BACKUP_SCOPE = USER_SCOPE + " and e.versionId=?3";

    public MatrixE2eeRelationalStore(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = java.util.Objects.requireNonNull(entityManager, "entityManager");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional(readOnly = true)
    public long currentRevision(String tenantId) {
        return first(SyncHead.class, "e.tenantId=?1", tenantId).map(e -> e.revision).orElse(0L);
    }

    @Override
    @Transactional
    public long nextRevision(String tenantId) {
        return nextRevisionLocked(tenantId);
    }

    private SyncHead syncHeadLocked(String tenantId) {
        fence(tenantId, "sync");
        return first(SyncHead.class, "e.tenantId=?1", tenantId).orElseGet(() -> {
            SyncHead head = new SyncHead();
            head.tenantId = tenantId;
            head.updatedAtUtc = databaseNow();
            entityManager.persist(head);
            return head;
        });
    }

    private long nextRevisionLocked(String tenantId) {
        SyncHead head = syncHeadLocked(tenantId);
        head.revision++;
        head.rowVersion++;
        head.updatedAtUtc = databaseNow();
        return head.revision;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceRecord> device(String tenantId, String userId, String deviceId) {
        return first(Device.class, DEVICE_SCOPE, tenantId, userId, deviceId).map(this::deviceRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceRecord> devices(String tenantId, String userId, Set<String> requestedDeviceIds) {
        TypedQuery<Device> query = requestedDeviceIds == null || requestedDeviceIds.isEmpty()
                ? query(Device.class, USER_SCOPE + " order by e.deviceId", tenantId, userId)
                : query(Device.class, USER_SCOPE + " and e.deviceId in ?3 order by e.deviceId", tenantId, userId, requestedDeviceIds);
        return query.getResultList().stream().map(this::deviceRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<String> activeDeviceIds(String tenantId, String userId) {
        return select("select e.deviceId from MatrixDevice e where " + USER_SCOPE + " and e.revoked=false order by e.deviceId", String.class, tenantId, userId).getResultList();
    }

    @Override
    @Transactional
    public void upsertDevice(String tenantId, String userId, String deviceId, Map<String, Object> deviceKeys, long changedRevision) {
        deviceFence(tenantId, userId, deviceId);
        Device value = first(Device.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElse(null);
        String json = writeJson(deviceKeys == null ? Map.of() : deviceKeys);
        if (value == null) {
            value = new Device();
            value.tenantId = tenantId;
            value.userId = userId;
            value.deviceId = deviceId;
            value.deviceKeysJson = json;
            value.updatedAtUtc = databaseNow();
            entityManager.persist(value);
        } else {
            if (!"{}".equals(json)) value.deviceKeysJson = json;
            value.rowVersion++;
        }
        value.changedRevision = changedRevision;
        value.revoked = false;
        value.updatedAtUtc = databaseNow();
    }

    @Override
    @Transactional
    public void addOneTimeKeys(String tenantId, String userId, String deviceId, Map<String, Object> keys) {
        deviceFence(tenantId, userId, deviceId);
        for (var entry : keys.entrySet()) {
            String keyId = entry.getKey();
            Object value = entry.getValue();
            OneTimeKey key = query(OneTimeKey.class, DEVICE_SCOPE + " and e.keyId=?4", tenantId, userId, deviceId, keyId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().stream().findFirst().orElseGet(() -> {
                OneTimeKey created = new OneTimeKey();
                created.tenantId = tenantId;
                created.userId = userId;
                created.deviceId = deviceId;
                created.keyId = keyId;
                return created;
            });
            key.algorithm = algorithm(keyId);
            key.keyJson = writeJson(value);
            key.claimedAtUtc = null;
            key.claimRef = null;
            if (!entityManager.contains(key)) { key.uploadedAtUtc = databaseNow(); entityManager.persist(key); }
        }
    }

    @Override
    @Transactional
    public void replaceFallbackKeys(String tenantId, String userId, String deviceId, Map<String, Object> keys, long changedRevision) {
        deviceFence(tenantId, userId, deviceId);
        delete(FallbackKey.class, DEVICE_SCOPE, tenantId, userId, deviceId);
        for (var entry : keys.entrySet()) {
            String keyId = entry.getKey();
            Object value = entry.getValue();
            FallbackKey key = new FallbackKey();
            key.tenantId = tenantId;
            key.userId = userId;
            key.deviceId = deviceId;
            key.keyId = keyId;
            key.algorithm = algorithm(keyId);
            key.keyJson = writeJson(value);
            key.updatedAtUtc = databaseNow();
            entityManager.persist(key);
        }
        upsertDevice(tenantId, userId, deviceId, Map.of(), changedRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> oneTimeKeyCounts(String tenantId, String userId, String deviceId) {
        Map<String, Long> result = new LinkedHashMap<>();
        select("select e.algorithm,count(e) from MatrixOneTimeKey e where " + DEVICE_SCOPE + " and e.claimedAtUtc is null group by e.algorithm order by e.algorithm", Object[].class, tenantId, userId, deviceId)
                .getResultList().forEach(row -> result.put((String) row[0], (Long) row[1]));
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> unusedFallbackAlgorithms(String tenantId, String userId, String deviceId) {
        return select("select distinct e.algorithm from MatrixFallbackKey e where " + DEVICE_SCOPE + " and e.used=false order by e.algorithm", String.class, tenantId, userId, deviceId).getResultList();
    }

    @Override
    @Transactional
    public Optional<ClaimedKey> claimOneTimeKey(String tenantId, String userId, String deviceId, String requestedAlgorithm) {
        // Hibernate's supported timeout -2 requests PostgreSQL FOR UPDATE SKIP LOCKED.
        List<OneTimeKey> keys = query(OneTimeKey.class, DEVICE_SCOPE + " and e.algorithm=?4 and e.claimedAtUtc is null order by e.keyId", tenantId, userId, deviceId, requestedAlgorithm)
                .setMaxResults(1).setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint("jakarta.persistence.lock.timeout", -2).getResultList();
        if (!keys.isEmpty()) {
            OneTimeKey key = keys.getFirst();
            Object value = readValue(key.keyJson);
            entityManager.remove(key);
            return Optional.of(new ClaimedKey(key.keyId, value, false));
        }
        return query(FallbackKey.class, DEVICE_SCOPE + " and e.algorithm=?4 order by e.keyId", tenantId, userId, deviceId, requestedAlgorithm)
                .setMaxResults(1).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().stream().findFirst().map(key -> {
                    key.used = true;
                    key.updatedAtUtc = databaseNow();
                    return new ClaimedKey(key.keyId, readValue(key.keyJson), true);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CrossSigningRecord> crossSigning(String tenantId, String userId) {
        Map<String, Map<String, Object>> keys = new LinkedHashMap<>();
        query(CrossSigningKey.class, USER_SCOPE + " order by e.usage", tenantId, userId).getResultList().forEach(key -> keys.put(key.usage, readObject(key.keyJson)));
        return keys.isEmpty() ? Optional.empty() : Optional.of(new CrossSigningRecord(keys.getOrDefault("master", Map.of()), keys.getOrDefault("self_signing", Map.of()), keys.getOrDefault("user_signing", Map.of())));
    }

    @Override
    @Transactional
    public void upsertCrossSigning(String tenantId, String userId, Map<String, Object> masterKey, Map<String, Object> selfSigningKey, Map<String, Object> userSigningKey, long changedRevision) {
        fence(tenantId, "signing:" + userId);
        saveSigningKey(tenantId, userId, "master", masterKey, changedRevision);
        saveSigningKey(tenantId, userId, "self_signing", selfSigningKey, changedRevision);
        saveSigningKey(tenantId, userId, "user_signing", userSigningKey, changedRevision);
    }

    @Override
    @Transactional
    public void mergeDeviceSignatures(String tenantId, String userId, String deviceId, Map<String, Object> signatures, long changedRevision) {
        deviceFence(tenantId, userId, deviceId);
        device(tenantId, userId, deviceId).ifPresent(value -> upsertDevice(tenantId, userId, deviceId, mergeSignatures(value.deviceKeys(), signatures), changedRevision));
    }

    @Override
    @Transactional
    public void mergeCrossSigningSignatures(String tenantId, String userId, String keyId, Map<String, Object> signatures, long changedRevision) {
        fence(tenantId, "signing:" + userId);
        crossSigning(tenantId, userId).ifPresent(current -> {
            if (matchesCrossSigningKey(current.masterKey(), keyId)) saveSigningKey(tenantId, userId, "master", mergeSignatures(current.masterKey(), signatures), changedRevision);
            else if (matchesCrossSigningKey(current.selfSigningKey(), keyId)) saveSigningKey(tenantId, userId, "self_signing", mergeSignatures(current.selfSigningKey(), signatures), changedRevision);
            else if (matchesCrossSigningKey(current.userSigningKey(), keyId)) saveSigningKey(tenantId, userId, "user_signing", mergeSignatures(current.userSigningKey(), signatures), changedRevision);
        });
    }

    @Override
    @Transactional
    public long appendToDevice(String tenantId, String targetUserId, String targetDeviceId, String senderUserId, String eventType, String transactionId, Map<String, Object> content) {
        // Fence before checking idempotency, including when neither a head nor a message exists.
        SyncHead head = syncHeadLocked(tenantId);
        Optional<ToDeviceMessage> existing = first(ToDeviceMessage.class, "e.tenantId=?1 and e.senderUserId=?2 and e.transactionId=?3 and e.targetUserId=?4 and e.targetDeviceId=?5 and e.eventType=?6", tenantId, senderUserId, transactionId, targetUserId, targetDeviceId, eventType);
        if (existing.isPresent()) return existing.get().revisionId;
        ToDeviceMessage message = new ToDeviceMessage();
        message.tenantId = tenantId;
        message.targetUserId = targetUserId;
        message.targetDeviceId = targetDeviceId;
        message.senderUserId = senderUserId;
        message.eventType = eventType;
        message.transactionId = transactionId;
        message.contentJson = writeJson(content);
        message.revisionId = ++head.revision;
        head.rowVersion++;
        head.updatedAtUtc = databaseNow();
        message.createdAtUtc = databaseNow();
        entityManager.persist(message);
        return message.revisionId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToDeviceRecord> toDeviceEvents(String tenantId, String userId, String deviceId, long afterRevision, long highWater, int limit) {
        return query(ToDeviceMessage.class, "e.tenantId=?1 and e.targetUserId=?2 and e.targetDeviceId=?3 and e.revisionId>?4 and e.revisionId<=?5 order by e.revisionId", tenantId, userId, deviceId, afterRevision, highWater)
                .setMaxResults(limit).getResultList().stream().map(e -> new ToDeviceRecord(e.revisionId, e.senderUserId, e.eventType, readObject(e.contentJson))).toList();
    }

    @Override
    @Transactional
    public void recordDeviceSyncProgress(String tenantId, String userId, String deviceId, long revision) {
        SyncProgress progress = progressLocked(tenantId, userId, deviceId, revision, 0, revision);
        progress.toDeviceSequence = Math.max(progress.toDeviceSequence, revision);
        progress.lastIssuedRevision = Math.max(progress.lastIssuedRevision, revision);
    }

    @Override
    @Transactional(readOnly = true)
    public long deviceListProgress(String tenantId, String userId, String deviceId) {
        return first(SyncProgress.class, DEVICE_SCOPE, tenantId, userId, deviceId).map(e -> e.deviceListRevision).orElse(0L);
    }

    @Override
    @Transactional
    public void recordDeviceListProgress(String tenantId, String userId, String deviceId, long revision) {
        SyncProgress progress = progressLocked(tenantId, userId, deviceId, 0, revision, revision);
        progress.deviceListRevision = Math.max(progress.deviceListRevision, revision);
        progress.lastIssuedRevision = Math.max(progress.lastIssuedRevision, revision);
    }

    private SyncProgress progressLocked(String tenantId, String userId, String deviceId, long initialSequence, long initialListRevision, long initialIssuedRevision) {
        deviceFence(tenantId, userId, deviceId);
        SyncProgress progress = first(SyncProgress.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElse(null);
        if (progress == null) {
            progress = new SyncProgress();
            progress.tenantId = tenantId;
            progress.userId = userId;
            progress.deviceId = deviceId;
            progress.toDeviceSequence = initialSequence;
            progress.deviceListRevision = initialListRevision;
            progress.lastIssuedRevision = initialIssuedRevision;
            progress.updatedAtUtc = databaseNow();
            entityManager.persist(progress);
        } else progress.rowVersion++;
        progress.updatedAtUtc = databaseNow();
        return progress;
    }

    @Override
    @Transactional
    public Map<String, DeviceListState> reconcileSharedUsers(String tenantId, String userId, String deviceId, Set<String> currentlySharedUserIds) {
        syncHeadLocked(tenantId);
        Map<String, SharedUser> current = new LinkedHashMap<>();
        query(SharedUser.class, DEVICE_SCOPE + " order by e.sharedUserId", tenantId, userId, deviceId).getResultList().forEach(e -> current.put(e.sharedUserId, e));
        Set<String> union = new LinkedHashSet<>(current.keySet());
        union.addAll(currentlySharedUserIds);
        Map<String, DeviceListState> changed = new LinkedHashMap<>();
        for (String sharedUserId : union) {
            boolean desired = currentlySharedUserIds.contains(sharedUserId);
            SharedUser prior = current.get(sharedUserId);
            if (prior == null || prior.shared != desired) {
                long revision = nextRevisionLocked(tenantId);
                if (prior == null) {
                    prior = new SharedUser();
                    prior.tenantId = tenantId;
                    prior.userId = userId;
                    prior.deviceId = deviceId;
                    prior.sharedUserId = sharedUserId;
                    entityManager.persist(prior);
                }
                prior.shared = desired;
                prior.changedRevision = revision;
                SharedUserChange change = new SharedUserChange();
                change.tenantId = tenantId;
                change.userId = userId;
                change.deviceId = deviceId;
                change.sharedUserId = sharedUserId;
                change.shared = desired;
                change.changedRevision = revision;
                entityManager.persist(change);
                changed.put(sharedUserId, new DeviceListState(desired, revision));
            }
        }
        return Map.copyOf(changed);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, DeviceListState> sharedUserChanges(String tenantId, String userId, String deviceId, long afterRevision, long highWater) {
        Map<String, DeviceListState> result = new LinkedHashMap<>();
        query(SharedUserChange.class, DEVICE_SCOPE + " and e.changedRevision>?4 and e.changedRevision<=?5 order by e.changedRevision,e.sharedUserId", tenantId, userId, deviceId, afterRevision, highWater)
                .getResultList().forEach(e -> result.put(e.sharedUserId, new DeviceListState(e.shared, e.changedRevision)));
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> deviceUsersChanged(String tenantId, long afterRevision, long highWater) {
        return select("select distinct e.userId from MatrixDevice e where e.tenantId=?1 and e.changedRevision>?2 and e.changedRevision<=?3 order by e.userId", String.class, tenantId, afterRevision, highWater).getResultList();
    }

    @Override
    @Transactional
    public void revokeDevice(String tenantId, String userId, String deviceId, long changedRevision) {
        deviceFence(tenantId, userId, deviceId);
        Device value = first(Device.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElseThrow(() -> new IllegalArgumentException("Matrix device not found"));
        value.revoked = true;
        value.changedRevision = changedRevision;
        value.rowVersion++;
        value.updatedAtUtc = databaseNow();
        delete(OneTimeKey.class, DEVICE_SCOPE, tenantId, userId, deviceId);
        delete(FallbackKey.class, DEVICE_SCOPE, tenantId, userId, deviceId);
        delete(SharedUser.class, DEVICE_SCOPE, tenantId, userId, deviceId);
    }

    @Override
    @Transactional
    public boolean bindOidcSession(String tenantId, String userId, String sessionHash, String deviceId) {
        fence(tenantId, "oidc:" + userId);
        OidcBinding binding = first(OidcBinding.class, USER_SCOPE + " and e.oidcSessionHash=?3", tenantId, userId, sessionHash).orElse(null);
        if (binding == null) {
            binding = new OidcBinding();
            binding.tenantId = tenantId;
            binding.userId = userId;
            binding.oidcSessionHash = sessionHash;
            binding.deviceId = deviceId;
            binding.boundAtUtc = databaseNow();
            entityManager.persist(binding);
        }
        return binding.deviceId.equals(deviceId);
    }

    @Override
    @Transactional
    public boolean bindDeviceProof(String tenantId, String userId, String deviceId, String proofHash) {
        deviceFence(tenantId, userId, deviceId);
        Device device = first(Device.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElse(null);
        Optional<DeviceProof> bound = first(DeviceProof.class, DEVICE_SCOPE, tenantId, userId, deviceId);
        if (bound.isEmpty() && (device == null || (!device.revoked && device.deviceKeysJson == null))) {
            DeviceProof proof = new DeviceProof();
            proof.tenantId = tenantId;
            proof.userId = userId;
            proof.deviceId = deviceId;
            proof.proofHash = proofHash;
            proof.boundAtUtc = databaseNow();
            entityManager.persist(proof);
            bound = Optional.of(proof);
        }
        return (device == null || !device.revoked) && bound.filter(e -> sameProof(e.proofHash, proofHash)).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> deviceProofHash(String tenantId, String userId, String deviceId) {
        return first(DeviceProof.class, DEVICE_SCOPE, tenantId, userId, deviceId).map(e -> e.proofHash);
    }

    @Override
    @Transactional
    public boolean issueDeviceRecoveryChallenge(String tenantId, String userId, String deviceId, String challengeId, String challengeText, String proofHash, Instant expiresAt) {
        deviceFence(tenantId, userId, deviceId);
        Device device = first(Device.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElse(null);
        if (device == null || device.revoked || device.deviceKeysJson == null || "{}".equals(device.deviceKeysJson) || deviceProofHash(tenantId, userId, deviceId).isPresent()) return false;
        RecoveryChallenge challenge = first(RecoveryChallenge.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElse(null);
        Instant now = databaseNow();
        if (challenge != null && challenge.expiresAtUtc.isAfter(now) && challenge.consumedAtUtc == null && !challenge.proofHash.equals(proofHash)) return false;
        if (challenge == null) {
            challenge = new RecoveryChallenge();
            challenge.tenantId = tenantId;
            challenge.userId = userId;
            challenge.deviceId = deviceId;
        }
        challenge.challengeId = UUID.fromString(challengeId);
        challenge.challengeText = challengeText;
        challenge.proofHash = proofHash;
        challenge.expiresAtUtc = expiresAt;
        challenge.consumedAtUtc = null;
        if (!entityManager.contains(challenge)) entityManager.persist(challenge);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceRecoveryChallenge> deviceRecoveryChallenge(String tenantId, String userId, String deviceId, String challengeId) {
        return first(RecoveryChallenge.class, DEVICE_SCOPE + " and e.challengeId=?4 and e.consumedAtUtc is null and e.expiresAtUtc>instant", tenantId, userId, deviceId, UUID.fromString(challengeId))
                .map(e -> new DeviceRecoveryChallenge(e.challengeId.toString(), e.challengeText, e.proofHash, e.expiresAtUtc));
    }

    @Override
    @Transactional
    public boolean completeDeviceRecoveryChallenge(String tenantId, String userId, String deviceId, String challengeId, String proofHash, String expectedPublicKey) {
        deviceFence(tenantId, userId, deviceId);
        Optional<RecoveryChallenge> found = lockedFirst(RecoveryChallenge.class, DEVICE_SCOPE + " and e.challengeId=?4 and e.consumedAtUtc is null and e.expiresAtUtc>instant", tenantId, userId, deviceId, UUID.fromString(challengeId));
        if (found.isEmpty() || !sameProof(found.get().proofHash, proofHash)) return false;
        Device device = lockedFirst(Device.class, DEVICE_SCOPE, tenantId, userId, deviceId).orElse(null);
        if (device == null || device.revoked || !expectedPublicKey.equals(objectMap(readObject(device.deviceKeysJson).get("keys")).get("ed25519:" + deviceId))) return false;
        found.get().consumedAtUtc = databaseNow();
        Optional<DeviceProof> proof = first(DeviceProof.class, DEVICE_SCOPE, tenantId, userId, deviceId);
        if (proof.isEmpty()) {
            DeviceProof created = new DeviceProof();
            created.tenantId = tenantId;
            created.userId = userId;
            created.deviceId = deviceId;
            created.proofHash = proofHash;
            created.boundAtUtc = databaseNow();
            entityManager.persist(created);
            return true;
        }
        return sameProof(proof.get().proofHash, proofHash);
    }

    @Override
    @Transactional
    public String createBackupVersion(String tenantId, String userId, String algorithm, Map<String, Object> authData) {
        backupFence(tenantId, userId);
        long version = select("select coalesce(max(e.versionId),0)+1 from MatrixBackupVersion e where " + USER_SCOPE, Long.class, tenantId, userId).getSingleResult();
        for (BackupVersion current : query(BackupVersion.class, USER_SCOPE + " and e.currentVersion=true", tenantId, userId).getResultList()) {
            current.currentVersion = false;
        }
        // Flush retirement before inserting a new current row under the partial unique index.
        entityManager.flush();
        BackupVersion created = new BackupVersion();
        created.tenantId = tenantId;
        created.userId = userId;
        created.versionId = version;
        created.algorithm = algorithm;
        created.authDataJson = writeJson(authData);
        created.currentVersion = true;
        created.revision = 1;
        entityManager.persist(created);
        return Long.toString(version);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BackupVersionRecord> backupVersion(String tenantId, String userId, String version) {
        if (version == null || version.isBlank()) return currentBackupVersion(tenantId, userId);
        long value;
        try { value = Long.parseLong(version);
        } catch (NumberFormatException invalid) { return Optional.empty();
        }
        return first(BackupVersion.class, BACKUP_SCOPE, tenantId, userId, value).map(this::backupRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BackupVersionRecord> currentBackupVersion(String tenantId, String userId) {
        return first(BackupVersion.class, USER_SCOPE + " and e.currentVersion=true order by e.versionId desc", tenantId, userId).map(this::backupRecord);
    }

    @Override
    @Transactional
    public void updateBackupVersion(String tenantId, String userId, String version, String algorithm, Map<String, Object> authData) {
        backupFence(tenantId, userId);
        BackupVersion value = first(BackupVersion.class, BACKUP_SCOPE, tenantId, userId, Long.parseLong(version)).orElseThrow(() -> new IllegalArgumentException("backup version not found"));
        value.algorithm = algorithm;
        value.authDataJson = writeJson(authData);
        value.revision++;
    }

    @Override
    @Transactional
    public boolean deleteBackupVersion(String tenantId, String userId, String version) {
        backupFence(tenantId, userId);
        long parsed = Long.parseLong(version);
        delete(BackupSession.class, BACKUP_SCOPE, tenantId, userId, parsed);
        return delete(BackupVersion.class, BACKUP_SCOPE, tenantId, userId, parsed) == 1;
    }

    @Override
    @Transactional
    public BackupMutationResult putBackupKeys(String tenantId, String userId, String version, String roomId, String sessionId, Map<String, Object> request) {
        backupFence(tenantId, userId);
        long parsed = Long.parseLong(version);
        if (sessionId != null) {
            upsertBackupSession(tenantId, userId, parsed, roomId, sessionId, request);
        } else if (roomId != null) {
            for (var session : objectMap(request.get("sessions")).entrySet()) {
                upsertBackupSession(tenantId, userId, parsed, roomId, session.getKey(), objectMap(session.getValue()));
            }
        } else {
            for (var room : objectMap(request.get("rooms")).entrySet()) {
                for (var session : objectMap(objectMap(room.getValue()).get("sessions")).entrySet()) {
                    upsertBackupSession(tenantId, userId, parsed, room.getKey(), session.getKey(), objectMap(session.getValue()));
                }
            }
        }
        first(BackupVersion.class, BACKUP_SCOPE, tenantId, userId, parsed).ifPresent(e -> e.revision++);
        BackupVersionRecord record = backupVersion(tenantId, userId, version).orElseThrow();
        return new BackupMutationResult(record.count(), Integer.toHexString(writeJson(record.authData()).hashCode()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> backupKeys(String tenantId, String userId, String version, String roomId, String sessionId) {
        long parsed = Long.parseLong(version);
        if (sessionId != null) return first(BackupSession.class, BACKUP_SCOPE + " and e.roomId=?4 and e.sessionId=?5", tenantId, userId, parsed, roomId, sessionId).map(e -> readObject(e.payloadJson)).orElse(Map.of());
        if (roomId != null) {
            Map<String, Object> sessions = new LinkedHashMap<>();
            query(BackupSession.class, BACKUP_SCOPE + " and e.roomId=?4 order by e.sessionId", tenantId, userId, parsed, roomId).getResultList().forEach(e -> sessions.put(e.sessionId, readObject(e.payloadJson)));
            return Map.of("sessions", Map.copyOf(sessions));
        }
        Map<String, Map<String, Object>> rooms = new LinkedHashMap<>();
        query(BackupSession.class, BACKUP_SCOPE + " order by e.roomId,e.sessionId", tenantId, userId, parsed).getResultList().forEach(e -> rooms.computeIfAbsent(e.roomId, ignored -> new LinkedHashMap<>()).put(e.sessionId, readObject(e.payloadJson)));
        Map<String, Object> result = new LinkedHashMap<>();
        rooms.forEach((room, sessions) -> result.put(room, Map.of("sessions", Map.copyOf(sessions))));
        return Map.of("rooms", Map.copyOf(result));
    }

    @Override
    @Transactional
    public void deleteBackupKeys(String tenantId, String userId, String version, String roomId, String sessionId) {
        backupFence(tenantId, userId);
        long parsed = Long.parseLong(version);
        int deleted;
        if (sessionId != null) deleted = delete(BackupSession.class, BACKUP_SCOPE + " and e.roomId=?4 and e.sessionId=?5", tenantId, userId, parsed, roomId, sessionId);
        else if (roomId != null) deleted = delete(BackupSession.class, BACKUP_SCOPE + " and e.roomId=?4", tenantId, userId, parsed, roomId);
        else deleted = delete(BackupSession.class, BACKUP_SCOPE, tenantId, userId, parsed);
        if (deleted > 0) first(BackupVersion.class, BACKUP_SCOPE, tenantId, userId, parsed).ifPresent(e -> e.revision++);
    }

    @Override
    @Transactional
    public void putAccountData(String tenantId, String userId, String eventType, Map<String, Object> content, long changedRevision) {
        fence(tenantId, "account:" + userId);
        AccountData value = first(AccountData.class, USER_SCOPE + " and e.eventType=?3", tenantId, userId, eventType).orElseGet(() -> {
            AccountData created = new AccountData();
            created.tenantId = tenantId;
            created.userId = userId;
            created.eventType = eventType;
            return created;
        });
        value.contentJson = writeJson(content);
        value.changedRevision = changedRevision;
        if (!entityManager.contains(value)) entityManager.persist(value);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> accountData(String tenantId, String userId, String eventType) {
        return first(AccountData.class, USER_SCOPE + " and e.eventType=?3", tenantId, userId, eventType).map(e -> readObject(e.contentJson));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> accountData(String tenantId, String userId) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        query(AccountData.class, USER_SCOPE + " order by e.eventType", tenantId, userId).getResultList().forEach(e -> result.put(e.eventType, readObject(e.contentJson)));
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportSafeStats supportSafeStats() {
        long active = count(Device.class, "e.revoked=false"), revoked = count(Device.class, "e.revoked=true");
        long queued = count(ToDeviceMessage.class, "1=1"), encrypted = count(ToDeviceMessage.class, "e.eventType='m.room.encrypted'");
        long plaintext = count(ToDeviceMessage.class, "e.eventType in ('m.room_key','m.forwarded_room_key')");
        long[] olm = {0, 0};
        // Only public envelope JSON needs client-side inspection;
        // The stream closes with this transaction.
        try (var envelopes = select("select e.contentJson from MatrixToDeviceMessage e where e.eventType='m.room.encrypted'", String.class).setHint("org.hibernate.fetchSize", 128).getResultStream()) {
            envelopes.forEach(json -> accumulateOlmEnvelopeCounts(readObject(json), olm));
        }
        // Tuple distinct keeps tenant boundaries and avoids PostgreSQL's invalid NUL text concatenation.
        long targets = select("select count(distinct (e.tenantId,e.targetUserId,e.targetDeviceId)) from MatrixToDeviceMessage e", Long.class).getSingleResult();
        long transactions = select("select count(distinct (e.tenantId,e.senderUserId,e.transactionId)) from MatrixToDeviceMessage e", Long.class).getSingleResult();
        long revision = select("select coalesce(max(e.revision),0) from MatrixSyncHead e", Long.class).getSingleResult();
        return new SupportSafeStats(active, revoked, queued, encrypted, plaintext, olm[0], olm[1], targets, transactions, revision);
    }

    private DeviceRecord deviceRecord(Device value) { return new DeviceRecord(value.userId, value.deviceId, readObject(value.deviceKeysJson), value.changedRevision, value.revoked); }
    private BackupVersionRecord backupRecord(BackupVersion value) {
        return new BackupVersionRecord(Long.toString(value.versionId), value.algorithm, readObject(value.authDataJson), value.currentVersion, value.revision, count(BackupSession.class, BACKUP_SCOPE, value.tenantId, value.userId, value.versionId));
    }

    private void saveSigningKey(String tenantId, String userId, String usage, Map<String, Object> key, long revision) {
        Optional<CrossSigningKey> prior = first(CrossSigningKey.class, USER_SCOPE + " and e.usage=?3", tenantId, userId, usage);
        if (key == null || key.isEmpty()) { prior.ifPresent(entityManager::remove); return; }
        CrossSigningKey value = prior.orElseGet(() -> {
            CrossSigningKey created = new CrossSigningKey();
            created.tenantId = tenantId;
            created.userId = userId;
            created.usage = usage;
            return created;
        });
        value.keyId = objectMap(key.get("keys")).entrySet().stream().findFirst().map(Map.Entry::getKey).orElse(usage);
        value.keyJson = writeJson(key);
        value.changedRevision = revision;
        if (!entityManager.contains(value)) entityManager.persist(value);
    }

    private void upsertBackupSession(String tenantId, String userId, long version, String roomId, String sessionId, Map<String, Object> payload) {
        BackupSession value = first(BackupSession.class, BACKUP_SCOPE + " and e.roomId=?4 and e.sessionId=?5", tenantId, userId, version, roomId, sessionId).orElseGet(() -> {
            BackupSession created = new BackupSession();
            created.tenantId = tenantId;
            created.userId = userId;
            created.versionId = version;
            created.roomId = roomId;
            created.sessionId = sessionId;
            return created;
        });
        value.payloadJson = writeJson(payload);
        if (!entityManager.contains(value)) entityManager.persist(value);
    }

    /** Missing rows cannot be pessimistically locked. Fence only the owning aggregate's writers. */
    private void fence(String tenantId, String aggregate) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(?1),hashtext(?2))", Object.class)
                .setParameter(1, tenantId).setParameter(2, "matrix:" + aggregate).getSingleResult();
    }
    private void deviceFence(String tenantId, String userId, String deviceId) { fence(tenantId, "device:" + userId.length() + ":" + userId + deviceId); }
    private void backupFence(String tenantId, String userId) { fence(tenantId, "backup:" + userId); }
    private Instant databaseNow() { return entityManager.createQuery("select instant", Instant.class).getSingleResult(); }
    private boolean sameProof(String left, String right) { return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII)); }
    private String entityName(Class<?> type) { return "Matrix" + type.getSimpleName(); }
    private <T> TypedQuery<T> query(Class<T> type, String predicate, Object... parameters) { return select("select e from " + entityName(type) + " e where " + predicate, type, parameters); }
    private <T> TypedQuery<T> select(String jpql, Class<T> type, Object... parameters) {
        TypedQuery<T> query = entityManager.createQuery(jpql, type);
        for (int index = 0; index < parameters.length; index++) query.setParameter(index + 1, parameters[index]);
        return query;
    }
    private <T> Optional<T> first(Class<T> type, String predicate, Object... parameters) { return query(type, predicate, parameters).setMaxResults(1).getResultList().stream().findFirst(); }
    private <T> Optional<T> lockedFirst(Class<T> type, String predicate, Object... parameters) {
        return query(type, predicate, parameters).setMaxResults(1).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList().stream().findFirst();
    }
    private int delete(Class<?> type, String predicate, Object... parameters) {
        // A bulk JPQL mutation bypasses the persistence context. Flush pending writes,
        // then detach only matching identities so later operations in the same transaction
        // can recreate them without clearing another capability's managed state.
        entityManager.flush();
        try (var identifiers = select("select id(e) from " + entityName(type) + " e where " + predicate,
                Object.class, parameters).setHint("org.hibernate.fetchSize", 128).getResultStream()) {
            var iterator = identifiers.iterator();
            while (iterator.hasNext()) {
                entityManager.detach(entityManager.getReference(type, iterator.next()));
            }
        }
        var query = entityManager.createQuery("delete from " + entityName(type) + " e where " + predicate);
        for (int index = 0; index < parameters.length; index++) query.setParameter(index + 1, parameters[index]);
        return query.executeUpdate();
    }
    private long count(Class<?> type, String predicate, Object... parameters) { return select("select count(e) from " + entityName(type) + " e where " + predicate, Long.class, parameters).getSingleResult(); }
    private Map<String, Object> mergeSignatures(Map<String, Object> stored, Map<String, Object> uploaded) {
        Map<String, Object> merged = new LinkedHashMap<>(stored);
        Map<String, Object> signatures = new LinkedHashMap<>(objectMap(stored.get("signatures")));
        objectMap(uploaded).forEach((user, raw) -> {
            Map<String, Object> existing = new LinkedHashMap<>(objectMap(signatures.get(user)));
            existing.putAll(objectMap(raw));
            signatures.put(user, Map.copyOf(existing));
        });
        if (!signatures.isEmpty()) merged.put("signatures", Map.copyOf(signatures));
        return Map.copyOf(merged);
    }

    private boolean matchesCrossSigningKey(Map<String, Object> key, String requestedKeyId) {
        return objectMap(key.get("keys")).entrySet().stream()
                .anyMatch(entry -> entry.getKey().equals(requestedKeyId)
                        || (entry.getValue() instanceof String value && value.equals(requestedKeyId)));
    }

    private void accumulateOlmEnvelopeCounts(Map<String, Object> content, long[] counts) {
        Object ciphertext = content.get("ciphertext");
        if (!(ciphertext instanceof Map<?, ?> map)) return;
        for (Object rawEnvelope : map.values()) {
            if (!(rawEnvelope instanceof Map<?, ?> envelope)) continue;
            Object type = envelope.get("type");
            if (type instanceof Number number) {
                if (number.intValue() == 0) counts[0]++;
                else if (number.intValue() == 1) counts[1]++;
            }
        }
    }

    private String algorithm(String keyId) { int separator = keyId.indexOf(':'); return separator > 0 ? keyId.substring(0, separator) : keyId; }

    private Map<String, Object> objectMap(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("expected object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String text)) throw new IllegalArgumentException("expected string key");
            result.put(text, entry.getValue());
        }
        return result;
    }

    private Map<String, Object> readObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { @SuppressWarnings("unchecked") Map<String, Object> value = objectMapper.readValue(json, Map.class);
        return value == null ? Map.of() : Map.copyOf(value);
        }
        catch (JacksonException exception) { throw new IllegalStateException("Matrix relational persistence object is invalid", exception); }
    }

    private Object readValue(String json) {
        try { return objectMapper.readValue(json, Object.class);
        }
        catch (JacksonException exception) { throw new IllegalStateException("Matrix relational persistence value is invalid", exception); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value);
        }
        catch (JacksonException exception) { throw new IllegalStateException("Matrix relational persistence value could not be serialized", exception); }
    }

}
