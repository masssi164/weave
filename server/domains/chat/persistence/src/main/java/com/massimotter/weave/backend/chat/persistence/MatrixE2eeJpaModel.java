package com.massimotter.weave.backend.chat.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Normalized Matrix public-envelope storage. Secret device/session keys stay on clients. */
public final class MatrixE2eeJpaModel {
    private MatrixE2eeJpaModel() {}

    @Entity(name = "MatrixSyncHead")
    @Table(name = "weave_matrix_sync_heads", check = @CheckConstraint(name = "ck_weave_matrix_sync_head_revision", constraint = "revision >= 0"))
    public static class SyncHead {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 255)
        String tenantId;
        @Column(name = "revision", nullable = false, options = "default 0")
        long revision;
        @Column(name = "row_version", nullable = false, options = "default 0")
        long rowVersion;
        @Column(name = "updated_at_utc", nullable = false, options = "default current_timestamp")
        Instant updatedAtUtc = Instant.now();
        protected SyncHead() {}
    }

    @Entity(name = "MatrixDevice")
    @Table(name = "weave_matrix_devices", check = @CheckConstraint(constraint = "changed_revision >= 0"))
    @IdClass(DeviceId.class)
    public static class Device {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Column(name = "device_keys_json", nullable = true, columnDefinition = "text")
        String deviceKeysJson;
        @Column(name = "changed_revision", nullable = false, options = "default 0")
        long changedRevision;
        @Column(name = "revoked", nullable = false, options = "default false")
        boolean revoked;
        @Column(name = "row_version", nullable = false, options = "default 0")
        long rowVersion;
        @Column(name = "updated_at_utc", nullable = false, options = "default current_timestamp")
        Instant updatedAtUtc = Instant.now();
        protected Device() {}
    }

    public static class DeviceId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        public DeviceId() {}
        @Override public boolean equals(Object value) {
            return value instanceof DeviceId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId); }
    }

    @Entity(name = "MatrixOneTimeKey")
    @Table(name = "weave_matrix_one_time_keys", indexes = @Index(name = "weave_matrix_otk_claim_idx", columnList = "tenant_id,user_id,device_id,algorithm,key_id", options = "where claimed_at_utc is null"))
    @IdClass(OneTimeKeyId.class)
    public static class OneTimeKey {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Id
        @Column(name = "key_id", nullable = false, length = 320)
        String keyId;
        @Column(name = "algorithm", nullable = false, length = 128)
        String algorithm;
        @Column(name = "key_json", nullable = false, columnDefinition = "text")
        String keyJson;
        @Column(name = "uploaded_at_utc", nullable = false, options = "default current_timestamp")
        Instant uploadedAtUtc = Instant.now();
        @Column(name = "claimed_at_utc", nullable = true)
        Instant claimedAtUtc;
        @Column(name = "claim_ref", nullable = true)
        UUID claimRef;
        protected OneTimeKey() {}
    }

    public static class OneTimeKeyId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        String keyId;
        public OneTimeKeyId() {}
        @Override public boolean equals(Object value) {
            return value instanceof OneTimeKeyId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId) && Objects.equals(keyId, other.keyId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId, keyId); }
    }

    @Entity(name = "MatrixFallbackKey")
    @Table(name = "weave_matrix_fallback_keys")
    @IdClass(FallbackKeyId.class)
    public static class FallbackKey {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Id
        @Column(name = "key_id", nullable = false, length = 320)
        String keyId;
        @Column(name = "algorithm", nullable = false, length = 128)
        String algorithm;
        @Column(name = "key_json", nullable = false, columnDefinition = "text")
        String keyJson;
        @Column(name = "used", nullable = false, options = "default false")
        boolean used;
        @Column(name = "updated_at_utc", nullable = false, options = "default current_timestamp")
        Instant updatedAtUtc = Instant.now();
        protected FallbackKey() {}
    }

    public static class FallbackKeyId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        String keyId;
        public FallbackKeyId() {}
        @Override public boolean equals(Object value) {
            return value instanceof FallbackKeyId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId) && Objects.equals(keyId, other.keyId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId, keyId); }
    }

    @Entity(name = "MatrixCrossSigningKey")
    @Table(name = "weave_matrix_cross_signing_keys", check = @CheckConstraint(constraint = "usage in ('master','self_signing','user_signing')"))
    @IdClass(CrossSigningKeyId.class)
    public static class CrossSigningKey {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "usage", nullable = false, length = 32)
        String usage;
        @Column(name = "key_id", nullable = false, length = 320)
        String keyId;
        @Column(name = "key_json", nullable = false, columnDefinition = "text")
        String keyJson;
        @Column(name = "changed_revision", nullable = false, options = "default 0")
        long changedRevision;
        protected CrossSigningKey() {}
    }

    public static class CrossSigningKeyId implements Serializable {
        String tenantId;
        String userId;
        String usage;
        public CrossSigningKeyId() {}
        @Override public boolean equals(Object value) {
            return value instanceof CrossSigningKeyId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(usage, other.usage);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, usage); }
    }

    @Entity(name = "MatrixToDeviceMessage")
    @Table(name = "weave_matrix_to_device_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id","sender_user_id","transaction_id","target_user_id","target_device_id","event_type"}), indexes = {@Index(name = "weave_matrix_to_device_sync_idx", columnList = "tenant_id,target_user_id,target_device_id,sequence_id"), @Index(name = "ix_weave_matrix_to_device_revision", columnList = "tenant_id,target_user_id,target_device_id,revision_id")})
    public static class ToDeviceMessage {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "sequence_id", nullable = false)
        long sequenceId;
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Column(name = "target_user_id", nullable = false, length = 512)
        String targetUserId;
        @Column(name = "target_device_id", nullable = false, length = 128)
        String targetDeviceId;
        @Column(name = "sender_user_id", nullable = false, length = 512)
        String senderUserId;
        @Column(name = "event_type", nullable = false, length = 255)
        String eventType;
        @Column(name = "transaction_id", nullable = false, length = 255)
        String transactionId;
        @Column(name = "content_json", nullable = false, columnDefinition = "text")
        String contentJson;
        @Column(name = "created_at_utc", nullable = false, options = "default current_timestamp")
        Instant createdAtUtc = Instant.now();
        @Column(name = "revision_id", nullable = false)
        long revisionId;
        protected ToDeviceMessage() {}
    }

    @Entity(name = "MatrixSyncProgress")
    @Table(name = "weave_matrix_device_sync_progress", check = {@CheckConstraint(constraint = "to_device_sequence >= 0"), @CheckConstraint(constraint = "device_list_revision >= 0")})
    @IdClass(SyncProgressId.class)
    public static class SyncProgress {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Column(name = "to_device_sequence", nullable = false, options = "default 0")
        long toDeviceSequence;
        @Column(name = "device_list_revision", nullable = false, options = "default 0")
        long deviceListRevision;
        @Column(name = "last_issued_revision", nullable = false, options = "default 0")
        long lastIssuedRevision;
        @Column(name = "row_version", nullable = false, options = "default 0")
        long rowVersion;
        @Column(name = "updated_at_utc", nullable = false, options = "default current_timestamp")
        Instant updatedAtUtc = Instant.now();
        protected SyncProgress() {}
    }

    public static class SyncProgressId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        public SyncProgressId() {}
        @Override public boolean equals(Object value) {
            return value instanceof SyncProgressId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId); }
    }

    @Entity(name = "MatrixAccountData")
    @Table(name = "weave_matrix_account_data")
    @IdClass(AccountDataId.class)
    public static class AccountData {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "event_type", nullable = false, length = 255)
        String eventType;
        @Column(name = "content_json", nullable = false, columnDefinition = "text")
        String contentJson;
        @Column(name = "changed_revision", nullable = false, options = "default 0")
        long changedRevision;
        protected AccountData() {}
    }

    public static class AccountDataId implements Serializable {
        String tenantId;
        String userId;
        String eventType;
        public AccountDataId() {}
        @Override public boolean equals(Object value) {
            return value instanceof AccountDataId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(eventType, other.eventType);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, eventType); }
    }

    // Parenthesized tenant_id is an equivalent index expression. On the pinned Hibernate it
    // preserves index DDL instead of incorrectly rendering a partial inline UNIQUE constraint.
    @Entity(name = "MatrixBackupVersion")
    @Table(name = "weave_matrix_key_backup_versions", indexes = @Index(name = "weave_matrix_key_backup_current_idx", columnList = "(tenant_id),user_id", unique = true, options = "where current_version"))
    @IdClass(BackupVersionId.class)
    public static class BackupVersion {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "version_id", nullable = false)
        long versionId;
        @Column(name = "algorithm", nullable = false, length = 128)
        String algorithm;
        @Column(name = "auth_data_json", nullable = false, columnDefinition = "text")
        String authDataJson;
        @Column(name = "current_version", nullable = false, options = "default false")
        boolean currentVersion;
        @Column(name = "revision", nullable = false, options = "default 0")
        long revision;
        protected BackupVersion() {}
    }

    public static class BackupVersionId implements Serializable {
        String tenantId;
        String userId;
        long versionId;
        public BackupVersionId() {}
        @Override public boolean equals(Object value) {
            return value instanceof BackupVersionId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(versionId, other.versionId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, versionId); }
    }

    @Entity(name = "MatrixBackupSession")
    @Table(name = "weave_matrix_key_backup_sessions")
    @IdClass(BackupSessionId.class)
    public static class BackupSession {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "version_id", nullable = false)
        long versionId;
        @Id
        @Column(name = "room_id", nullable = false, length = 512)
        String roomId;
        @Id
        @Column(name = "session_id", nullable = false, length = 512)
        String sessionId;
        @Column(name = "payload_json", nullable = false, columnDefinition = "text")
        String payloadJson;
        protected BackupSession() {}
    }

    public static class BackupSessionId implements Serializable {
        String tenantId;
        String userId;
        long versionId;
        String roomId;
        String sessionId;
        public BackupSessionId() {}
        @Override public boolean equals(Object value) {
            return value instanceof BackupSessionId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(versionId, other.versionId) && Objects.equals(roomId, other.roomId) && Objects.equals(sessionId, other.sessionId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, versionId, roomId, sessionId); }
    }

    @Entity(name = "MatrixSharedUser")
    @Table(name = "weave_matrix_shared_users", check = @CheckConstraint(name = "ck_weave_matrix_shared_user_revision", constraint = "changed_revision >= 0"), indexes = @Index(name = "ix_weave_matrix_shared_users_changes", columnList = "tenant_id,user_id,device_id,changed_revision"))
    @IdClass(SharedUserId.class)
    public static class SharedUser {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 255)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 255)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 255)
        String deviceId;
        @Id
        @Column(name = "shared_user_id", nullable = false, length = 255)
        String sharedUserId;
        @Column(name = "shared", nullable = false)
        boolean shared;
        @Column(name = "changed_revision", nullable = false, options = "default 0")
        long changedRevision;
        protected SharedUser() {}
    }

    public static class SharedUserId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        String sharedUserId;
        public SharedUserId() {}
        @Override public boolean equals(Object value) {
            return value instanceof SharedUserId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId) && Objects.equals(sharedUserId, other.sharedUserId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId, sharedUserId); }
    }

    @Entity(name = "MatrixSharedUserChange")
    @Table(name = "weave_matrix_shared_user_changes", check = @CheckConstraint(constraint = "changed_revision >= 0"), indexes = @Index(name = "weave_matrix_shared_user_changes_lookup_idx", columnList = "tenant_id,user_id,device_id,changed_revision,shared_user_id"))
    @IdClass(SharedUserChangeId.class)
    public static class SharedUserChange {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 255)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Id
        @Column(name = "changed_revision", nullable = false, options = "default 0")
        long changedRevision;
        @Id
        @Column(name = "shared_user_id", nullable = false, length = 512)
        String sharedUserId;
        @Column(name = "shared", nullable = false)
        boolean shared;
        protected SharedUserChange() {}
    }

    public static class SharedUserChangeId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        long changedRevision;
        String sharedUserId;
        public SharedUserChangeId() {}
        @Override public boolean equals(Object value) {
            return value instanceof SharedUserChangeId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId) && Objects.equals(changedRevision, other.changedRevision) && Objects.equals(sharedUserId, other.sharedUserId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId, changedRevision, sharedUserId); }
    }

    @Entity(name = "MatrixOidcBinding")
    @Table(name = "weave_matrix_oidc_device_bindings", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id","user_id","device_id","oidc_session_hash"}))
    @IdClass(OidcBindingId.class)
    public static class OidcBinding {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "oidc_session_hash", nullable = false, length = 64)
        String oidcSessionHash;
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Column(name = "bound_at_utc", nullable = false, options = "default current_timestamp")
        Instant boundAtUtc = Instant.now();
        protected OidcBinding() {}
    }

    public static class OidcBindingId implements Serializable {
        String tenantId;
        String userId;
        String oidcSessionHash;
        public OidcBindingId() {}
        @Override public boolean equals(Object value) {
            return value instanceof OidcBindingId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(oidcSessionHash, other.oidcSessionHash);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, oidcSessionHash); }
    }

    @Entity(name = "MatrixDeviceProof")
    @Table(name = "weave_matrix_device_proofs", check = @CheckConstraint(constraint = "proof_hash ~ '^[0-9a-f]{64}$'"))
    @IdClass(DeviceProofId.class)
    public static class DeviceProof {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Column(name = "proof_hash", nullable = false, length = 64)
        String proofHash;
        @Column(name = "bound_at_utc", nullable = false, options = "default current_timestamp")
        Instant boundAtUtc = Instant.now();
        protected DeviceProof() {}
    }

    public static class DeviceProofId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        public DeviceProofId() {}
        @Override public boolean equals(Object value) {
            return value instanceof DeviceProofId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId); }
    }

    @Entity(name = "MatrixRecoveryChallenge")
    @Table(name = "weave_matrix_device_recovery_challenges", check = @CheckConstraint(constraint = "proof_hash ~ '^[0-9a-f]{64}$'"))
    @IdClass(RecoveryChallengeId.class)
    public static class RecoveryChallenge {
        @Id
        @Column(name = "tenant_id", nullable = false, length = 160)
        String tenantId;
        @Id
        @Column(name = "user_id", nullable = false, length = 512)
        String userId;
        @Id
        @Column(name = "device_id", nullable = false, length = 128)
        String deviceId;
        @Column(name = "challenge_id", nullable = false)
        UUID challengeId;
        @Column(name = "challenge_text", nullable = false, columnDefinition = "text")
        String challengeText;
        @Column(name = "proof_hash", nullable = false, length = 64)
        String proofHash;
        @Column(name = "expires_at_utc", nullable = false)
        Instant expiresAtUtc;
        @Column(name = "consumed_at_utc", nullable = true)
        Instant consumedAtUtc;
        protected RecoveryChallenge() {}
    }

    public static class RecoveryChallengeId implements Serializable {
        String tenantId;
        String userId;
        String deviceId;
        public RecoveryChallengeId() {}
        @Override public boolean equals(Object value) {
            return value instanceof RecoveryChallengeId other && Objects.equals(tenantId, other.tenantId) && Objects.equals(userId, other.userId) && Objects.equals(deviceId, other.deviceId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, userId, deviceId); }
    }
}
