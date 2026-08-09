package com.massimotter.weave.backend.matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fast service-test implementation of the same operation-level E2EE persistence port. */
final class InMemoryMatrixE2eeRelationalStore implements MatrixE2eePersistence {

    private final Map<String, AtomicLong> revisions = new ConcurrentHashMap<>();
    private final Map<DeviceKey, DeviceState> devices = new ConcurrentHashMap<>();
    private final Map<DeviceKey, Map<String, Object>> oneTimeKeys = new ConcurrentHashMap<>();
    private final Map<DeviceKey, Map<String, FallbackState>> fallbackKeys = new ConcurrentHashMap<>();
    private final Map<UserKey, CrossSigningRecord> signing = new ConcurrentHashMap<>();
    private final List<ToDeviceState> toDevice = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Set<String> toDeviceTransactions = ConcurrentHashMap.newKeySet();
    private final Map<DeviceKey, Long> syncProgress = new ConcurrentHashMap<>();
    private final Map<SharedKey, DeviceListState> sharedUsers = new ConcurrentHashMap<>();
    private final List<SharedChange> sharedUserChanges = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<OidcKey, String> oidcBindings = new ConcurrentHashMap<>();
    private final Map<UserKey, Map<String, BackupState>> backups = new ConcurrentHashMap<>();
    private final Map<UserKey, AtomicLong> backupSequences = new ConcurrentHashMap<>();
    private final Map<UserKey, Map<String, Map<String, Object>>> accountData = new ConcurrentHashMap<>();

    @Override
    public long currentRevision(String tenantId) {
        return revisions.computeIfAbsent(tenantId, ignored -> new AtomicLong()).get();
    }

    @Override
    public long nextRevision(String tenantId) {
        return revisions.computeIfAbsent(tenantId, ignored -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public Optional<DeviceRecord> device(String tenantId, String userId, String deviceId) {
        DeviceState state = devices.get(new DeviceKey(tenantId, userId, deviceId));
        return state == null ? Optional.empty() : Optional.of(state.record(userId, deviceId));
    }

    @Override
    public List<DeviceRecord> devices(String tenantId, String userId, Set<String> requestedDeviceIds) {
        return devices.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId.equals(tenantId) && entry.getKey().userId.equals(userId))
                .filter(entry -> requestedDeviceIds == null || requestedDeviceIds.isEmpty() || requestedDeviceIds.contains(entry.getKey().deviceId))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().record(userId, entry.getKey().deviceId))
                .toList();
    }

