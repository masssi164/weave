package com.massimotter.weave.backend.matrix;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL authority for Matrix facade routing/E2EE metadata. */
@Component
public class MatrixE2eeRelationalStore implements MatrixE2eePersistence {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    MatrixE2eeRelationalStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional(readOnly = true)
    public long currentRevision(String tenantId) {
        Long revision = jdbc.query(
                "select revision from weave_matrix_sync_heads where tenant_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L,
                tenantId);
        return revision == null ? 0 : revision;
    }

    @Override
    @Transactional
    public long nextRevision(String tenantId) {
        jdbc.update(
                "insert into weave_matrix_sync_heads(tenant_id,revision,row_version,updated_at_utc) values (?,0,0,now()) on conflict (tenant_id) do nothing",
                tenantId);
        Long revision = jdbc.query(
                "select revision from weave_matrix_sync_heads where tenant_id=? for update",
                rs -> rs.next() ? rs.getLong(1) : 0L,
                tenantId);
        long next = (revision == null ? 0 : revision) + 1;
        jdbc.update(
                "update weave_matrix_sync_heads set revision=?,row_version=row_version+1,updated_at_utc=now() where tenant_id=?",
                next,
                tenantId);
        return next;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceRecord> device(String tenantId, String userId, String deviceId) {
        return jdbc.query(
                "select user_id,device_id,device_keys_json,changed_revision,revoked from weave_matrix_devices where tenant_id=? and user_id=? and device_id=?",
                (rs, ignored) -> deviceRecord(rs),
                tenantId,
                userId,
                deviceId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceRecord> devices(String tenantId, String userId, Set<String> requestedDeviceIds) {
        List<DeviceRecord> rows = jdbc.query(
                "select user_id,device_id,device_keys_json,changed_revision,revoked from weave_matrix_devices where tenant_id=? and user_id=? order by device_id",
                (rs, ignored) -> deviceRecord(rs),
                tenantId,
                userId);
        if (requestedDeviceIds == null || requestedDeviceIds.isEmpty()) {
            return rows;
        }
        return rows.stream().filter(row -> requestedDeviceIds.contains(row.deviceId())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<String> activeDeviceIds(String tenantId, String userId) {
        return jdbc.query(
                "select device_id from weave_matrix_devices where tenant_id=? and user_id=? and revoked=false order by device_id",
                (rs, ignored) -> rs.getString(1),
                tenantId,
                userId);
    }

    @Override
    @Transactional
    public void upsertDevice(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> deviceKeys,
            long changedRevision) {
        jdbc.update(
                """
                insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json,changed_revision,revoked,row_version,updated_at_utc)
                values (?,?,?,?,?,false,0,now())
                on conflict (tenant_id,user_id,device_id) do update
                set device_keys_json=case when excluded.device_keys_json='{}' then weave_matrix_devices.device_keys_json else excluded.device_keys_json end,
                    changed_revision=excluded.changed_revision,
                    row_version=weave_matrix_devices.row_version+1,
                    updated_at_utc=now()
                """,
                tenantId, userId, deviceId, writeJson(deviceKeys == null ? Map.of() : deviceKeys), changedRevision);
    }

    @Override
    @Transactional
    public void addOneTimeKeys(String tenantId, String userId, String deviceId, Map<String, Object> keys) {
        keys.forEach((keyId, value) -> jdbc.update(
                """
                insert into weave_matrix_one_time_keys(tenant_id,user_id,device_id,key_id,algorithm,key_json)
                values (?,?,?,?,?,?)
                on conflict (tenant_id,user_id,device_id,key_id) do update set algorithm=excluded.algorithm,key_json=excluded.key_json,claimed_at_utc=null
                """,
                tenantId, userId, deviceId, keyId, algorithm(keyId), writeJson(value)));
    }

    @Override
    @Transactional
    public void replaceFallbackKeys(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> keys,
            long changedRevision) {
        jdbc.update("delete from weave_matrix_fallback_keys where tenant_id=? and user_id=? and device_id=?", tenantId, userId, deviceId);
        keys.forEach((keyId, value) -> jdbc.update(
                "insert into weave_matrix_fallback_keys(tenant_id,user_id,device_id,key_id,algorithm,key_json,used,updated_at_utc) values (?,?,?,?,?,?,false,now())",
                tenantId, userId, deviceId, keyId, algorithm(keyId), writeJson(value)));
        upsertDevice(tenantId, userId, deviceId, Map.of(), changedRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> oneTimeKeyCounts(String tenantId, String userId, String deviceId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(
                "select algorithm,count(*) from weave_matrix_one_time_keys where tenant_id=? and user_id=? and device_id=? group by algorithm order by algorithm",
                rs -> counts.put(rs.getString(1), rs.getLong(2)),
                tenantId, userId, deviceId);
        return Map.copyOf(counts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> unusedFallbackAlgorithms(String tenantId, String userId, String deviceId) {
        return jdbc.query(
                "select distinct algorithm from weave_matrix_fallback_keys where tenant_id=? and user_id=? and device_id=? and used=false order by algorithm",
                (rs, ignored) -> rs.getString(1),
                tenantId, userId, deviceId);
    }

    @Override
    @Transactional
    public Optional<ClaimedKey> claimOneTimeKey(String tenantId, String userId, String deviceId, String requestedAlgorithm) {
        List<ClaimedKey> claimed = jdbc.query(
                """
                delete from weave_matrix_one_time_keys
                where (tenant_id,user_id,device_id,key_id) = (
                    select tenant_id,user_id,device_id,key_id
                    from weave_matrix_one_time_keys
                    where tenant_id=? and user_id=? and device_id=? and algorithm=?
                    order by key_id
                    for update skip locked
                    limit 1
                )
                returning key_id,key_json
                """,
                (rs, ignored) -> new ClaimedKey(rs.getString(1), readValue(rs.getString(2)), false),
                tenantId, userId, deviceId, requestedAlgorithm);
        if (!claimed.isEmpty()) return Optional.of(claimed.getFirst());
        return jdbc.query(
                """
                update weave_matrix_fallback_keys set used=true,updated_at_utc=now()
                where (tenant_id,user_id,device_id,key_id) = (
                    select tenant_id,user_id,device_id,key_id
                    from weave_matrix_fallback_keys
                    where tenant_id=? and user_id=? and device_id=? and algorithm=?
                    order by key_id
                    for update skip locked
                    limit 1
                )
                returning key_id,key_json
                """,
                (rs, ignored) -> new ClaimedKey(rs.getString(1), readValue(rs.getString(2)), true),
                tenantId, userId, deviceId, requestedAlgorithm).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CrossSigningRecord> crossSigning(String tenantId, String userId) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        jdbc.query(
                "select usage,key_json from weave_matrix_cross_signing_keys where tenant_id=? and user_id=? order by usage",
                rs -> values.put(rs.getString(1), readObject(rs.getString(2))),
                tenantId, userId);
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(new CrossSigningRecord(
                values.getOrDefault("master", Map.of()),
                values.getOrDefault("self_signing", Map.of()),
                values.getOrDefault("user_signing", Map.of())));
    }

    @Override
    @Transactional
    public void upsertCrossSigning(
            String tenantId,
            String userId,
            Map<String, Object> masterKey,
            Map<String, Object> selfSigningKey,
            Map<String, Object> userSigningKey,
            long changedRevision) {
        saveSigningKey(tenantId, userId, "master", masterKey, changedRevision);
        saveSigningKey(tenantId, userId, "self_signing", selfSigningKey, changedRevision);
        saveSigningKey(tenantId, userId, "user_signing", userSigningKey, changedRevision);
    }

    @Override
    @Transactional
    public void mergeDeviceSignatures(
            String tenantId,
            String userId,
            String deviceId,
            Map<String, Object> signatures,
            long changedRevision) {
        DeviceRecord device = device(tenantId, userId, deviceId).orElseThrow();
        Map<String, Object> merged = mergeSignatures(device.deviceKeys(), signatures);
        upsertDevice(tenantId, userId, deviceId, merged, changedRevision);
    }

    @Override
    @Transactional
    public void mergeCrossSigningSignatures(
            String tenantId,
            String userId,
            String keyId,
            Map<String, Object> signatures,
            long changedRevision) {
        List<Map.Entry<String, Map<String, Object>>> matches = new ArrayList<>();
        crossSigning(tenantId, userId).ifPresent(record -> {
            matches.add(Map.entry("master", record.masterKey()));
            matches.add(Map.entry("self_signing", record.selfSigningKey()));
            matches.add(Map.entry("user_signing", record.userSigningKey()));
        });
        for (Map.Entry<String, Map<String, Object>> candidate : matches) {
            Object rawKeys = candidate.getValue().get("keys");
            if (!(rawKeys instanceof Map<?, ?> keys) || !keys.containsValue(keyId) && !keys.containsKey(keyId)) continue;
            saveSigningKey(tenantId, userId, candidate.getKey(), mergeSignatures(candidate.getValue(), signatures), changedRevision);
            return;
        }
    }

    @Override
    @Transactional
    public boolean appendToDevice(
            long revision,
            String tenantId,
            String targetUserId,
            String targetDeviceId,
            String senderUserId,
            String eventType,
            String transactionId,
            Map<String, Object> content) {
        return jdbc.update(
                """
                insert into weave_matrix_to_device_messages(
                    revision_id,tenant_id,target_user_id,target_device_id,sender_user_id,event_type,transaction_id,content_json)
                values (?,?,?,?,?,?,?,?)
                on conflict (tenant_id,sender_user_id,transaction_id,target_user_id,target_device_id,event_type) do nothing
                """,
                revision, tenantId, targetUserId, targetDeviceId, senderUserId, eventType, transactionId, writeJson(content)) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToDeviceRecord> toDeviceEvents(
            String tenantId,
            String userId,
            String deviceId,
            long afterRevision,
            long highWater,
            int limit) {
        return jdbc.query(
                """
                select revision_id,sender_user_id,event_type,content_json
                from weave_matrix_to_device_messages
                where tenant_id=? and target_user_id=? and target_device_id=?
                  and revision_id>? and revision_id<=?
                order by revision_id
                limit ?
                """,
                (rs, ignored) -> new ToDeviceRecord(rs.getLong(1), rs.getString(2), rs.getString(3), readObject(rs.getString(4))),
                tenantId, userId, deviceId, afterRevision, highWater, limit);
    }

    @Override
    @Transactional
    public void recordDeviceSyncProgress(String tenantId, String userId, String deviceId, long revision) {
        jdbc.update(
                """
                insert into weave_matrix_device_sync_progress(tenant_id,user_id,device_id,to_device_sequence,device_list_revision,last_issued_revision,row_version,updated_at_utc)
                values (?,?,?,?,?,?,0,now())
                on conflict (tenant_id,user_id,device_id) do update
                set to_device_sequence=greatest(weave_matrix_device_sync_progress.to_device_sequence,excluded.to_device_sequence),
                    last_issued_revision=greatest(weave_matrix_device_sync_progress.last_issued_revision,excluded.last_issued_revision),
                    row_version=weave_matrix_device_sync_progress.row_version+1,
                    updated_at_utc=now()
                """,
                tenantId, userId, deviceId, revision, 0, revision);
    }

    @Override
    @Transactional
    public Map<String, DeviceListState> reconcileSharedUsers(
            String tenantId,
            String userId,
            String deviceId,
            Set<String> currentlySharedUserIds) {
        Map<String, DeviceListState> existing = sharedUserChanges(tenantId, userId, deviceId, -1, Long.MAX_VALUE);
        Set<String> union = new LinkedHashSet<>(existing.keySet());
        union.addAll(currentlySharedUserIds);
        Map<String, DeviceListState> changed = new LinkedHashMap<>();
        for (String sharedUserId : union) {
            boolean desired = currentlySharedUserIds.contains(sharedUserId);
            DeviceListState prior = existing.get(sharedUserId);
            if (prior == null || prior.shared() != desired) {
                long revision = nextRevision(tenantId);
                jdbc.update(
                        """
                        insert into weave_matrix_shared_users(tenant_id,user_id,device_id,shared_user_id,shared,changed_revision)
                        values (?,?,?,?,?,?)
                        on conflict (tenant_id,user_id,device_id,shared_user_id) do update
                        set shared=excluded.shared,changed_revision=excluded.changed_revision
                        """,
                        tenantId, userId, deviceId, sharedUserId, desired, revision);
                changed.put(sharedUserId, new DeviceListState(desired, revision));
            }
        }
        return Map.copyOf(changed);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, DeviceListState> sharedUserChanges(
            String tenantId,
            String userId,
            String deviceId,
            long afterRevision,
            long highWater) {
        Map<String, DeviceListState> result = new LinkedHashMap<>();
        jdbc.query(
                """
                select shared_user_id,shared,changed_revision from weave_matrix_shared_users
                where tenant_id=? and user_id=? and device_id=? and changed_revision>? and changed_revision<=?
                order by changed_revision,shared_user_id
                """,
                rs -> result.put(rs.getString(1), new DeviceListState(rs.getBoolean(2), rs.getLong(3))),
                tenantId, userId, deviceId, afterRevision, highWater);
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> deviceUsersChanged(String tenantId, long afterRevision, long highWater) {
        return jdbc.query(
                "select distinct user_id from weave_matrix_devices where tenant_id=? and changed_revision>? and changed_revision<=? order by user_id",
                (rs, ignored) -> rs.getString(1), tenantId, afterRevision, highWater);
    }

    @Override
    @Transactional
    public void revokeDevice(String tenantId, String userId, String deviceId, long changedRevision) {
        int updated = jdbc.update(
                "update weave_matrix_devices set revoked=true,changed_revision=?,row_version=row_version+1,updated_at_utc=now() where tenant_id=? and user_id=? and device_id=?",
                changedRevision, tenantId, userId, deviceId);
        if (updated == 0) throw new IllegalArgumentException("Matrix device not found");
        jdbc.update("delete from weave_matrix_one_time_keys where tenant_id=? and user_id=? and device_id=?", tenantId, userId, deviceId);
        jdbc.update("delete from weave_matrix_fallback_keys where tenant_id=? and user_id=? and device_id=?", tenantId, userId, deviceId);
        jdbc.update("delete from weave_matrix_shared_users where tenant_id=? and user_id=? and device_id=?", tenantId, userId, deviceId);
    }

    @Override
    @Transactional
    public boolean bindOidcSession(String tenantId, String userId, String sessionHash, String deviceId) {
        List<String> existing = jdbc.query(
                "select device_id from weave_matrix_oidc_device_bindings where tenant_id=? and user_id=? and oidc_session_hash=? for update",
                (rs, ignored) -> rs.getString(1), tenantId, userId, sessionHash);
        if (!existing.isEmpty()) return existing.getFirst().equals(deviceId);
        jdbc.update("insert into weave_matrix_oidc_device_bindings(tenant_id,user_id,oidc_session_hash,device_id) values (?,?,?,?)",
                tenantId, userId, sessionHash, deviceId);
        return true;
    }

    @Override
    @Transactional
    public String createBackupVersion(String tenantId, String userId, String algorithm, Map<String, Object> authData) {
        Long next = jdbc.query(
                "select coalesce(max(version_id),0)+1 from weave_matrix_key_backup_versions where tenant_id=? and user_id=? for update",
                rs -> rs.next() ? rs.getLong(1) : 1L,
                tenantId, userId);
        long version = next == null ? 1 : next;
        jdbc.update("update weave_matrix_key_backup_versions set current_version=false where tenant_id=? and user_id=?", tenantId, userId);
        jdbc.update(
                "insert into weave_matrix_key_backup_versions(tenant_id,user_id,version_id,algorithm,auth_data_json,current_version,revision) values (?,?,?,?,?,true,1)",
                tenantId, userId, version, algorithm, writeJson(authData));
        return Long.toString(version);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BackupVersionRecord> backupVersion(String tenantId, String userId, String version) {
        if (version == null || version.isBlank()) return currentBackupVersion(tenantId, userId);
        long value;
        try { value = Long.parseLong(version); } catch (NumberFormatException exception) { return Optional.empty(); }
        return jdbc.query(
                """
                select version_id,algorithm,auth_data_json,current_version,revision,
                       (select count(*) from weave_matrix_key_backup_sessions s where s.tenant_id=v.tenant_id and s.user_id=v.user_id and s.version_id=v.version_id) as session_count
                from weave_matrix_key_backup_versions v where tenant_id=? and user_id=? and version_id=?
                """,
                (rs, ignored) -> backupRecord(rs), tenantId, userId, value).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BackupVersionRecord> currentBackupVersion(String tenantId, String userId) {
        return jdbc.query(
                """
                select version_id,algorithm,auth_data_json,current_version,revision,
                       (select count(*) from weave_matrix_key_backup_sessions s where s.tenant_id=v.tenant_id and s.user_id=v.user_id and s.version_id=v.version_id) as session_count
                from weave_matrix_key_backup_versions v where tenant_id=? and user_id=? and current_version=true
                order by version_id desc limit 1
                """,
                (rs, ignored) -> backupRecord(rs), tenantId, userId).stream().findFirst();
    }

    @Override
    @Transactional
    public void updateBackupVersion(String tenantId, String userId, String version, String algorithm, Map<String, Object> authData) {
        int updated = jdbc.update(
                "update weave_matrix_key_backup_versions set algorithm=?,auth_data_json=?,revision=revision+1 where tenant_id=? and user_id=? and version_id=?",
                algorithm, writeJson(authData), tenantId, userId, Long.parseLong(version));
        if (updated == 0) throw new IllegalArgumentException("backup version not found");
    }

    @Override
    @Transactional
    public boolean deleteBackupVersion(String tenantId, String userId, String version) {
        long parsed = Long.parseLong(version);
        jdbc.update("delete from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=?", tenantId, userId, parsed);
        return jdbc.update("delete from weave_matrix_key_backup_versions where tenant_id=? and user_id=? and version_id=?", tenantId, userId, parsed) == 1;
    }

    @Override
    @Transactional
    public BackupMutationResult putBackupKeys(
            String tenantId,
            String userId,
            String version,
            String roomId,
            String sessionId,
            Map<String, Object> request) {
        long parsed = Long.parseLong(version);
        if (sessionId != null) {
            upsertBackupSession(tenantId, userId, parsed, roomId, sessionId, request);
        } else if (roomId != null) {
            objectMap(request.get("sessions")).forEach((id, value) ->
                    upsertBackupSession(tenantId, userId, parsed, roomId, id, objectMap(value)));
        } else {
            objectMap(request.get("rooms")).forEach((room, rawRoom) ->
                    objectMap(objectMap(rawRoom).get("sessions")).forEach((id, value) ->
                            upsertBackupSession(tenantId, userId, parsed, room, id, objectMap(value))));
        }
        jdbc.update("update weave_matrix_key_backup_versions set revision=revision+1 where tenant_id=? and user_id=? and version_id=?", tenantId, userId, parsed);
        BackupVersionRecord record = backupVersion(tenantId, userId, version).orElseThrow();
        return new BackupMutationResult(record.count(), Long.toString(record.revision()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> backupKeys(String tenantId, String userId, String version, String roomId, String sessionId) {
        long parsed = Long.parseLong(version);
        if (sessionId != null) {
            return jdbc.query(
                    "select payload_json from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=? and room_id=? and session_id=?",
                    (rs, ignored) -> readObject(rs.getString(1)), tenantId, userId, parsed, roomId, sessionId)
                    .stream().findFirst().orElseThrow();
        }
        if (roomId != null) {
            Map<String, Object> sessions = new LinkedHashMap<>();
            jdbc.query("select session_id,payload_json from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=? and room_id=? order by session_id",
                    rs -> sessions.put(rs.getString(1), readObject(rs.getString(2))), tenantId, userId, parsed, roomId);
            return Map.of("sessions", Map.copyOf(sessions));
        }
        Map<String, Map<String, Object>> rooms = new LinkedHashMap<>();
        jdbc.query("select room_id,session_id,payload_json from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=? order by room_id,session_id",
                rs -> rooms.computeIfAbsent(rs.getString(1), ignored -> new LinkedHashMap<>())
                        .put(rs.getString(2), readObject(rs.getString(3))), tenantId, userId, parsed);
        Map<String, Object> projected = new LinkedHashMap<>();
        rooms.forEach((room, sessions) -> projected.put(room, Map.of("sessions", Map.copyOf(sessions))));
        return Map.of("rooms", Map.copyOf(projected));
    }

    @Override
    @Transactional
    public void deleteBackupKeys(String tenantId, String userId, String version, String roomId, String sessionId) {
        long parsed = Long.parseLong(version);
        if (sessionId != null) {
            jdbc.update("delete from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=? and room_id=? and session_id=?",
                    tenantId, userId, parsed, roomId, sessionId);
        } else if (roomId != null) {
            jdbc.update("delete from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=? and room_id=?",
                    tenantId, userId, parsed, roomId);
        } else {
            jdbc.update("delete from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=?", tenantId, userId, parsed);
        }
        jdbc.update("update weave_matrix_key_backup_versions set revision=revision+1 where tenant_id=? and user_id=? and version_id=?", tenantId, userId, parsed);
    }

    @Override
    @Transactional
    public void putAccountData(String tenantId, String userId, String eventType, Map<String, Object> content, long revision) {
        jdbc.update(
                """
                insert into weave_matrix_account_data(tenant_id,user_id,event_type,content_json,changed_revision)
                values (?,?,?,?,?)
                on conflict (tenant_id,user_id,event_type) do update set content_json=excluded.content_json,changed_revision=excluded.changed_revision
                """,
                tenantId, userId, eventType, writeJson(content), revision);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> accountData(String tenantId, String userId, String eventType) {
        return jdbc.query("select content_json from weave_matrix_account_data where tenant_id=? and user_id=? and event_type=?",
                (rs, ignored) -> readObject(rs.getString(1)), tenantId, userId, eventType).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> accountData(String tenantId, String userId) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        jdbc.query("select event_type,content_json from weave_matrix_account_data where tenant_id=? and user_id=? order by event_type",
                rs -> values.put(rs.getString(1), readObject(rs.getString(2))), tenantId, userId);
        return Map.copyOf(values);
    }

    @Override
    @Transactional(readOnly = true)
    public SupportSafeStats supportSafeStats() {
        long active = scalar("select count(*) from weave_matrix_devices where revoked=false");
        long revoked = scalar("select count(*) from weave_matrix_devices where revoked=true");
        long queued = scalar("select count(*) from weave_matrix_to_device_messages");
        long encrypted = scalar("select count(*) from weave_matrix_to_device_messages where event_type='m.room.encrypted'");
        long plaintextRoomKey = scalar("select count(*) from weave_matrix_to_device_messages where event_type in ('m.room_key','m.forwarded_room_key')");
        long targets = scalar("select count(distinct tenant_id || chr(0) || target_user_id || chr(0) || target_device_id) from weave_matrix_to_device_messages");
        long transactions = scalar("select count(distinct tenant_id || chr(0) || sender_user_id || chr(0) || transaction_id) from weave_matrix_to_device_messages");
        long revision = scalar("select coalesce(max(revision),0) from weave_matrix_sync_heads");
        return new SupportSafeStats(active, revoked, queued, encrypted, plaintextRoomKey, 0, 0, targets, transactions, revision);
    }

    private DeviceRecord deviceRecord(ResultSet rs) throws SQLException {
        return new DeviceRecord(rs.getString(1), rs.getString(2), readObject(rs.getString(3)), rs.getLong(4), rs.getBoolean(5));
    }

    private BackupVersionRecord backupRecord(ResultSet rs) throws SQLException {
        return new BackupVersionRecord(Long.toString(rs.getLong(1)), rs.getString(2), readObject(rs.getString(3)), rs.getBoolean(4), rs.getLong(5), rs.getLong(6));
    }

    private void saveSigningKey(String tenantId, String userId, String usage, Map<String, Object> key, long revision) {
        if (key == null || key.isEmpty()) {
            jdbc.update("delete from weave_matrix_cross_signing_keys where tenant_id=? and user_id=? and usage=?", tenantId, userId, usage);
            return;
        }
        String keyId = objectMap(key.get("keys")).entrySet().stream().findFirst().map(Map.Entry::getKey).orElse(usage);
        jdbc.update(
                """
                insert into weave_matrix_cross_signing_keys(tenant_id,user_id,usage,key_id,key_json,changed_revision)
                values (?,?,?,?,?,?)
                on conflict (tenant_id,user_id,usage) do update set key_id=excluded.key_id,key_json=excluded.key_json,changed_revision=excluded.changed_revision
                """,
                tenantId, userId, usage, keyId, writeJson(key), revision);
    }

    private void upsertBackupSession(String tenantId, String userId, long version, String roomId, String sessionId, Map<String, Object> payload) {
        jdbc.update(
                """
                insert into weave_matrix_key_backup_sessions(tenant_id,user_id,version_id,room_id,session_id,payload_json)
                values (?,?,?,?,?,?)
                on conflict (tenant_id,user_id,version_id,room_id,session_id) do update set payload_json=excluded.payload_json
                """,
                tenantId, userId, version, roomId, sessionId, writeJson(payload));
    }

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

    private String algorithm(String keyId) {
        int separator = keyId.indexOf(':');
        return separator > 0 ? keyId.substring(0, separator) : keyId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("expected object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> {
            if (!(key instanceof String text)) throw new IllegalArgumentException("expected string key");
            result.put(text, nested);
        });
        return result;
    }

    private Map<String, Object> readObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked") Map<String, Object> value = objectMapper.readValue(json, Map.class);
            return value == null ? Map.of() : Map.copyOf(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Matrix relational persistence object is invalid", exception);
        }
    }

    private Object readValue(String json) {
        try { return objectMapper.readValue(json, Object.class); }
        catch (JacksonException exception) { throw new IllegalStateException("Matrix relational persistence value is invalid", exception); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Matrix relational persistence value could not be serialized", exception); }
    }

    private long scalar(String sql) {
        Long value = jdbc.query(sql, rs -> rs.next() ? rs.getLong(1) : 0L);
        return value == null ? 0 : value;
    }
}
