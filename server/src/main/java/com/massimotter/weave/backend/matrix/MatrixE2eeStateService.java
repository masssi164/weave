package com.massimotter.weave.backend.matrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Matrix server-side routing/E2EE orchestration.
 *
 * <p>All durable protocol state lives behind {@link MatrixE2eePersistence}.
 * The service owns validation and Matrix response projection only; it does not
 * retain tenant/device/key state in process memory.</p>
 */
@Service
public class MatrixE2eeStateService {

    private static final int MAX_ONE_TIME_KEYS_PER_UPLOAD = 256;
    private static final int MAX_TO_DEVICE_TARGETS = 1_000;
    private static final int MAX_TO_DEVICE_EVENTS_PER_SYNC = 100;

    private final MatrixE2eePersistence persistence;
    private final AtomicLong projectedToDeviceEventCount = new AtomicLong();
    private final AtomicLong syncResponsesWithToDeviceEvents = new AtomicLong();

    public MatrixE2eeStateService(MatrixE2eePersistence persistence) {
        this.persistence = java.util.Objects.requireNonNull(persistence, "persistence");
    }

    public Map<String, Object> uploadKeys(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> deviceKeys = immutableObject(objectMap(request.get("device_keys")));
        if (!deviceKeys.isEmpty()) {
            requireEquals(deviceKeys.get("user_id"), identity.userId(), "device key user");
            requireEquals(deviceKeys.get("device_id"), identity.deviceId(), "device key device");
        }
        Map<String, Object> oneTimeKeys = objectMap(request.get("one_time_keys"));
        if (oneTimeKeys.size() > MAX_ONE_TIME_KEYS_PER_UPLOAD) {
            throw new MatrixProtocolException("M_LIMIT_EXCEEDED", "The Matrix one-time key upload limit was reached.");
        }
        Map<String, Object> validatedOneTimeKeys = new LinkedHashMap<>();
        oneTimeKeys.forEach((keyId, value) -> validatedOneTimeKeys.put(requireKeyId(keyId), immutableValue(value)));
        Map<String, Object> fallbackKeys = immutableObject(objectMap(request.get("fallback_keys")));
        if (!deviceKeys.isEmpty() || !validatedOneTimeKeys.isEmpty() || !fallbackKeys.isEmpty()) {
            long revision = persistence.nextRevision(identity.tenantId());
            persistence.upsertDevice(identity.tenantId(), identity.userId(), identity.deviceId(), deviceKeys, revision);
            if (!validatedOneTimeKeys.isEmpty()) persistence.addOneTimeKeys(identity.tenantId(), identity.userId(), identity.deviceId(), validatedOneTimeKeys);
            if (!fallbackKeys.isEmpty()) persistence.replaceFallbackKeys(identity.tenantId(), identity.userId(), identity.deviceId(), fallbackKeys, revision);
        }
        return Map.of("one_time_key_counts", persistence.oneTimeKeyCounts(identity.tenantId(), identity.userId(), identity.deviceId()));
    }

    public Map<String, Object> queryKeys(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> requestedUsers = objectMap(request.get("device_keys"));
        Map<String, Object> deviceKeys = new LinkedHashMap<>();
        Map<String, Object> masterKeys = new LinkedHashMap<>();
        Map<String, Object> selfSigningKeys = new LinkedHashMap<>();
        Map<String, Object> userSigningKeys = new LinkedHashMap<>();
        for (Map.Entry<String, Object> requestedUser : requestedUsers.entrySet()) {
            Set<String> requestedDevices = stringSet(requestedUser.getValue());
            Map<String, Object> projectedDevices = new LinkedHashMap<>();
            persistence.devices(identity.tenantId(), requestedUser.getKey(), requestedDevices).stream()
                    .filter(device -> !device.revoked())
                    .filter(device -> !device.deviceKeys().isEmpty())
                    .forEach(device -> projectedDevices.put(device.deviceId(), device.deviceKeys()));
            deviceKeys.put(requestedUser.getKey(), Map.copyOf(projectedDevices));
            persistence.crossSigning(identity.tenantId(), requestedUser.getKey()).ifPresent(signing -> {
                putIfPresent(masterKeys, requestedUser.getKey(), signing.masterKey());
                putIfPresent(selfSigningKeys, requestedUser.getKey(), signing.selfSigningKey());
                putIfPresent(userSigningKeys, requestedUser.getKey(), signing.userSigningKey());
            });
        }
        return Map.of("device_keys", Map.copyOf(deviceKeys), "master_keys", Map.copyOf(masterKeys), "self_signing_keys", Map.copyOf(selfSigningKeys), "user_signing_keys", Map.copyOf(userSigningKeys), "failures", Map.of());
    }

