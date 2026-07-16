package com.massimotter.weave.backend.matrix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class MatrixE2eeStateService {

    private static final int MAX_ONE_TIME_KEYS_PER_UPLOAD = 256;
    private static final int MAX_TO_DEVICE_TARGETS = 1_000;

    private final ConcurrentMap<DeviceKey, DeviceState> devices = new ConcurrentHashMap<>();
    private final ConcurrentMap<UserKey, CrossSigningState> crossSigning = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ToDeviceEvent> toDeviceEvents = new CopyOnWriteArrayList<>();
    private final Set<TransactionKey> toDeviceTransactions = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UserKey, ConcurrentMap<String, BackupVersion>> backups = new ConcurrentHashMap<>();
    private final ConcurrentMap<UserKey, AtomicLong> backupVersionSequences = new ConcurrentHashMap<>();
    private final ConcurrentMap<UserKey, ConcurrentMap<String, Map<String, Object>>> accountData =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<OidcSessionKey, String> devicesByOidcSession = new ConcurrentHashMap<>();
    private final ConcurrentMap<DeviceKey, ConcurrentMap<String, SharedUserState>> sharedUsersByDevice =
            new ConcurrentHashMap<>();
    private final AtomicLong projectedToDeviceEventCount = new AtomicLong();
    private final AtomicLong syncResponsesWithToDeviceEvents = new AtomicLong();
    private final MatrixE2eeSequenceJournal sequenceJournal = new MatrixE2eeSequenceJournal();
    private final Set<String> loadedTenants = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final MatrixE2eeSnapshotStore snapshotStore;

    public MatrixE2eeStateService(
            ObjectMapper objectMapper,
            ObjectProvider<MatrixE2eeSnapshotStore> snapshotStoreProvider) {
        this.objectMapper = objectMapper;
        this.snapshotStore = snapshotStoreProvider.getIfAvailable();
    }

    public Map<String, Object> uploadKeys(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> request) {
        prepare(identity);
        DeviceKey key = deviceKey(identity);
        DeviceState state = devices.computeIfAbsent(key, ignored -> new DeviceState());
        boolean mutated = false;
        Map<String, Object> deviceKeys = objectMap(request.get("device_keys"));
        if (!deviceKeys.isEmpty()) {
            requireEquals(deviceKeys.get("user_id"), identity.userId(), "device key user");
            requireEquals(deviceKeys.get("device_id"), identity.deviceId(), "device key device");
            mutated = true;
        }
        Map<String, Object> oneTimeKeys = objectMap(request.get("one_time_keys"));
        if (oneTimeKeys.size() > MAX_ONE_TIME_KEYS_PER_UPLOAD) {
            throw new MatrixProtocolException("M_LIMIT_EXCEEDED", "The Matrix one-time key upload limit was reached.");
        }
        Map<String, Object> validatedOneTimeKeys = new LinkedHashMap<>();
        oneTimeKeys.forEach((keyId, value) ->
                validatedOneTimeKeys.put(requireKeyId(keyId), immutableValue(value)));
        mutated = mutated || !oneTimeKeys.isEmpty();
        Map<String, Object> fallbackKeys = objectMap(request.get("fallback_keys"));
        Map<String, Object> validatedDeviceKeys = immutableObject(deviceKeys);
        Map<String, Object> validatedFallbackKeys = immutableObject(fallbackKeys);
        mutated = mutated || !fallbackKeys.isEmpty();
        if (mutated) {
            sequenceJournal.publish(value -> {
                if (!validatedDeviceKeys.isEmpty()) {
                    state.deviceKeys = validatedDeviceKeys;
                }
                validatedOneTimeKeys.forEach(state.oneTimeKeys::put);
                if (!validatedFallbackKeys.isEmpty()) {
                    state.fallbackKeys = validatedFallbackKeys;
                    state.usedFallbackAlgorithms.clear();
                }
                state.changedSequence = value;
            });
            persist(identity.tenantId());
        }
        return Map.of("one_time_key_counts", oneTimeKeyCounts(state));
    }

    public Map<String, Object> queryKeys(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> request) {
        prepare(identity);
        Map<String, Object> requestedUsers = objectMap(request.get("device_keys"));
        Map<String, Object> deviceKeys = new LinkedHashMap<>();
        Map<String, Object> masterKeys = new LinkedHashMap<>();
        Map<String, Object> selfSigningKeys = new LinkedHashMap<>();
        Map<String, Object> userSigningKeys = new LinkedHashMap<>();
        for (Map.Entry<String, Object> requestedUser : requestedUsers.entrySet()) {
            String userId = requestedUser.getKey();
            Set<String> requestedDevices = stringSet(requestedUser.getValue());
            Map<String, Object> userDevices = new LinkedHashMap<>();
            devices.entrySet().stream()
                    .filter(entry -> entry.getKey().tenantId().equals(identity.tenantId()))
                    .filter(entry -> entry.getKey().userId().equals(userId))
                    .filter(entry -> requestedDevices.isEmpty() || requestedDevices.contains(entry.getKey().deviceId()))
                    .filter(entry -> !entry.getValue().revoked)
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(DeviceKey::deviceId)))
                    .forEach(entry -> {
                        if (!entry.getValue().deviceKeys.isEmpty()) {
                            userDevices.put(entry.getKey().deviceId(), entry.getValue().deviceKeys);
                        }
                    });
            deviceKeys.put(userId, Map.copyOf(userDevices));
            CrossSigningState signing = crossSigning.get(new UserKey(identity.tenantId(), userId));
            if (signing != null) {
                putIfPresent(masterKeys, userId, signing.masterKey);
                putIfPresent(selfSigningKeys, userId, signing.selfSigningKey);
                putIfPresent(userSigningKeys, userId, signing.userSigningKey);
            }
        }
        return Map.of(
                "device_keys", Map.copyOf(deviceKeys),
                "master_keys", Map.copyOf(masterKeys),
                "self_signing_keys", Map.copyOf(selfSigningKeys),
                "user_signing_keys", Map.copyOf(userSigningKeys),
                "failures", Map.of());
    }

    public Map<String, Object> claimKeys(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> request) {
        prepare(identity);
        Map<String, Object> requestedUsers = objectMap(request.get("one_time_keys"));
        Map<String, Object> claimedUsers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> userEntry : requestedUsers.entrySet()) {
            Map<String, Object> requestedDevices = objectMap(userEntry.getValue());
            Map<String, Object> claimedDevices = new LinkedHashMap<>();
            for (Map.Entry<String, Object> deviceEntry : requestedDevices.entrySet()) {
                DeviceState state = devices.get(new DeviceKey(identity.tenantId(), userEntry.getKey(), deviceEntry.getKey()));
                if (state == null || state.revoked || !(deviceEntry.getValue() instanceof String algorithm)) {
                    continue;
                }
                Map<String, Object> claimed = claimOneTimeKey(state, algorithm);
                if (!claimed.isEmpty()) {
                    claimedDevices.put(deviceEntry.getKey(), claimed);
                }
            }
            if (!claimedDevices.isEmpty()) {
                claimedUsers.put(userEntry.getKey(), Map.copyOf(claimedDevices));
            }
        }
        persist(identity.tenantId());
        return Map.of("one_time_keys", Map.copyOf(claimedUsers), "failures", Map.of());
    }

    public void uploadCrossSigning(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> request) {
        prepare(identity);
        UserKey key = new UserKey(identity.tenantId(), identity.userId());
        Map<String, Object> masterKey = validatedSigningKey(request.get("master_key"), identity.userId(), "master");
        Map<String, Object> selfSigningKey =
                validatedSigningKey(request.get("self_signing_key"), identity.userId(), "self_signing");
        Map<String, Object> userSigningKey =
                validatedSigningKey(request.get("user_signing_key"), identity.userId(), "user_signing");
        sequenceJournal.publish(ignored -> {
            CrossSigningState state = crossSigning.computeIfAbsent(key, unused -> new CrossSigningState());
            state.masterKey = masterKey;
            state.selfSigningKey = selfSigningKey;
            state.userSigningKey = userSigningKey;
        });
        persist(identity.tenantId());
    }

    public Map<String, Object> uploadSignatures(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> request) {
        prepare(identity);
        request.forEach((userId, rawSignedObjects) -> objectMap(rawSignedObjects).forEach((keyId, rawSignedObject) -> {
            Map<String, Object> signedObject = immutableObject(objectMap(rawSignedObject));
            DeviceState device = devices.get(new DeviceKey(identity.tenantId(), userId, keyId));
            if (device != null) {
                sequenceJournal.publish(value -> {
                    device.deviceKeys = signedObject;
                    device.changedSequence = value;
                });
                return;
            }
            CrossSigningState signing = crossSigning.get(new UserKey(identity.tenantId(), userId));
            if (signing != null) {
                sequenceJournal.publish(ignored -> {
                    signing.replaceMatchingKey(keyId, signedObject);
                });
            }
        }));
        persist(identity.tenantId());
        return Map.of("failures", Map.of());
    }

    public void sendToDevice(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String eventType,
            String transactionId,
            Map<String, Object> request) {
        prepare(identity);
        TransactionKey transaction = new TransactionKey(identity.tenantId(), identity.userId(), transactionId);
        if (toDeviceTransactions.contains(transaction)) {
            return;
        }
        int targetCount = 0;
        List<PendingToDeviceEvent> pendingEvents = new ArrayList<>();
        for (Map.Entry<String, Object> userEntry : objectMap(request.get("messages")).entrySet()) {
            for (Map.Entry<String, Object> deviceEntry : objectMap(userEntry.getValue()).entrySet()) {
                Collection<String> targetDevices = "*".equals(deviceEntry.getKey())
                        ? activeDeviceIds(identity.tenantId(), userEntry.getKey())
                        : List.of(deviceEntry.getKey());
                for (String targetDevice : targetDevices) {
                    if (++targetCount > MAX_TO_DEVICE_TARGETS) {
                        throw new MatrixProtocolException(
                                "M_LIMIT_EXCEEDED",
                                "The Matrix to-device target limit was reached.");
                    }
                    pendingEvents.add(new PendingToDeviceEvent(
                            userEntry.getKey(),
                            targetDevice,
                            immutableObject(objectMap(deviceEntry.getValue()))));
                }
            }
        }
        boolean published = sequenceJournal.publishAllIf(
                () -> toDeviceTransactions.add(transaction),
                pendingEvents,
                (event, value) -> toDeviceEvents.add(new ToDeviceEvent(
                        value,
                        identity.tenantId(),
                        event.targetUserId(),
                        event.targetDeviceId(),
                        identity.userId(),
                        eventType,
                        event.content())));
        if (!published) {
            return;
        }
        persist(identity.tenantId());
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            long afterSequence) {
        return sync(identity, afterSequence, null);
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            long afterSequence,
            Collection<String> currentlySharedUserIds) {
        prepare(identity);
        requireActive(identity);
        Set<String> sharedUserIds = currentlySharedUserIds == null
                ? null
                : Set.copyOf(currentlySharedUserIds.stream()
                        .filter(userId -> userId != null && !userId.isBlank())
                        .filter(userId -> !userId.equals(identity.userId()))
                        .toList());
        if (sharedUserIds != null && reconcileSharedUsers(identity, sharedUserIds)) {
            // The in-memory projection is intentionally rebuilt after a server
            // restart. Persisting the advanced high-water mark makes the
            // resulting notification cursor durable while avoiding a second
            // source of truth for canonical Chat membership.
            persist(identity.tenantId());
        }
        MatrixE2eeSequenceJournal.Snapshot<MatrixProtocolCoreService.MatrixSyncCrypto> snapshot =
                sequenceJournal.snapshot(snapshotSequence -> {
                    List<Map<String, Object>> events = toDeviceEvents.stream()
                            .filter(event -> event.sequence() > afterSequence)
                            .filter(event -> event.sequence() <= snapshotSequence)
                            .filter(event -> event.tenantId().equals(identity.tenantId()))
                            .filter(event -> event.targetUserId().equals(identity.userId()))
                            .filter(event -> event.targetDeviceId().equals(identity.deviceId()))
                            .map(event -> Map.<String, Object>of(
                                    "sender", event.senderUserId(),
                                    "type", event.eventType(),
                                    "content", event.content()))
                            .toList();
                    if (!events.isEmpty()) {
                        projectedToDeviceEventCount.addAndGet(events.size());
                        syncResponsesWithToDeviceEvents.incrementAndGet();
                    }
                    Set<String> changed = new HashSet<>();
                    devices.entrySet().stream()
                            .filter(entry -> entry.getKey().tenantId().equals(identity.tenantId()))
                            .filter(entry -> sharedUserIds == null
                                    || entry.getKey().userId().equals(identity.userId())
                                    || sharedUserIds.contains(entry.getKey().userId()))
                            .filter(entry -> entry.getValue().changedSequence > afterSequence)
                            .filter(entry -> entry.getValue().changedSequence <= snapshotSequence)
                            .map(entry -> entry.getKey().userId())
                            .forEach(changed::add);
                    List<String> left = new ArrayList<>();
                    if (sharedUserIds != null) {
                        sharedUsersByDevice
                                .getOrDefault(deviceKey(identity), new ConcurrentHashMap<>())
                                .forEach((userId, state) -> {
                                    if (state.changedSequence > afterSequence
                                            && state.changedSequence <= snapshotSequence) {
                                        if (state.shared) {
                                            changed.add(userId);
                                        } else {
                                            left.add(userId);
                                        }
                                    }
                                });
                    }
                    List<String> sortedChanged = changed.stream().sorted().toList();
                    List<String> sortedLeft = left.stream().distinct().sorted().toList();
                    DeviceState ownDevice = devices.get(deviceKey(identity));
                    return new MatrixProtocolCoreService.MatrixSyncCrypto(
                            events,
                            sortedChanged,
                            sortedLeft,
                            ownDevice == null ? Map.of() : oneTimeKeyCounts(ownDevice),
                            ownDevice == null ? List.of() : fallbackAlgorithms(ownDevice),
                            snapshotSequence);
                });
        return snapshot.value();
    }

    /**
     * Returns aggregate, isolated-E2E-only evidence for the northbound Matrix
     * to-device path. The caller is a separately authenticated, run-scoped
     * proof endpoint; no tenant, user, device, room, session, key, ciphertext,
     * transaction, URL, or provider reference is included.
     */
    public SupportSafeToDeviceEvidence supportSafeToDeviceEvidence() {
        long encryptedEventCount = toDeviceEvents.stream()
                .filter(event -> "m.room.encrypted".equals(event.eventType()))
                .count();
        long plaintextRoomKeyEventCount = toDeviceEvents.stream()
                .filter(event -> "m.room_key".equals(event.eventType())
                        || "m.forwarded_room_key".equals(event.eventType()))
                .count();
        long olmPreKeyEnvelopeCount = toDeviceEvents.stream()
                .filter(event -> containsOlmMessageType(event.content(), 0))
                .count();
        long olmExistingSessionEnvelopeCount = toDeviceEvents.stream()
                .filter(event -> containsOlmMessageType(event.content(), 1))
                .count();
        long activeDeviceCount = devices.values().stream()
                .filter(device -> !device.revoked)
                .count();
        long revokedDeviceCount = devices.values().stream()
                .filter(device -> device.revoked)
                .count();
        long targetedDeviceCount = toDeviceEvents.stream()
                .map(event -> event.tenantId() + "\u0000" + event.targetUserId() + "\u0000" + event.targetDeviceId())
                .distinct()
                .count();
        return new SupportSafeToDeviceEvidence(
                "matrix-to-device-proof-v1",
                activeDeviceCount,
                revokedDeviceCount,
                toDeviceEvents.size(),
                encryptedEventCount,
                plaintextRoomKeyEventCount,
                olmPreKeyEnvelopeCount,
                olmExistingSessionEnvelopeCount,
                targetedDeviceCount,
                toDeviceTransactions.size(),
                projectedToDeviceEventCount.get(),
                syncResponsesWithToDeviceEvents.get(),
                sequenceJournal.current(),
                true);
    }

    private boolean containsOlmMessageType(Map<String, Object> content, int expectedType) {
        Object rawCiphertext = content.get("ciphertext");
        if (!(rawCiphertext instanceof Map<?, ?> ciphertext)) {
            return false;
        }
        return ciphertext.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(value -> value.get("type"))
                .anyMatch(value -> value instanceof Number number && number.intValue() == expectedType);
    }

    private synchronized boolean reconcileSharedUsers(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Set<String> currentlySharedUserIds) {
        ConcurrentMap<String, SharedUserState> known = sharedUsersByDevice.computeIfAbsent(
                deviceKey(identity), ignored -> new ConcurrentHashMap<>());
        boolean mutated = false;
        for (String userId : currentlySharedUserIds) {
            SharedUserState state = known.computeIfAbsent(userId, ignored -> new SharedUserState());
            if (!state.shared) {
                sequenceJournal.publish(value -> {
                    state.shared = true;
                    state.changedSequence = value;
                });
                mutated = true;
            }
        }
        for (Map.Entry<String, SharedUserState> entry : known.entrySet()) {
            SharedUserState state = entry.getValue();
            if (state.shared && !currentlySharedUserIds.contains(entry.getKey())) {
                sequenceJournal.publish(value -> {
                    state.shared = false;
                    state.changedSequence = value;
                });
                mutated = true;
            }
        }
        return mutated;
    }

    public Map<String, Object> keyChanges(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            long afterSequence) {
        prepare(identity);
        List<String> changed = sequenceJournal.snapshot(snapshotSequence -> devices.entrySet().stream()
                        .filter(entry -> entry.getKey().tenantId().equals(identity.tenantId()))
                        .filter(entry -> entry.getValue().changedSequence > afterSequence)
                        .filter(entry -> entry.getValue().changedSequence <= snapshotSequence)
                        .map(entry -> entry.getKey().userId())
                        .distinct()
                        .sorted()
                        .toList())
                .value();
        return Map.of("changed", changed, "left", List.of());
    }

    public long currentSequence() {
        return sequenceJournal.current();
    }

    public String combinedCursor(String chatCursor, long cryptoSequence) {
        if (cryptoSequence < 0) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid.");
        }
        return chatCursor + "|e2ee:" + cryptoSequence;
    }

    public long cryptoSequence(String decodedCursor) {
        if (decodedCursor == null || decodedCursor.isBlank()) {
            return 0;
        }
        int marker = decodedCursor.lastIndexOf("|e2ee:");
        if (marker < 0) {
            return 0;
        }
        try {
            return Long.parseLong(decodedCursor.substring(marker + "|e2ee:".length()));
        } catch (NumberFormatException exception) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid.");
        }
    }

    public void revokeDevice(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String deviceId) {
        prepare(identity);
        DeviceState state = devices.get(new DeviceKey(identity.tenantId(), identity.userId(), deviceId));
        if (state == null) {
            throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix device was not found.");
        }
        sequenceJournal.publish(value -> {
            state.revoked = true;
            state.oneTimeKeys.clear();
            state.fallbackKeys = Map.of();
            state.changedSequence = value;
        });
        sharedUsersByDevice.remove(new DeviceKey(identity.tenantId(), identity.userId(), deviceId));
        persist(identity.tenantId());
    }

    public void requireActive(MatrixFacadeClientStateService.MatrixIdentity identity) {
        prepare(identity);
        bindOidcSession(identity);
        DeviceState state = devices.get(deviceKey(identity));
        if (state != null && state.revoked) {
            throw new MatrixProtocolException("M_UNKNOWN_TOKEN", "The Matrix device was revoked.");
        }
    }

    public Map<String, Object> createBackupVersion(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            Map<String, Object> request) {
        prepare(identity);
        String algorithm = requiredText(request.get("algorithm"), "backup algorithm", 128);
        Map<String, Object> authData = immutableObject(objectMap(request.get("auth_data")));
        UserKey user = new UserKey(identity.tenantId(), identity.userId());
        String version = Long.toString(backupVersionSequences
                .computeIfAbsent(user, ignored -> new AtomicLong())
                .incrementAndGet());
        ConcurrentMap<String, BackupVersion> userBackups = backups.computeIfAbsent(
                user,
                ignored -> new ConcurrentHashMap<>());
        userBackups.values().forEach(existing -> existing.current = false);
        userBackups.put(version, new BackupVersion(version, algorithm, authData, true));
        persist(identity.tenantId());
        return Map.of("version", version);
    }

    public Map<String, Object> backupVersion(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String requestedVersion) {
        prepare(identity);
        BackupVersion backup = requireBackup(identity, requestedVersion);
        return Map.of(
                "algorithm", backup.algorithm,
                "auth_data", backup.authData,
                "count", backup.sessions.size(),
                "etag", backup.etag(),
                "version", backup.version);
    }

    public void updateBackupVersion(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String version,
            Map<String, Object> request) {
        prepare(identity);
        BackupVersion backup = requireBackup(identity, version);
        backup.algorithm = requiredText(request.get("algorithm"), "backup algorithm", 128);
        backup.authData = immutableObject(objectMap(request.get("auth_data")));
        backup.revision.incrementAndGet();
        persist(identity.tenantId());
    }

    public void deleteBackupVersion(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String version) {
        prepare(identity);
        UserKey user = new UserKey(identity.tenantId(), identity.userId());
        BackupVersion removed = backups.getOrDefault(user, new ConcurrentHashMap<>()).remove(version);
        if (removed == null) {
            throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup version was not found.");
        }
        persist(identity.tenantId());
    }

    public Map<String, Object> putBackupKeys(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String version,
            String roomId,
            String sessionId,
            Map<String, Object> request) {
        prepare(identity);
        BackupVersion backup = requireBackup(identity, version);
        if (sessionId != null) {
            backup.sessions.put(new BackupSessionKey(requiredPath(roomId), requiredPath(sessionId)), immutableObject(request));
        } else if (roomId != null) {
            objectMap(request.get("sessions")).forEach((id, payload) -> backup.sessions.put(
                    new BackupSessionKey(requiredPath(roomId), requiredPath(id)),
                    immutableObject(objectMap(payload))));
        } else {
            objectMap(request.get("rooms")).forEach((room, rawRoom) -> objectMap(objectMap(rawRoom).get("sessions"))
                    .forEach((id, payload) -> backup.sessions.put(
                            new BackupSessionKey(requiredPath(room), requiredPath(id)),
                            immutableObject(objectMap(payload)))));
        }
        if (backup.sessions.size() > 100_000) {
            throw new MatrixProtocolException("M_LIMIT_EXCEEDED", "The Matrix room-key backup limit was reached.");
        }
        backup.revision.incrementAndGet();
        persist(identity.tenantId());
        return Map.of("count", backup.sessions.size(), "etag", backup.etag());
    }

    public Map<String, Object> backupKeys(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String version,
            String roomId,
            String sessionId) {
        prepare(identity);
        BackupVersion backup = requireBackup(identity, version);
        if (sessionId != null) {
            Map<String, Object> payload = backup.sessions.get(new BackupSessionKey(requiredPath(roomId), requiredPath(sessionId)));
            if (payload == null) {
                throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup session was not found.");
            }
            return payload;
        }
        Map<String, Object> rooms = new LinkedHashMap<>();
        backup.sessions.entrySet().stream()
                .filter(entry -> roomId == null || entry.getKey().roomId().equals(roomId))
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(BackupSessionKey::roomId)
                        .thenComparing(BackupSessionKey::sessionId)))
                .forEach(entry -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> room = (Map<String, Object>) rooms.computeIfAbsent(
                            entry.getKey().roomId(),
                            ignored -> new LinkedHashMap<String, Object>());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sessions = (Map<String, Object>) room.computeIfAbsent(
                            "sessions",
                            ignored -> new LinkedHashMap<String, Object>());
                    sessions.put(entry.getKey().sessionId(), entry.getValue());
                });
        if (roomId != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> room = (Map<String, Object>) rooms.getOrDefault(roomId, Map.of("sessions", Map.of()));
            return Map.copyOf(room);
        }
        return Map.of("rooms", immutableObject(rooms));
    }

    public void putAccountData(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String eventType,
            Map<String, Object> content) {
        prepare(identity);
        String validatedEventType = requiredText(eventType, "account-data event type", 255);
        sequenceJournal.publish(unusedSequence -> {
            accountData.computeIfAbsent(
                            new UserKey(identity.tenantId(), identity.userId()),
                            ignored -> new ConcurrentHashMap<>())
                    .put(validatedEventType, immutableObject(content));
        });
        persist(identity.tenantId());
    }

    public Map<String, Object> accountData(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String eventType) {
        prepare(identity);
        Map<String, Object> content = accountData
                .getOrDefault(new UserKey(identity.tenantId(), identity.userId()), new ConcurrentHashMap<>())
                .get(requiredText(eventType, "account-data event type", 255));
        if (content == null) {
            throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix account-data event was not found.");
        }
        return content;
    }

    public Map<String, Object> accountData(MatrixFacadeClientStateService.MatrixIdentity identity) {
        prepare(identity);
        return Map.copyOf(accountData.getOrDefault(
                new UserKey(identity.tenantId(), identity.userId()),
                new ConcurrentHashMap<>()));
    }

    public void deleteBackupKeys(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String version,
            String roomId,
            String sessionId) {
        prepare(identity);
        BackupVersion backup = requireBackup(identity, version);
        if (sessionId != null) {
            backup.sessions.remove(new BackupSessionKey(requiredPath(roomId), requiredPath(sessionId)));
        } else if (roomId != null) {
            backup.sessions.keySet().removeIf(key -> key.roomId().equals(roomId));
        } else {
            backup.sessions.clear();
        }
        backup.revision.incrementAndGet();
        persist(identity.tenantId());
    }

    private synchronized void prepare(MatrixFacadeClientStateService.MatrixIdentity identity) {
        String tenantId = identity.tenantId();
        if (!loadedTenants.add(tenantId) || snapshotStore == null || !snapshotStore.durable()) {
            return;
        }
        try {
            snapshotStore.load(tenantId).ifPresent(document -> restoreSnapshot(
                    tenantId,
                    document.sequence(),
                    document.payloadJson()));
        } catch (RuntimeException exception) {
            loadedTenants.remove(tenantId);
            throw new MatrixProtocolException("M_UNAVAILABLE", "Matrix E2EE state could not be restored.");
        }
    }

    private synchronized void persist(String tenantId) {
        if (snapshotStore == null || !snapshotStore.durable()) {
            return;
        }
        try {
            MatrixE2eeSequenceJournal.Snapshot<PersistedSnapshot> captured =
                    sequenceJournal.snapshot(ignored -> snapshot(tenantId));
            snapshotStore.save(
                    tenantId,
                    captured.highWater(),
                    objectMapper.writeValueAsString(captured.value()));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new MatrixProtocolException("M_UNAVAILABLE", "Matrix E2EE state could not be persisted.");
        }
    }

    private PersistedSnapshot snapshot(String tenantId) {
        List<PersistedDevice> persistedDevices = devices.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId().equals(tenantId))
                .map(entry -> new PersistedDevice(
                        entry.getKey().userId(),
                        entry.getKey().deviceId(),
                        entry.getValue().deviceKeys,
                        Map.copyOf(entry.getValue().oneTimeKeys),
                        entry.getValue().fallbackKeys,
                        Set.copyOf(entry.getValue().usedFallbackAlgorithms),
                        entry.getValue().changedSequence,
                        entry.getValue().revoked))
                .toList();
        List<PersistedCrossSigning> persistedCrossSigning = crossSigning.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId().equals(tenantId))
                .map(entry -> new PersistedCrossSigning(
                        entry.getKey().userId(),
                        entry.getValue().masterKey,
                        entry.getValue().selfSigningKey,
                        entry.getValue().userSigningKey))
                .toList();
        List<ToDeviceEvent> persistedEvents = toDeviceEvents.stream()
                .filter(event -> event.tenantId().equals(tenantId))
                .toList();
        List<TransactionKey> persistedTransactions = toDeviceTransactions.stream()
                .filter(transaction -> transaction.tenantId().equals(tenantId))
                .toList();
        List<PersistedBackup> persistedBackups = backups.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId().equals(tenantId))
                .flatMap(entry -> entry.getValue().values().stream().map(backup -> new PersistedBackup(
                        entry.getKey().userId(),
                        backup.version,
                        backup.algorithm,
                        backup.authData,
                        backup.current,
                        backup.revision.get(),
                        backup.sessions.entrySet().stream()
                                .map(session -> new PersistedBackupSession(
                                        session.getKey().roomId(),
                                        session.getKey().sessionId(),
                                        session.getValue()))
                                .toList())))
                .toList();
        Map<String, Long> persistedBackupSequences = new LinkedHashMap<>();
        backupVersionSequences.forEach((user, value) -> {
            if (user.tenantId().equals(tenantId)) {
                persistedBackupSequences.put(user.userId(), value.get());
            }
        });
        Map<String, Map<String, Map<String, Object>>> persistedAccountData = new LinkedHashMap<>();
        accountData.forEach((user, events) -> {
            if (user.tenantId().equals(tenantId)) {
                persistedAccountData.put(user.userId(), Map.copyOf(events));
            }
        });
        List<PersistedOidcSessionBinding> persistedSessionBindings = devicesByOidcSession.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId().equals(tenantId))
                .map(entry -> new PersistedOidcSessionBinding(
                        entry.getKey().userId(),
                        entry.getKey().sessionHash(),
                        entry.getValue()))
                .toList();
        return new PersistedSnapshot(
                persistedDevices,
                persistedCrossSigning,
                persistedEvents,
                persistedTransactions,
                persistedBackups,
                Map.copyOf(persistedBackupSequences),
                Map.copyOf(persistedAccountData),
                persistedSessionBindings);
    }

    private void restoreSnapshot(String tenantId, long persistedSequence, String payloadJson) {
        try {
            PersistedSnapshot snapshot = objectMapper.readValue(payloadJson, PersistedSnapshot.class);
            sequenceJournal.restore(persistedSequence, () -> {
                for (PersistedDevice persisted : snapshot.devices()) {
                    DeviceState state = new DeviceState();
                    state.deviceKeys = immutableObject(persisted.deviceKeys());
                    persisted.oneTimeKeys().forEach(
                            (key, value) -> state.oneTimeKeys.put(key, immutableValue(value)));
                    state.fallbackKeys = immutableObject(persisted.fallbackKeys());
                    state.usedFallbackAlgorithms.addAll(persisted.usedFallbackAlgorithms());
                    state.changedSequence = persisted.changedSequence();
                    state.revoked = persisted.revoked();
                    devices.put(new DeviceKey(tenantId, persisted.userId(), persisted.deviceId()), state);
                }
                for (PersistedCrossSigning persisted : snapshot.crossSigning()) {
                    CrossSigningState state = new CrossSigningState();
                    state.masterKey = immutableObject(persisted.masterKey());
                    state.selfSigningKey = immutableObject(persisted.selfSigningKey());
                    state.userSigningKey = immutableObject(persisted.userSigningKey());
                    crossSigning.put(new UserKey(tenantId, persisted.userId()), state);
                }
                toDeviceEvents.addAll(snapshot.toDeviceEvents());
                toDeviceTransactions.addAll(snapshot.toDeviceTransactions());
                for (PersistedBackup persisted : snapshot.backups()) {
                    BackupVersion backup = new BackupVersion(
                            persisted.version(),
                            persisted.algorithm(),
                            immutableObject(persisted.authData()),
                            persisted.current(),
                            persisted.revision());
                    persisted.sessions().forEach(session -> backup.sessions.put(
                            new BackupSessionKey(session.roomId(), session.sessionId()),
                            immutableObject(session.payload())));
                    backups.computeIfAbsent(
                                    new UserKey(tenantId, persisted.userId()),
                                    ignored -> new ConcurrentHashMap<>())
                            .put(persisted.version(), backup);
                }
                snapshot.backupVersionSequences().forEach((userId, value) -> backupVersionSequences.put(
                        new UserKey(tenantId, userId),
                        new AtomicLong(value)));
                snapshot.accountData().forEach((userId, events) -> accountData.put(
                        new UserKey(tenantId, userId),
                        new ConcurrentHashMap<>(events)));
                snapshot.oidcSessionBindings().forEach(binding -> devicesByOidcSession.put(
                        new OidcSessionKey(tenantId, binding.userId(), binding.sessionHash()),
                        binding.deviceId()));
            });
        } catch (JsonProcessingException exception) {
            throw new MatrixProtocolException("M_UNAVAILABLE", "Matrix E2EE state snapshot is invalid.");
        }
    }

    private DeviceKey deviceKey(MatrixFacadeClientStateService.MatrixIdentity identity) {
        return new DeviceKey(identity.tenantId(), identity.userId(), identity.deviceId());
    }

    private void bindOidcSession(MatrixFacadeClientStateService.MatrixIdentity identity) {
        if (identity.oidcSessionHash() == null || identity.oidcSessionHash().isBlank()) {
            return;
        }
        OidcSessionKey key = new OidcSessionKey(
                identity.tenantId(),
                identity.userId(),
                identity.oidcSessionHash());
        String existingDevice = devicesByOidcSession.putIfAbsent(key, identity.deviceId());
        if (existingDevice != null && !existingDevice.equals(identity.deviceId())) {
            throw new MatrixProtocolException(
                    "M_UNKNOWN_TOKEN",
                    "The OIDC session is bound to a different Matrix device.");
        }
        if (existingDevice == null) {
            persist(identity.tenantId());
        }
    }

    private BackupVersion requireBackup(
            MatrixFacadeClientStateService.MatrixIdentity identity,
            String requestedVersion) {
        UserKey user = new UserKey(identity.tenantId(), identity.userId());
        Collection<BackupVersion> userBackups = backups.getOrDefault(user, new ConcurrentHashMap<>()).values();
        BackupVersion backup = requestedVersion == null || requestedVersion.isBlank()
                ? userBackups.stream().filter(value -> value.current).findFirst().orElse(null)
                : userBackups.stream().filter(value -> value.version.equals(requestedVersion)).findFirst().orElse(null);
        if (backup == null) {
            throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup version was not found.");
        }
        return backup;
    }

    private String requiredText(Object raw, String field, int maxLength) {
        if (!(raw instanceof String value) || value.isBlank() || value.length() > maxLength) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix " + field + " is invalid.");
        }
        return value;
    }

    private String requiredPath(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "The Matrix backup path is invalid.");
        }
        return value;
    }

    private Map<String, Long> oneTimeKeyCounts(DeviceState state) {
        Map<String, Long> counts = new LinkedHashMap<>();
        state.oneTimeKeys.keySet().forEach(keyId -> counts.merge(algorithm(keyId), 1L, Long::sum));
        return Map.copyOf(counts);
    }

    private List<String> fallbackAlgorithms(DeviceState state) {
        return state.fallbackKeys.keySet().stream()
                .map(this::algorithm)
                .filter(algorithm -> !state.usedFallbackAlgorithms.contains(algorithm))
                .distinct()
                .sorted()
                .toList();
    }

    private synchronized Map<String, Object> claimOneTimeKey(DeviceState state, String algorithm) {
        Map<String, Object> oneTimeKey = state.oneTimeKeys.entrySet().stream()
                .filter(entry -> algorithm(entry.getKey()).equals(algorithm))
                .sorted(Map.Entry.comparingByKey())
                .findFirst()
                .map(entry -> {
                    state.oneTimeKeys.remove(entry.getKey());
                    return Map.<String, Object>of(entry.getKey(), entry.getValue());
                })
                .orElse(Map.of());
        if (!oneTimeKey.isEmpty()) {
            return oneTimeKey;
        }
        return state.fallbackKeys.entrySet().stream()
                .filter(entry -> algorithm(entry.getKey()).equals(algorithm))
                .sorted(Map.Entry.comparingByKey())
                .findFirst()
                .map(entry -> {
                    state.usedFallbackAlgorithms.add(algorithm);
                    return Map.<String, Object>of(entry.getKey(), entry.getValue());
                })
                .orElse(Map.of());
    }

    private String algorithm(String keyId) {
        int separator = keyId.indexOf(':');
        return separator > 0 ? keyId.substring(0, separator) : keyId;
    }

    private Collection<String> activeDeviceIds(String tenantId, String userId) {
        return devices.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId().equals(tenantId))
                .filter(entry -> entry.getKey().userId().equals(userId))
                .filter(entry -> !entry.getValue().revoked)
                .map(entry -> entry.getKey().deviceId())
                .sorted()
                .toList();
    }

    private Map<String, Object> validatedSigningKey(Object value, String userId, String usage) {
        Map<String, Object> key = objectMap(value);
        if (key.isEmpty()) {
            return Map.of();
        }
        requireEquals(key.get("user_id"), userId, "cross-signing user");
        if (!stringSet(key.get("usage")).contains(usage)) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix cross-signing usage is invalid.");
        }
        return immutableObject(key);
    }

    private void requireEquals(Object actual, String expected, String field) {
        if (!(actual instanceof String value) || !expected.equals(value)) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix " + field + " does not match the OIDC device session.");
        }
    }

    private String requireKeyId(String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9._=-]+:[A-Za-z0-9._=-]{1,256}")) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix key identifier is invalid.");
        }
        return keyId;
    }

    private void putIfPresent(Map<String, Object> target, String key, Map<String, Object> value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix request object is invalid.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> {
            if (!(key instanceof String text)) {
                throw new MatrixProtocolException("M_BAD_JSON", "The Matrix request object key is invalid.");
            }
            result.put(text, nested);
        });
        return result;
    }

    private Set<String> stringSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> values)) {
            throw new MatrixProtocolException("M_BAD_JSON", "The Matrix string list is invalid.");
        }
        Set<String> result = ConcurrentHashMap.newKeySet();
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new MatrixProtocolException("M_BAD_JSON", "The Matrix string list item is invalid.");
            }
            result.add(text);
        }
        return Set.copyOf(result);
    }

    private Object immutableValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return immutableObject(objectMap(value));
        }
        if (value instanceof Collection<?> values) {
            return List.copyOf(values.stream().map(this::immutableValue).toList());
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw new MatrixProtocolException("M_BAD_JSON", "The Matrix request value is invalid.");
    }

    private Map<String, Object> immutableObject(Map<String, Object> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, nested) -> result.put(key, immutableValue(nested)));
        return Map.copyOf(result);
    }

    private record DeviceKey(String tenantId, String userId, String deviceId) {
    }

    private record UserKey(String tenantId, String userId) {
    }

    private record OidcSessionKey(String tenantId, String userId, String sessionHash) {
    }

    private record TransactionKey(String tenantId, String userId, String transactionId) {
    }

    private record BackupSessionKey(String roomId, String sessionId) {
    }

    private record PendingToDeviceEvent(
            String targetUserId,
            String targetDeviceId,
            Map<String, Object> content) {
    }

    private record ToDeviceEvent(
            long sequence,
            String tenantId,
            String targetUserId,
            String targetDeviceId,
            String senderUserId,
            String eventType,
            Map<String, Object> content) {
    }

    private static final class DeviceState {
        private volatile Map<String, Object> deviceKeys = Map.of();
        private final ConcurrentMap<String, Object> oneTimeKeys = new ConcurrentHashMap<>();
        private volatile Map<String, Object> fallbackKeys = Map.of();
        private final Set<String> usedFallbackAlgorithms = ConcurrentHashMap.newKeySet();
        private volatile long changedSequence;
        private volatile boolean revoked;
    }

    private static final class SharedUserState {
        private volatile long changedSequence;
        private volatile boolean shared;
    }

    public record SupportSafeToDeviceEvidence(
            String contractVersion,
            long activeDeviceCount,
            long revokedDeviceCount,
            long queuedEventCount,
            long encryptedEventCount,
            long plaintextRoomKeyEventCount,
            long olmPreKeyEnvelopeCount,
            long olmExistingSessionEnvelopeCount,
            long targetedDeviceCount,
            long transactionCount,
            long projectedEventCount,
            long syncResponseCount,
            long sequenceHighWater,
            boolean supportSafe) {
    }

    private static final class CrossSigningState {
        private volatile Map<String, Object> masterKey = Map.of();
        private volatile Map<String, Object> selfSigningKey = Map.of();
        private volatile Map<String, Object> userSigningKey = Map.of();

        private void replaceMatchingKey(String keyId, Map<String, Object> signedObject) {
            if (containsKeyId(masterKey, keyId)) {
                masterKey = signedObject;
            } else if (containsKeyId(selfSigningKey, keyId)) {
                selfSigningKey = signedObject;
            } else if (containsKeyId(userSigningKey, keyId)) {
                userSigningKey = signedObject;
            }
        }

        private boolean containsKeyId(Map<String, Object> key, String keyId) {
            Object rawKeys = key.get("keys");
            return rawKeys instanceof Map<?, ?> keys && keys.containsKey(keyId);
        }
    }

    private static final class BackupVersion {
        private final String version;
        private volatile String algorithm;
        private volatile Map<String, Object> authData;
        private volatile boolean current;
        private final ConcurrentMap<BackupSessionKey, Map<String, Object>> sessions = new ConcurrentHashMap<>();
        private final AtomicLong revision = new AtomicLong(1);

        private BackupVersion(String version, String algorithm, Map<String, Object> authData, boolean current) {
            this(version, algorithm, authData, current, 1);
        }

        private BackupVersion(
                String version,
                String algorithm,
                Map<String, Object> authData,
                boolean current,
                long revision) {
            this.version = version;
            this.algorithm = algorithm;
            this.authData = authData;
            this.current = current;
            this.revision.set(revision);
        }

        private String etag() {
            return "weave-backup-" + revision.get();
        }
    }

    private record PersistedSnapshot(
            List<PersistedDevice> devices,
            List<PersistedCrossSigning> crossSigning,
            List<ToDeviceEvent> toDeviceEvents,
            List<TransactionKey> toDeviceTransactions,
            List<PersistedBackup> backups,
            Map<String, Long> backupVersionSequences,
            Map<String, Map<String, Map<String, Object>>> accountData,
            List<PersistedOidcSessionBinding> oidcSessionBindings) {

        private PersistedSnapshot {
            devices = devices == null ? List.of() : List.copyOf(devices);
            crossSigning = crossSigning == null ? List.of() : List.copyOf(crossSigning);
            toDeviceEvents = toDeviceEvents == null ? List.of() : List.copyOf(toDeviceEvents);
            toDeviceTransactions = toDeviceTransactions == null ? List.of() : List.copyOf(toDeviceTransactions);
            backups = backups == null ? List.of() : List.copyOf(backups);
            backupVersionSequences = backupVersionSequences == null ? Map.of() : Map.copyOf(backupVersionSequences);
            accountData = accountData == null ? Map.of() : Map.copyOf(accountData);
            oidcSessionBindings = oidcSessionBindings == null ? List.of() : List.copyOf(oidcSessionBindings);
        }
    }

    private record PersistedOidcSessionBinding(String userId, String sessionHash, String deviceId) {
    }

    private record PersistedDevice(
            String userId,
            String deviceId,
            Map<String, Object> deviceKeys,
            Map<String, Object> oneTimeKeys,
            Map<String, Object> fallbackKeys,
            Set<String> usedFallbackAlgorithms,
            long changedSequence,
            boolean revoked) {
        private PersistedDevice {
            usedFallbackAlgorithms = usedFallbackAlgorithms == null
                    ? Set.of()
                    : Set.copyOf(usedFallbackAlgorithms);
        }
    }

    private record PersistedCrossSigning(
            String userId,
            Map<String, Object> masterKey,
            Map<String, Object> selfSigningKey,
            Map<String, Object> userSigningKey) {
    }

    private record PersistedBackup(
            String userId,
            String version,
            String algorithm,
            Map<String, Object> authData,
            boolean current,
            long revision,
            List<PersistedBackupSession> sessions) {
    }

    private record PersistedBackupSession(
            String roomId,
            String sessionId,
            Map<String, Object> payload) {
    }
}
