package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class MatrixE2eeStateService {
    private static final int MAX_TO_DEVICE_EVENTS_PER_SYNC = 100;
    private static final String E2EE_CURSOR_MARKER = "|e2ee:";
    private static final String E2EE_CURSOR_V2_PREFIX = "v2:";

    private final MatrixE2eePersistence persistence;
    private final AtomicLong projectedToDeviceEventCount = new AtomicLong();
    private final AtomicLong syncResponsesWithToDeviceEvents = new AtomicLong();

    public MatrixE2eeStateService(MatrixE2eePersistence persistence) {
        this.persistence = persistence;
    }

    public Map<String, Object> uploadKeys(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> deviceKeys = immutableObject(objectMap(request.get("device_keys")));
        if (!deviceKeys.isEmpty()) {
            requireEquals(deviceKeys.get("user_id"), identity.userId(), "device key user");
            requireEquals(deviceKeys.get("device_id"), identity.deviceId(), "device key device");
            persistence.upsertDevice(identity.tenantId(), identity.userId(), identity.deviceId(), deviceKeys, persistence.nextRevision(identity.tenantId()));
        }
        Map<String, Object> oneTimeKeys = immutableObject(objectMap(request.get("one_time_keys")));
        if (!oneTimeKeys.isEmpty()) persistence.addOneTimeKeys(identity.tenantId(), identity.userId(), identity.deviceId(), oneTimeKeys);
        if (request.containsKey("fallback_keys")) {
            persistence.replaceFallbackKeys(identity.tenantId(), identity.userId(), identity.deviceId(), immutableObject(objectMap(request.get("fallback_keys"))), persistence.nextRevision(identity.tenantId()));
        }
        return Map.of("one_time_key_counts", persistence.oneTimeKeyCounts(identity.tenantId(), identity.userId(), identity.deviceId()));
    }

    public Map<String, Object> queryKeys(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> requested = immutableObject(objectMap(request.get("device_keys")));
        Map<String, Object> responseDevices = new LinkedHashMap<>();
        Map<String, Object> master = new LinkedHashMap<>();
        Map<String, Object> selfSigning = new LinkedHashMap<>();
        for (Map.Entry<String, Object> userEntry : requested.entrySet()) {
            String userId = userEntry.getKey();
            Set<String> requestedDevices = stringSet(userEntry.getValue());
            Map<String, Object> userDevices = new LinkedHashMap<>();
            for (MatrixE2eePersistence.DeviceRecord device : persistence.devices(identity.tenantId(), userId, requestedDevices)) {
                if (!device.revoked() && !device.deviceKeys().isEmpty()) userDevices.put(device.deviceId(), device.deviceKeys());
            }
            if (!userDevices.isEmpty()) responseDevices.put(userId, Map.copyOf(userDevices));
            persistence.crossSigning(identity.tenantId(), userId).ifPresent(cross -> {
                putIfPresent(master, userId, cross.masterKey());
                putIfPresent(selfSigning, userId, cross.selfSigningKey());
            });
        }
        return Map.of("device_keys", Map.copyOf(responseDevices), "master_keys", Map.copyOf(master), "self_signing_keys", Map.copyOf(selfSigning), "failures", Map.of());
    }

    public Map<String, Object> claimKeys(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> requested = immutableObject(objectMap(request.get("one_time_keys")));
        Map<String, Object> claims = new LinkedHashMap<>();
        for (Map.Entry<String, Object> userEntry : requested.entrySet()) {
            Map<String, Object> devices = immutableObject(objectMap(userEntry.getValue()));
            Map<String, Object> userClaims = new LinkedHashMap<>();
            for (Map.Entry<String, Object> deviceEntry : devices.entrySet()) {
                String algorithm = requiredText(deviceEntry.getValue(), "one-time key algorithm", 128);
                persistence.claimOneTimeKey(identity.tenantId(), userEntry.getKey(), deviceEntry.getKey(), algorithm)
                        .ifPresent(claimed -> userClaims.put(deviceEntry.getKey(), Map.of(claimed.keyId(), claimed.value())));
            }
            if (!userClaims.isEmpty()) claims.put(userEntry.getKey(), Map.copyOf(userClaims));
        }
        return Map.of("one_time_keys", Map.copyOf(claims), "failures", Map.of());
    }

    public void uploadCrossSigning(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        persistence.upsertCrossSigning(identity.tenantId(), identity.userId(), validatedSigningKey(request.get("master_key"), identity.userId(), "master"), validatedSigningKey(request.get("self_signing_key"), identity.userId(), "self_signing"), validatedSigningKey(request.get("user_signing_key"), identity.userId(), "user_signing"), persistence.nextRevision(identity.tenantId()));
    }

    public Map<String, Object> uploadSignatures(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        int count = 0;
        for (Map.Entry<String, Object> userEntry : request.entrySet()) {
            Map<String, Object> targets = immutableObject(objectMap(userEntry.getValue()));
            for (Map.Entry<String, Object> targetEntry : targets.entrySet()) {
                Map<String, Object> target = immutableObject(objectMap(targetEntry.getValue()));
                Map<String, Object> signatures = immutableObject(objectMap(target.get("signatures")));
                if (signatures.isEmpty()) continue;
                if (persistence.device(identity.tenantId(), userEntry.getKey(), targetEntry.getKey()).isPresent()) persistence.mergeDeviceSignatures(identity.tenantId(), userEntry.getKey(), targetEntry.getKey(), signatures, persistence.nextRevision(identity.tenantId()));
                else persistence.mergeCrossSigningSignatures(identity.tenantId(), userEntry.getKey(), targetEntry.getKey(), signatures, persistence.nextRevision(identity.tenantId()));
                count++;
            }
        }
        return Map.of("failures", Map.of(), "signed", count);
    }

    public void sendToDevice(MatrixFacadeClientStateService.MatrixIdentity identity, String eventType, String transactionId, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> messages = immutableObject(objectMap(request.get("messages")));
        for (Map.Entry<String, Object> userEntry : messages.entrySet()) {
            Map<String, Object> devices = immutableObject(objectMap(userEntry.getValue()));
            for (Map.Entry<String, Object> deviceEntry : devices.entrySet()) {
                persistence.appendToDevice(identity.tenantId(), userEntry.getKey(), deviceEntry.getKey(), identity.userId(), requiredText(eventType, "to-device event type", 255), requiredText(transactionId, "to-device transaction id", 255), immutableObject(objectMap(deviceEntry.getValue())));
            }
        }
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(MatrixFacadeClientStateService.MatrixIdentity identity, long afterSequence) {
        return sync(identity, afterSequence, afterSequence, null);
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(MatrixFacadeClientStateService.MatrixIdentity identity, long afterSequence, Collection<String> currentlySharedUserIds) {
        return sync(identity, afterSequence, afterSequence, currentlySharedUserIds);
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(MatrixFacadeClientStateService.MatrixIdentity identity, long toDeviceAfterSequence, long deviceListAfterSequence, Collection<String> currentlySharedUserIds) {
        requireActive(identity);
        if (toDeviceAfterSequence < 0 || deviceListAfterSequence < 0) throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid.");
        Set<String> shared = currentlySharedUserIds == null ? null : Set.copyOf(currentlySharedUserIds.stream().filter(value -> value != null && !value.isBlank() && !value.equals(identity.userId())).toList());

        Map<String, MatrixE2eePersistence.DeviceListState> transitions = shared == null
                ? Map.of()
                : persistence.reconcileSharedUsers(identity.tenantId(), identity.userId(), identity.deviceId(), shared);

        long snapshotHighWater = persistence.currentRevision(identity.tenantId());
        List<MatrixE2eePersistence.ToDeviceRecord> queue = persistence.toDeviceEvents(identity.tenantId(), identity.userId(), identity.deviceId(), toDeviceAfterSequence, snapshotHighWater, MAX_TO_DEVICE_EVENTS_PER_SYNC);
        List<Map<String, Object>> events = queue.stream().map(event -> Map.<String, Object>of("sender", event.senderUserId(), "type", event.eventType(), "content", event.content())).toList();
        if (!events.isEmpty()) { projectedToDeviceEventCount.addAndGet(events.size()); syncResponsesWithToDeviceEvents.incrementAndGet(); }

        long toDeviceDeliveredHighWater = queue.size() == MAX_TO_DEVICE_EVENTS_PER_SYNC ? queue.getLast().revision() : snapshotHighWater;
        Map<String, MatrixE2eePersistence.DeviceListState> sharedChanges = new LinkedHashMap<>();
        if (shared != null) {
            sharedChanges.putAll(persistence.sharedUserChanges(identity.tenantId(), identity.userId(), identity.deviceId(), deviceListAfterSequence, snapshotHighWater));
            transitions.forEach(sharedChanges::put);
        }

        Set<String> changed = new java.util.TreeSet<>();
        List<String> left = new ArrayList<>();
        sharedChanges.forEach((user, state) -> { if (state.shared()) changed.add(user); else left.add(user); });
        if (shared != null) {
            Set<String> relevantUsers = new LinkedHashSet<>(shared);
            relevantUsers.addAll(sharedChanges.keySet());
            persistence.deviceUsersChanged(identity.tenantId(), deviceListAfterSequence, snapshotHighWater).stream()
                    .filter(relevantUsers::contains)
                    .forEach(changed::add);
        }

        long deviceListDeliveredHighWater = deviceListAfterSequence;
        for (MatrixE2eePersistence.DeviceListState state : sharedChanges.values()) deviceListDeliveredHighWater = Math.max(deviceListDeliveredHighWater, state.changedRevision());

        long nextSequence = Math.min(snapshotHighWater, toDeviceDeliveredHighWater);
        persistence.recordDeviceSyncProgress(identity.tenantId(), identity.userId(), identity.deviceId(), nextSequence);
        return new MatrixProtocolCoreService.MatrixSyncCrypto(events, List.copyOf(changed), left.stream().distinct().sorted().toList(), persistence.oneTimeKeyCounts(identity.tenantId(), identity.userId(), identity.deviceId()), persistence.unusedFallbackAlgorithms(identity.tenantId(), identity.userId(), identity.deviceId()), nextSequence, deviceListDeliveredHighWater);
    }

    public SupportSafeToDeviceEvidence supportSafeToDeviceEvidence() {
        var stats = persistence.supportSafeStats();
        return new SupportSafeToDeviceEvidence("matrix-to-device-proof-v1", stats.activeDeviceCount(), stats.revokedDeviceCount(), stats.queuedEventCount(), stats.encryptedEventCount(), stats.plaintextRoomKeyEventCount(), stats.olmPreKeyEnvelopeCount(), stats.olmExistingSessionEnvelopeCount(), stats.targetedDeviceCount(), stats.transactionCount(), projectedToDeviceEventCount.get(), syncResponsesWithToDeviceEvents.get(), stats.currentRevision(), true);
    }

    public Map<String, Object> keyChanges(MatrixFacadeClientStateService.MatrixIdentity identity, long afterSequence) { requireActive(identity); long highWater = persistence.currentRevision(identity.tenantId()); return Map.of("changed", persistence.deviceUsersChanged(identity.tenantId(), afterSequence, highWater), "left", List.of()); }
    public long currentSequence() { throw new IllegalStateException("Matrix E2EE sequence is tenant-scoped; use currentSequence(identity)"); }
    public long currentSequence(MatrixFacadeClientStateService.MatrixIdentity identity) { return persistence.currentRevision(identity.tenantId()); }
    public String combinedCursor(String chatCursor, long cryptoSequence) { return combinedCursor(chatCursor, new E2eeSyncCursor(cryptoSequence, cryptoSequence)); }
    public String combinedCursor(String chatCursor, MatrixProtocolCoreService.MatrixSyncCrypto crypto) { return combinedCursor(chatCursor, new E2eeSyncCursor(crypto.nextSequence(), crypto.deviceListSequence())); }
    public String combinedCursor(String chatCursor, E2eeSyncCursor cursor) { if (cursor == null || cursor.toDeviceSequence() < 0 || cursor.deviceListSequence() < 0) throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid."); return chatCursor + E2EE_CURSOR_MARKER + E2EE_CURSOR_V2_PREFIX + cursor.toDeviceSequence() + ":" + cursor.deviceListSequence(); }
    public E2eeSyncCursor cryptoCursor(String decodedCursor) { if (decodedCursor == null || decodedCursor.isBlank()) return new E2eeSyncCursor(0, 0); int marker = decodedCursor.lastIndexOf(E2EE_CURSOR_MARKER); if (marker < 0) return new E2eeSyncCursor(0, 0); String encoded = decodedCursor.substring(marker + E2EE_CURSOR_MARKER.length()); try { if (encoded.startsWith(E2EE_CURSOR_V2_PREFIX)) { String[] parts = encoded.substring(E2EE_CURSOR_V2_PREFIX.length()).split(":", -1); if (parts.length != 2) throw new NumberFormatException("invalid v2 cursor"); return new E2eeSyncCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1])); } long legacy = Long.parseLong(encoded); return new E2eeSyncCursor(legacy, legacy); } catch (NumberFormatException exception) { throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid."); } }
    public long cryptoSequence(String decodedCursor) { return cryptoCursor(decodedCursor).toDeviceSequence(); }
    public long cryptoDeviceListSequence(String decodedCursor) { return cryptoCursor(decodedCursor).deviceListSequence(); }
    public void revokeDevice(MatrixFacadeClientStateService.MatrixIdentity identity, String deviceId) { requireActive(identity); if (persistence.device(identity.tenantId(), identity.userId(), deviceId).isEmpty()) throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix device was not found."); persistence.revokeDevice(identity.tenantId(), identity.userId(), deviceId, persistence.nextRevision(identity.tenantId())); }
    public void requireActive(MatrixFacadeClientStateService.MatrixIdentity identity) { if (identity.oidcSessionHash() != null && !identity.oidcSessionHash().isBlank() && !persistence.bindOidcSession(identity.tenantId(), identity.userId(), identity.oidcSessionHash(), identity.deviceId())) throw new MatrixProtocolException("M_UNKNOWN_TOKEN", "The OIDC session is bound to a different Matrix device."); persistence.device(identity.tenantId(), identity.userId(), identity.deviceId()).ifPresent(device -> { if (device.revoked()) throw new MatrixProtocolException("M_UNKNOWN_TOKEN", "The Matrix device was revoked."); }); }

    public Map<String, Object> createBackupVersion(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) { requireActive(identity); return Map.of("version", persistence.createBackupVersion(identity.tenantId(), identity.userId(), requiredText(request.get("algorithm"), "backup algorithm", 128), immutableObject(objectMap(request.get("auth_data"))))); }
    public Map<String, Object> backupVersion(MatrixFacadeClientStateService.MatrixIdentity identity, String requestedVersion) { requireActive(identity); var backup = (requestedVersion == null || requestedVersion.isBlank() ? persistence.currentBackupVersion(identity.tenantId(), identity.userId()) : persistence.backupVersion(identity.tenantId(), identity.userId(), requestedVersion)).orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup version was not found.")); return Map.of("algorithm", backup.algorithm(), "auth_data", backup.authData(), "count", backup.count(), "etag", Long.toString(backup.revision()), "version", backup.version()); }
    public void updateBackupVersion(MatrixFacadeClientStateService.MatrixIdentity identity, String version, Map<String, Object> request) { requireActive(identity); if (persistence.backupVersion(identity.tenantId(), identity.userId(), version).isEmpty()) throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup version was not found."); persistence.updateBackupVersion(identity.tenantId(), identity.userId(), version, requiredText(request.get("algorithm"), "backup algorithm", 128), immutableObject(objectMap(request.get("auth_data")))); }
    public void deleteBackupVersion(MatrixFacadeClientStateService.MatrixIdentity identity, String version) { requireActive(identity); if (!persistence.deleteBackupVersion(identity.tenantId(), identity.userId(), version)) throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup version was not found."); }
    public Map<String, Object> putBackupKeys(MatrixFacadeClientStateService.MatrixIdentity identity, String version, String roomId, String sessionId, Map<String, Object> request) { requireActive(identity); requireBackup(identity, version); if (sessionId != null) { requiredPath(roomId); requiredPath(sessionId); } var result = persistence.putBackupKeys(identity.tenantId(), identity.userId(), version, roomId, sessionId, immutableObject(request)); return Map.of("count", result.count(), "etag", result.etag()); }
    public Map<String, Object> backupKeys(MatrixFacadeClientStateService.MatrixIdentity identity, String version, String roomId, String sessionId) { requireActive(identity); requireBackup(identity, version); return persistence.backupKeys(identity.tenantId(), identity.userId(), version, roomId, sessionId); }
    public void deleteBackupKeys(MatrixFacadeClientStateService.MatrixIdentity identity, String version, String roomId, String sessionId) { requireActive(identity); requireBackup(identity, version); persistence.deleteBackupKeys(identity.tenantId(), identity.userId(), version, roomId, sessionId); }
    public void putAccountData(MatrixFacadeClientStateService.MatrixIdentity identity, String eventType, Map<String, Object> content) { requireActive(identity); persistence.putAccountData(identity.tenantId(), identity.userId(), requiredText(eventType, "account data event type", 255), immutableObject(content), persistence.nextRevision(identity.tenantId())); }
    public Map<String, Object> accountData(MatrixFacadeClientStateService.MatrixIdentity identity, String eventType) { requireActive(identity); return persistence.accountData(identity.tenantId(), identity.userId(), eventType).orElseThrow(() -> new MatrixProtocolException("M_NOT_FOUND", "The Matrix account-data event was not found.")); }
    public Map<String, Map<String, Object>> accountData(MatrixFacadeClientStateService.MatrixIdentity identity) { requireActive(identity); return persistence.accountData(identity.tenantId(), identity.userId()); }

    private void requireBackup(MatrixFacadeClientStateService.MatrixIdentity identity, String version) { if (persistence.backupVersion(identity.tenantId(), identity.userId(), version).isEmpty()) throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix room-key backup version was not found."); }
    private Map<String, Object> validatedSigningKey(Object raw, String expectedUserId, String expectedUsage) { Map<String, Object> key = immutableObject(objectMap(raw)); if (key.isEmpty()) return Map.of(); requireEquals(key.get("user_id"), expectedUserId, "cross-signing user"); if (!stringSet(key.get("usage")).contains(expectedUsage)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix cross-signing usage is invalid."); return key; }
    private void requireEquals(Object actual, String expected, String label) { if (!(actual instanceof String text) || !text.equals(expected)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix " + label + " is invalid."); }
    private String requireKeyId(String value) { return requiredText(value, "one-time key id", 255); }
    private String requiredPath(String value) { return requiredText(value, "Matrix path identifier", 512); }
    private String requiredText(Object value, String label, int maximumLength) { if (!(value instanceof String text) || text.isBlank() || text.length() > maximumLength) throw new MatrixProtocolException("M_BAD_JSON", "Matrix " + label + " is invalid."); return text; }
    private Set<String> stringSet(Object value) { if (value == null) return Set.of(); if (value instanceof Collection<?> collection) return collection.stream().filter(String.class::isInstance).map(String.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet()); if (value instanceof Map<?, ?> map) return map.keySet().stream().filter(String.class::isInstance).map(String.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet()); throw new MatrixProtocolException("M_BAD_JSON", "Matrix string collection is invalid."); }
    private Map<String, Object> objectMap(Object value) { if (value == null) return Map.of(); if (!(value instanceof Map<?, ?> map)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix request object is invalid."); Map<String, Object> result = new LinkedHashMap<>(); map.forEach((key, nested) -> { if (!(key instanceof String text)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix request key is invalid."); result.put(text, nested); }); return result; }
    private Map<String, Object> immutableObject(Map<String, Object> value) { return value == null || value.isEmpty() ? Map.of() : Map.copyOf(value); }
    private Object immutableValue(Object value) { if (value instanceof Map<?, ?> map) { Map<String, Object> nested = new LinkedHashMap<>(); map.forEach((key, item) -> { if (!(key instanceof String text)) throw new MatrixProtocolException("M_BAD_JSON", "Matrix request key is invalid."); nested.put(text, immutableValue(item)); }); return Map.copyOf(nested); } if (value instanceof Collection<?> collection) return List.copyOf(collection.stream().map(this::immutableValue).toList()); return value; }
    private void putIfPresent(Map<String, Object> target, String userId, Map<String, Object> value) { if (value != null && !value.isEmpty()) target.put(userId, value); }

    public record E2eeSyncCursor(long toDeviceSequence, long deviceListSequence) {}
    public record SupportSafeToDeviceEvidence(String contractVersion, long activeDeviceCount, long revokedDeviceCount, long queuedEventCount, long encryptedEventCount, long plaintextRoomKeyEventCount, long olmPreKeyEnvelopeCount, long olmExistingSessionEnvelopeCount, long targetedDeviceCount, long transactionCount, long projectedToDeviceEventCount, long syncResponsesWithToDeviceEvents, long currentRevision, boolean supportSafe) {}
}
