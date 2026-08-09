package com.massimotter.weave.backend.matrix;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Operation-level persistence boundary for server-side Matrix routing/E2EE metadata.
 *
 * <p>The interface deliberately exposes no tenant-wide snapshot. Every method is
 * scoped to the smallest aggregate required by the Matrix operation so multiple
 * server instances can coordinate through PostgreSQL rather than process-local maps.</p>
 */
public interface MatrixE2eePersistence {

    long currentRevision(String tenantId);

    long nextRevision(String tenantId);

    Optional<DeviceRecord> device(String tenantId, String userId, String deviceId);

    List<DeviceRecord> devices(String tenantId, String userId, Set<String> requestedDeviceIds);

    Collection<String> activeDeviceIds(String tenantId, String userId);

    void upsertDevice(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> deviceKeys,
            long changedRevision);

    void addOneTimeKeys(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> keys);

    void replaceFallbackKeys(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> keys,
            long changedRevision);

    Map<String, Long> oneTimeKeyCounts(String tenantId, String userId, String deviceId);

    List<String> unusedFallbackAlgorithms(String tenantId, String userId, String deviceId);

    Optional<ClaimedKey> claimOneTimeKey(String tenantId, String userId, String deviceId, String algorithm);

    Optional<CrossSigningRecord> crossSigning(String tenantId, String userId);

    void upsertCrossSigning(
            String tenantId,
            String userId,
            Map<String, Object> masterKey,
            Map<String, Object> selfSigningKey,
            Map<String, Object> userSigningKey,
            long changedRevision);

    void mergeDeviceSignatures(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> signatures,
            long changedRevision);

    void mergeCrossSigningSignatures(
            String tenantId,
            String userId,
            String keyId,
            Map<String, Object> signatures,
            long changedRevision);

    /**
     * Atomically allocates a logical Matrix revision and appends the to-device event.
     * Duplicate transactions return the already visible revision without advancing the head.
     */
    long appendToDevice(
            String tenantId,
            String targetUserId,
            String targetDeviceId,
            String senderUserId,
            String eventType,
            String transactionId,
            Map<String, Object> content);

    List<ToDeviceRecord> toDeviceEvents(
            String tenantId,
            String userId,
            String deviceId,
            long afterRevision,
            long highWater,
            int limit);

    void recordDeviceSyncProgress(String tenantId, String userId, String deviceId, long revision);

    Map<String, DeviceListState> reconcileSharedUsers(
            String tenantId,
            String userId,
            String deviceId,
            Set<String> currentlySharedUserIds);

    Map<String, DeviceListState> sharedUserChanges(
            String tenantId,
            String userId,
            String deviceId,
            long afterRevision,
            long highWater);

    List<String> deviceUsersChanged(String tenantId, long afterRevision, long highWater);

    void revokeDevice(String tenantId, String userId, String deviceId, long changedRevision);

    boolean bindOidcSession(String tenantId, String userId, String sessionHash, String deviceId);

    String createBackupVersion(
            String tenantId,
            String userId,
            String algorithm,
            Map<String, Object> authData);

    Optional<BackupVersionRecord> backupVersion(String tenantId, String userId, String version);

    Optional<BackupVersionRecord> currentBackupVersion(String tenantId, String userId);

    void updateBackupVersion(
            String tenantId,
            String userId,
            String version,
            String algorithm,
            Map<String, Object> authData);

    boolean deleteBackupVersion(String tenantId, String userId, String version);

    BackupMutationResult putBackupKeys(
            String tenantId,
            String userId,
            String version,
            String roomId,
            String sessionId,
            Map<String, Object> request);

    Map<String, Object> backupKeys(
            String tenantId,
            String userId,
            String version,
            String roomId,
            String sessionId);

    void deleteBackupKeys(
            String tenantId,
            String userId,
            String version,
            String roomId,
            String sessionId);

    void putAccountData(String tenantId, String userId, String eventType, Map<String, Object> content, long revision);

    Optional<Map<String, Object>> accountData(String tenantId, String userId, String eventType);

    Map<String, Map<String, Object>> accountData(String tenantId, String userId);

    SupportSafeStats supportSafeStats();

    record DeviceRecord(
            String userId,
            String deviceId,
            Map<String, Object> deviceKeys,
            long changedRevision,
            boolean revoked) {}

    record ClaimedKey(String keyId, Object value, boolean fallback) {}

    record CrossSigningRecord(
            Map<String, Object> masterKey,
            Map<String, Object> selfSigningKey,
            Map<String, Object> userSigningKey) {}

    record ToDeviceRecord(long revision, String senderUserId, String eventType, Map<String, Object> content) {}

    record DeviceListState(boolean shared, long changedRevision) {}

    record BackupVersionRecord(
            String version,
            String algorithm,
            Map<String, Object> authData,
            boolean current,
            long revision,
            long count) {}

    record BackupMutationResult(long count, String etag) {}

    record SupportSafeStats(
            long activeDeviceCount,
            long revokedDeviceCount,
            long queuedEventCount,
            long encryptedEventCount,
            long plaintextRoomKeyEventCount,
            long olmPreKeyEnvelopeCount,
            long olmExistingSessionEnvelopeCount,
            long targetedDeviceCount,
            long transactionCount,
            long currentRevision) {}
}