    @Override
    public Collection<String> activeDeviceIds(String tenantId, String userId) {
        return devices.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId.equals(tenantId) && entry.getKey().userId.equals(userId))
                .filter(entry -> !entry.getValue().revoked)
                .map(entry -> entry.getKey().deviceId)
                .sorted()
                .toList();
    }

    @Override
    public void upsertDevice(String tenantId, String userId, String deviceId, Map<String, Object> deviceKeys, long changedRevision) {
        DeviceState state = devices.computeIfAbsent(new DeviceKey(tenantId, userId, deviceId), ignored -> new DeviceState());
        if (deviceKeys != null && !deviceKeys.isEmpty()) state.deviceKeys = Map.copyOf(deviceKeys);
        state.changedRevision = changedRevision;
    }

    @Override
    public void addOneTimeKeys(String tenantId, String userId, String deviceId, Map<String, Object> keys) {
        oneTimeKeys.computeIfAbsent(new DeviceKey(tenantId, userId, deviceId), ignored -> new ConcurrentHashMap<>()).putAll(keys);
    }

    @Override
    public void replaceFallbackKeys(String tenantId, String userId, String deviceId, Map<String, Object> keys, long changedRevision) {
        Map<String, FallbackState> replacement = new ConcurrentHashMap<>();
        keys.forEach((key, value) -> replacement.put(key, new FallbackState(value, false)));
        fallbackKeys.put(new DeviceKey(tenantId, userId, deviceId), replacement);
        upsertDevice(tenantId, userId, deviceId, Map.of(), changedRevision);
    }

    @Override
    public Map<String, Long> oneTimeKeyCounts(String tenantId, String userId, String deviceId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        oneTimeKeys.getOrDefault(new DeviceKey(tenantId, userId, deviceId), Map.of())
                .keySet().forEach(key -> counts.merge(algorithm(key), 1L, Long::sum));
        return Map.copyOf(counts);
    }

    @Override
    public List<String> unusedFallbackAlgorithms(String tenantId, String userId, String deviceId) {
        return fallbackKeys.getOrDefault(new DeviceKey(tenantId, userId, deviceId), Map.of()).entrySet().stream()
                .filter(entry -> !entry.getValue().used)
                .map(entry -> algorithm(entry.getKey()))
                .distinct().sorted().toList();
    }

    @Override
    public synchronized Optional<ClaimedKey> claimOneTimeKey(String tenantId, String userId, String deviceId, String requestedAlgorithm) {
        DeviceKey device = new DeviceKey(tenantId, userId, deviceId);
        Map<String, Object> otk = oneTimeKeys.getOrDefault(device, Map.of());
        String keyId = otk.keySet().stream().filter(key -> algorithm(key).equals(requestedAlgorithm)).sorted().findFirst().orElse(null);
        if (keyId != null) {
            Object value = oneTimeKeys.get(device).remove(keyId);
            return Optional.of(new ClaimedKey(keyId, value, false));
        }
        Map<String, FallbackState> fallback = fallbackKeys.getOrDefault(device, Map.of());
        String fallbackId = fallback.keySet().stream().filter(key -> algorithm(key).equals(requestedAlgorithm)).sorted().findFirst().orElse(null);
        if (fallbackId == null) return Optional.empty();
        FallbackState state = fallback.get(fallbackId);
        state.used = true;
        return Optional.of(new ClaimedKey(fallbackId, state.value, true));
    }

    @Override
    public Optional<CrossSigningRecord> crossSigning(String tenantId, String userId) {
        return Optional.ofNullable(signing.get(new UserKey(tenantId, userId)));
    }

    @Override
    public void upsertCrossSigning(String tenantId, String userId, Map<String, Object> masterKey, Map<String, Object> selfSigningKey, Map<String, Object> userSigningKey, long changedRevision) {
        signing.put(new UserKey(tenantId, userId), new CrossSigningRecord(masterKey, selfSigningKey, userSigningKey));
    }

    @Override
    public void mergeDeviceSignatures(String tenantId, String userId, String deviceId, Map<String, Object> signatures, long changedRevision) {
        DeviceKey key = new DeviceKey(tenantId, userId, deviceId);
        DeviceState state = devices.get(key);
        if (state == null) return;
        state.deviceKeys = mergeSignatures(state.deviceKeys, signatures);
        state.changedRevision = changedRevision;
    }

    @Override
    public void mergeCrossSigningSignatures(String tenantId, String userId, String keyId, Map<String, Object> signatures, long changedRevision) {
        UserKey key = new UserKey(tenantId, userId);
        CrossSigningRecord record = signing.get(key);
        if (record == null) return;
        Map<String, Object> master = matches(record.masterKey(), keyId) ? mergeSignatures(record.masterKey(), signatures) : record.masterKey();
        Map<String, Object> self = matches(record.selfSigningKey(), keyId) ? mergeSignatures(record.selfSigningKey(), signatures) : record.selfSigningKey();
        Map<String, Object> user = matches(record.userSigningKey(), keyId) ? mergeSignatures(record.userSigningKey(), signatures) : record.userSigningKey();
        signing.put(key, new CrossSigningRecord(master, self, user));
    }

    @Override
    public synchronized long appendToDevice(String tenantId, String targetUserId, String targetDeviceId, String senderUserId, String eventType, String transactionId, Map<String, Object> content) {
        String transactionKey = String.join("\u0000", tenantId, senderUserId, transactionId, targetUserId, targetDeviceId, eventType);
        ToDeviceState existing = toDevice.stream()
                .filter(event -> event.transactionKey.equals(transactionKey))
                .findFirst()
                .orElse(null);
        if (existing != null) return existing.revision;
        if (!toDeviceTransactions.add(transactionKey)) {
            return toDevice.stream().filter(event -> event.transactionKey.equals(transactionKey)).findFirst().orElseThrow().revision;
        }
        long revision = revisions.computeIfAbsent(tenantId, ignored -> new AtomicLong()).incrementAndGet();
        toDevice.add(new ToDeviceState(revision, tenantId, targetUserId, targetDeviceId, senderUserId, eventType, transactionId, transactionKey, Map.copyOf(content)));
        return revision;
    }

    @Override
    public List<ToDeviceRecord> toDeviceEvents(String tenantId, String userId, String deviceId, long afterRevision, long highWater, int limit) {
        synchronized (toDevice) {
            return toDevice.stream()
                    .filter(event -> event.tenantId.equals(tenantId) && event.targetUserId.equals(userId) && event.targetDeviceId.equals(deviceId))
                    .filter(event -> event.revision > afterRevision && event.revision <= highWater)
                    .sorted(java.util.Comparator.comparingLong(event -> event.revision))
                    .limit(limit)
                    .map(event -> new ToDeviceRecord(event.revision, event.senderUserId, event.eventType, event.content))
                    .toList();
        }
    }

    @Override
    public void recordDeviceSyncProgress(String tenantId, String userId, String deviceId, long revision) {
        syncProgress.merge(new DeviceKey(tenantId, userId, deviceId), revision, Math::max);
    }

    @Override
    public Map<String, DeviceListState> reconcileSharedUsers(String tenantId, String userId, String deviceId, Set<String> currentlySharedUserIds) {
        Set<String> existing = new LinkedHashSet<>();
        sharedUsers.keySet().stream()
                .filter(key -> key.tenantId.equals(tenantId) && key.userId.equals(userId) && key.deviceId.equals(deviceId))
                .map(key -> key.sharedUserId).forEach(existing::add);
        existing.addAll(currentlySharedUserIds);
        Map<String, DeviceListState> changed = new LinkedHashMap<>();
        for (String sharedUser : existing) {
            SharedKey key = new SharedKey(tenantId, userId, deviceId, sharedUser);
            boolean desired = currentlySharedUserIds.contains(sharedUser);
            DeviceListState current = sharedUsers.get(key);
            if (current == null || current.shared() != desired) {
                DeviceListState next = new DeviceListState(desired, nextRevision(tenantId));
                sharedUsers.put(key, next);
                sharedUserChanges.add(new SharedChange(key, next));
                changed.put(sharedUser, next);
            }
        }
        return Map.copyOf(changed);
    }

    @Override
    public Map<String, DeviceListState> sharedUserChanges(String tenantId, String userId, String deviceId, long afterRevision, long highWater) {
        Map<String, DeviceListState> result = new LinkedHashMap<>();
        synchronized (sharedUserChanges) {
            sharedUserChanges.stream()
                    .filter(change -> change.key.tenantId.equals(tenantId)
                            && change.key.userId.equals(userId)
                            && change.key.deviceId.equals(deviceId))
                    .filter(change -> change.state.changedRevision() > afterRevision && change.state.changedRevision() <= highWater)
                    .sorted(java.util.Comparator.comparingLong(change -> change.state.changedRevision()))
                    .forEach(change -> result.put(change.key.sharedUserId, change.state));
        }
        return Map.copyOf(result);
    }

    @Override
    public List<String> deviceUsersChanged(String tenantId, long afterRevision, long highWater) {
        return devices.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId.equals(tenantId))
                .filter(entry -> entry.getValue().changedRevision > afterRevision && entry.getValue().changedRevision <= highWater)
                .map(entry -> entry.getKey().userId).distinct().sorted().toList();
    }

    @Override
    public void revokeDevice(String tenantId, String userId, String deviceId, long changedRevision) {
        DeviceKey key = new DeviceKey(tenantId, userId, deviceId);
        DeviceState state = devices.get(key);
        if (state == null) throw new IllegalArgumentException("device not found");
        state.revoked = true;
        state.changedRevision = changedRevision;
        oneTimeKeys.remove(key);
        fallbackKeys.remove(key);
        sharedUsers.keySet().removeIf(shared -> shared.tenantId.equals(tenantId) && shared.userId.equals(userId) && shared.deviceId.equals(deviceId));
    }

    @Override
    public boolean bindOidcSession(String tenantId, String userId, String sessionHash, String deviceId) {
        String existing = oidcBindings.putIfAbsent(new OidcKey(tenantId, userId, sessionHash), deviceId);
        return existing == null || existing.equals(deviceId);
    }

    @Override
    public String createBackupVersion(String tenantId, String userId, String algorithm, Map<String, Object> authData) {
        UserKey user = new UserKey(tenantId, userId);
        String version = Long.toString(backupSequences.computeIfAbsent(user, ignored -> new AtomicLong()).incrementAndGet());
        Map<String, BackupState> userBackups = backups.computeIfAbsent(user, ignored -> new ConcurrentHashMap<>());
        userBackups.values().forEach(backup -> backup.current = false);
        userBackups.put(version, new BackupState(version, algorithm, Map.copyOf(authData), true));
        return version;
    }

    @Override
    public Optional<BackupVersionRecord> backupVersion(String tenantId, String userId, String version) {
        BackupState state = backups.getOrDefault(new UserKey(tenantId, userId), Map.of()).get(version);
        return state == null ? Optional.empty() : Optional.of(state.record());
    }

    @Override
    public Optional<BackupVersionRecord> currentBackupVersion(String tenantId, String userId) {
        return backups.getOrDefault(new UserKey(tenantId, userId), Map.of()).values().stream().filter(backup -> backup.current).findFirst().map(BackupState::record);
    }

    @Override
    public void updateBackupVersion(String tenantId, String userId, String version, String algorithm, Map<String, Object> authData) {
        BackupState state = backups.getOrDefault(new UserKey(tenantId, userId), Map.of()).get(version);
        if (state == null) throw new IllegalArgumentException("backup not found");
        state.algorithm = algorithm;
        state.authData = Map.copyOf(authData);
        state.revision++;
    }

    @Override
    public boolean deleteBackupVersion(String tenantId, String userId, String version) {
        return backups.getOrDefault(new UserKey(tenantId, userId), Map.of()).remove(version) != null;
    }

    @Override
    public BackupMutationResult putBackupKeys(String tenantId, String userId, String version, String roomId, String sessionId, Map<String, Object> request) {
        BackupState state = requireBackup(tenantId, userId, version);
        if (sessionId != null) {
            state.sessions.put(new BackupKey(roomId, sessionId), Map.copyOf(request));
        } else if (roomId != null) {
            objectMap(request.get("sessions")).forEach((id, raw) -> state.sessions.put(new BackupKey(roomId, id), Map.copyOf(objectMap(raw))));
        } else {
            objectMap(request.get("rooms")).forEach((room, rawRoom) -> objectMap(objectMap(rawRoom).get("sessions"))
                    .forEach((id, raw) -> state.sessions.put(new BackupKey(room, id), Map.copyOf(objectMap(raw)))));
        }
        state.revision++;
        return new BackupMutationResult(state.sessions.size(), Long.toString(state.revision));
    }

    @Override
    public Map<String, Object> backupKeys(String tenantId, String userId, String version, String roomId, String sessionId) {
        BackupState state = requireBackup(tenantId, userId, version);
        if (sessionId != null) return Optional.ofNullable(state.sessions.get(new BackupKey(roomId, sessionId))).orElseThrow();
        Map<String, Map<String, Object>> rooms = new LinkedHashMap<>();
        state.sessions.forEach((key, payload) -> {
            if (roomId == null || roomId.equals(key.roomId)) {
                rooms.computeIfAbsent(key.roomId, ignored -> new LinkedHashMap<>()).put(key.sessionId, payload);
            }
        });
        if (roomId != null) return Map.of("sessions", Map.copyOf(rooms.getOrDefault(roomId, Map.of())));
        Map<String, Object> result = new LinkedHashMap<>();
        rooms.forEach((room, sessions) -> result.put(room, Map.of("sessions", Map.copyOf(sessions))));
        return Map.of("rooms", Map.copyOf(result));
    }

    @Override
    public void deleteBackupKeys(String tenantId, String userId, String version, String roomId, String sessionId) {
        BackupState state = requireBackup(tenantId, userId, version);
        if (sessionId != null) state.sessions.remove(new BackupKey(roomId, sessionId));
        else if (roomId != null) state.sessions.keySet().removeIf(key -> key.roomId.equals(roomId));
        else state.sessions.clear();
        state.revision++;
    }

    @Override
    public void putAccountData(String tenantId, String userId, String eventType, Map<String, Object> content, long revision) {
        accountData.computeIfAbsent(new UserKey(tenantId, userId), ignored -> new ConcurrentHashMap<>()).put(eventType, Map.copyOf(content));
    }

    @Override
    public Optional<Map<String, Object>> accountData(String tenantId, String userId, String eventType) {
        return Optional.ofNullable(accountData.getOrDefault(new UserKey(tenantId, userId), Map.of()).get(eventType));
    }

    @Override
    public Map<String, Map<String, Object>> accountData(String tenantId, String userId) {
        return Map.copyOf(accountData.getOrDefault(new UserKey(tenantId, userId), Map.of()));
    }

    @Override
    public SupportSafeStats supportSafeStats() {
        long active = devices.values().stream().filter(device -> !device.revoked).count();
        long revoked = devices.values().stream().filter(device -> device.revoked).count();
        long encrypted;
        long plaintext;
        long preKey;
        long existingSession;
        long targets;
        synchronized (toDevice) {
            encrypted = toDevice.stream().filter(event -> "m.room.encrypted".equals(event.eventType)).count();
            plaintext = toDevice.stream().filter(event -> "m.room_key".equals(event.eventType) || "m.forwarded_room_key".equals(event.eventType)).count();
            preKey = toDevice.stream().filter(event -> containsOlmType(event.content, 0)).count();
            existingSession = toDevice.stream().filter(event -> containsOlmType(event.content, 1)).count();
            targets = toDevice.stream().map(event -> event.tenantId + "\u0000" + event.targetUserId + "\u0000" + event.targetDeviceId).distinct().count();
        }
        long revision = revisions.values().stream().mapToLong(AtomicLong::get).max().orElse(0);
        return new SupportSafeStats(active, revoked, toDevice.size(), encrypted, plaintext, preKey, existingSession, targets, toDeviceTransactions.size(), revision);
    }

    private BackupState requireBackup(String tenantId, String userId, String version) {
        BackupState state = backups.getOrDefault(new UserKey(tenantId, userId), Map.of()).get(version);
        if (state == null) throw new IllegalArgumentException("backup not found");
        return state;
    }

    private boolean matches(Map<String, Object> signed, String keyId) {
        Map<String, Object> keys = objectMap(signed.get("keys"));
        return keys.containsKey(keyId) || keys.containsValue(keyId);
    }

    private Map<String, Object> mergeSignatures(Map<String, Object> stored, Map<String, Object> uploaded) {
        Map<String, Object> merged = new LinkedHashMap<>(stored);
        Map<String, Object> signatures = new LinkedHashMap<>(objectMap(stored.get("signatures")));
        objectMap(uploaded).forEach((user, raw) -> {
            Map<String, Object> values = new LinkedHashMap<>(objectMap(signatures.get(user)));
            values.putAll(objectMap(raw));
            signatures.put(user, Map.copyOf(values));
        });
        merged.put("signatures", Map.copyOf(signatures));
        return Map.copyOf(merged);
    }

    private boolean containsOlmType(Map<String, Object> content, int expected) {
        Object raw = content.get("ciphertext");
        if (!(raw instanceof Map<?, ?> ciphertext)) return false;
        return ciphertext.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(value -> value.get("type"))
                .anyMatch(value -> value instanceof Number number && number.intValue() == expected);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private String algorithm(String keyId) {
        int separator = keyId.indexOf(':');
        return separator > 0 ? keyId.substring(0, separator) : keyId;
    }

    private record DeviceKey(String tenantId, String userId, String deviceId) implements Comparable<DeviceKey> {
        @Override public int compareTo(DeviceKey other) {
            int a = tenantId.compareTo(other.tenantId); if (a != 0) return a;
            int b = userId.compareTo(other.userId); return b != 0 ? b : deviceId.compareTo(other.deviceId);
        }
    }
    private record UserKey(String tenantId, String userId) {}
    private record OidcKey(String tenantId, String userId, String sessionHash) {}
    private record SharedKey(String tenantId, String userId, String deviceId, String sharedUserId) {}
    private record SharedChange(SharedKey key, DeviceListState state) {}
    private record BackupKey(String roomId, String sessionId) {}
    private record ToDeviceState(long revision, String tenantId, String targetUserId, String targetDeviceId, String senderUserId, String eventType, String transactionId, String transactionKey, Map<String, Object> content) {}

    private static final class DeviceState {
        private Map<String, Object> deviceKeys = Map.of();
        private long changedRevision;
        private boolean revoked;
        private DeviceRecord record(String userId, String deviceId) { return new DeviceRecord(userId, deviceId, deviceKeys, changedRevision, revoked); }
    }
    private static final class FallbackState {
        private final Object value;
        private boolean used;
        private FallbackState(Object value, boolean used) { this.value = value; this.used = used; }
    }
    private static final class BackupState {
        private final String version;
        private String algorithm;
        private Map<String, Object> authData;
        private boolean current;
        private long revision = 1;
        private final Map<BackupKey, Map<String, Object>> sessions = new ConcurrentHashMap<>();
        private BackupState(String version, String algorithm, Map<String, Object> authData, boolean current) {
            this.version = version; this.algorithm = algorithm; this.authData = authData; this.current = current;
        }
        private BackupVersionRecord record() { return new BackupVersionRecord(version, algorithm, authData, current, revision, sessions.size()); }
    }
}