    public Map<String, Object> claimKeys(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> requestedUsers = objectMap(request.get("one_time_keys"));
        Map<String, Object> claimedUsers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> userEntry : requestedUsers.entrySet()) {
            Map<String, Object> claimedDevices = new LinkedHashMap<>();
            for (Map.Entry<String, Object> deviceEntry : objectMap(userEntry.getValue()).entrySet()) {
                if (!(deviceEntry.getValue() instanceof String algorithm)) continue;
                persistence.claimOneTimeKey(identity.tenantId(), userEntry.getKey(), deviceEntry.getKey(), algorithm)
                        .ifPresent(claimed -> claimedDevices.put(deviceEntry.getKey(), Map.of(claimed.keyId(), claimed.value())));
            }
            if (!claimedDevices.isEmpty()) claimedUsers.put(userEntry.getKey(), Map.copyOf(claimedDevices));
        }
        return Map.of("one_time_keys", Map.copyOf(claimedUsers), "failures", Map.of());
    }

    public void uploadCrossSigning(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        Map<String, Object> master = validatedSigningKey(request.get("master_key"), identity.userId(), "master");
        Map<String, Object> self = validatedSigningKey(request.get("self_signing_key"), identity.userId(), "self_signing");
        Map<String, Object> user = validatedSigningKey(request.get("user_signing_key"), identity.userId(), "user_signing");
        long revision = persistence.nextRevision(identity.tenantId());
        persistence.upsertCrossSigning(identity.tenantId(), identity.userId(), master, self, user, revision);
    }

    public Map<String, Object> uploadSignatures(MatrixFacadeClientStateService.MatrixIdentity identity, Map<String, Object> request) {
        requireActive(identity);
        request.forEach((userId, rawSignedObjects) -> objectMap(rawSignedObjects).forEach((keyId, rawSignedObject) -> {
            Map<String, Object> signatures = immutableObject(objectMap(objectMap(rawSignedObject).get("signatures")));
            if (signatures.isEmpty()) return;
            var device = persistence.device(identity.tenantId(), userId, keyId);
            long revision = persistence.nextRevision(identity.tenantId());
            if (device.isPresent() && !device.get().deviceKeys().isEmpty()) persistence.mergeDeviceSignatures(identity.tenantId(), userId, keyId, signatures, revision);
            else persistence.mergeCrossSigningSignatures(identity.tenantId(), userId, keyId, signatures, revision);
        }));
        return Map.of("failures", Map.of());
    }

    public void sendToDevice(MatrixFacadeClientStateService.MatrixIdentity identity, String eventType, String transactionId, Map<String, Object> request) {
        requireActive(identity);
        int targets = 0;
        for (Map.Entry<String, Object> userEntry : objectMap(request.get("messages")).entrySet()) {
            for (Map.Entry<String, Object> deviceEntry : objectMap(userEntry.getValue()).entrySet()) {
                Collection<String> targetDevices = "*".equals(deviceEntry.getKey()) ? persistence.activeDeviceIds(identity.tenantId(), userEntry.getKey()) : List.of(deviceEntry.getKey());
                for (String targetDevice : targetDevices) {
                    if (++targets > MAX_TO_DEVICE_TARGETS) throw new MatrixProtocolException("M_LIMIT_EXCEEDED", "The Matrix to-device target limit was reached.");
                    persistence.appendToDevice(identity.tenantId(), userEntry.getKey(), targetDevice, identity.userId(), requiredText(eventType, "to-device event type", 255), requiredText(transactionId, "transaction id", 255), immutableObject(objectMap(deviceEntry.getValue())));
                }
            }
        }
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(MatrixFacadeClientStateService.MatrixIdentity identity, long afterSequence) {
        return sync(identity, afterSequence, null);
    }

    public MatrixProtocolCoreService.MatrixSyncCrypto sync(MatrixFacadeClientStateService.MatrixIdentity identity, long afterSequence, Collection<String> currentlySharedUserIds) {
        requireActive(identity);
        Set<String> shared = currentlySharedUserIds == null ? null : Set.copyOf(currentlySharedUserIds.stream()
                .filter(value -> value != null && !value.isBlank() && !value.equals(identity.userId()))
                .toList());

        long persistedDeviceListProgress = persistence.deviceListProgress(identity.tenantId(), identity.userId(), identity.deviceId());
        long deviceListAfter = Math.max(persistedDeviceListProgress, afterSequence);
        if (deviceListAfter > persistedDeviceListProgress) {
            persistence.recordDeviceListProgress(identity.tenantId(), identity.userId(), identity.deviceId(), deviceListAfter);
        }
        if (shared != null) {
            persistence.reconcileSharedUsers(identity.tenantId(), identity.userId(), identity.deviceId(), shared);
        }

        long snapshotHighWater = persistence.currentRevision(identity.tenantId());
        List<MatrixE2eePersistence.ToDeviceRecord> queue = persistence.toDeviceEvents(
                identity.tenantId(), identity.userId(), identity.deviceId(), afterSequence, snapshotHighWater,
                MAX_TO_DEVICE_EVENTS_PER_SYNC);
        List<Map<String, Object>> events = queue.stream()
                .map(event -> Map.<String, Object>of("sender", event.senderUserId(), "type", event.eventType(), "content", event.content()))
                .toList();
        if (!events.isEmpty()) {
            projectedToDeviceEventCount.addAndGet(events.size());
            syncResponsesWithToDeviceEvents.incrementAndGet();
        }

        long toDeviceDeliveredHighWater = queue.size() == MAX_TO_DEVICE_EVENTS_PER_SYNC ? queue.getLast().revision() : snapshotHighWater;
        Set<String> changed = new java.util.TreeSet<>(persistence.deviceUsersChanged(identity.tenantId(), deviceListAfter, snapshotHighWater));
        Map<String, MatrixE2eePersistence.DeviceListState> sharedChanges = shared == null
                ? Map.of()
                : persistence.sharedUserChanges(identity.tenantId(), identity.userId(), identity.deviceId(), deviceListAfter, snapshotHighWater);
        List<String> left = new ArrayList<>();
        sharedChanges.forEach((user, state) -> { if (state.shared()) changed.add(user); else left.add(user); });

        long nextSequence = Math.min(snapshotHighWater, toDeviceDeliveredHighWater);
        persistence.recordDeviceSyncProgress(identity.tenantId(), identity.userId(), identity.deviceId(), nextSequence);
        return new MatrixProtocolCoreService.MatrixSyncCrypto(
                events,
                List.copyOf(changed),
                left.stream().distinct().sorted().toList(),
                persistence.oneTimeKeyCounts(identity.tenantId(), identity.userId(), identity.deviceId()),
                persistence.unusedFallbackAlgorithms(identity.tenantId(), identity.userId(), identity.deviceId()),
                nextSequence);
    }

    public SupportSafeToDeviceEvidence supportSafeToDeviceEvidence() {
        var stats = persistence.supportSafeStats();
        return new SupportSafeToDeviceEvidence("matrix-to-device-proof-v1", stats.activeDeviceCount(), stats.revokedDeviceCount(), stats.queuedEventCount(), stats.encryptedEventCount(), stats.plaintextRoomKeyEventCount(), stats.olmPreKeyEnvelopeCount(), stats.olmExistingSessionEnvelopeCount(), stats.targetedDeviceCount(), stats.transactionCount(), projectedToDeviceEventCount.get(), syncResponsesWithToDeviceEvents.get(), stats.currentRevision(), true);
    }

    public Map<String, Object> keyChanges(MatrixFacadeClientStateService.MatrixIdentity identity, long afterSequence) {
        requireActive(identity);
        long highWater = persistence.currentRevision(identity.tenantId());
        return Map.of("changed", persistence.deviceUsersChanged(identity.tenantId(), afterSequence, highWater), "left", List.of());
    }

    public long currentSequence() { throw new IllegalStateException("Matrix E2EE sequence is tenant-scoped; use currentSequence(identity)"); }
    public long currentSequence(MatrixFacadeClientStateService.MatrixIdentity identity) { return persistence.currentRevision(identity.tenantId()); }
    public String combinedCursor(String chatCursor, long cryptoSequence) { if (cryptoSequence < 0) throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid."); return chatCursor + "|e2ee:" + cryptoSequence; }
    public long cryptoSequence(String decodedCursor) { if (decodedCursor == null || decodedCursor.isBlank()) return 0; int marker = decodedCursor.lastIndexOf("|e2ee:"); if (marker < 0) return 0; try { return Long.parseLong(decodedCursor.substring(marker + "|e2ee:".length())); } catch (NumberFormatException exception) { throw new MatrixProtocolException("M_BAD_JSON", "The Matrix E2EE sync cursor is invalid."); } }

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

    public record SupportSafeToDeviceEvidence(String contractVersion, long activeDeviceCount, long revokedDeviceCount, long queuedEventCount, long encryptedEventCount, long plaintextRoomKeyEventCount, long olmPreKeyEnvelopeCount, long olmExistingSessionEnvelopeCount, long targetedDeviceCount, long transactionCount, long projectedToDeviceEventCount, long syncResponsesWithToDeviceEvents, long currentRevision, boolean supportSafe) {}
}
