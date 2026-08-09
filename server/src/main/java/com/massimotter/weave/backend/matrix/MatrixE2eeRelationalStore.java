package com.massimotter.weave.backend.matrix;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Normalized PostgreSQL authority for Matrix facade routing/E2EE metadata.
 *
 * <p>The public shape intentionally mirrors the service's transient projection so
 * the protocol behavior can remain stable while persistence is normalized. No
 * tenant-wide serialized snapshot is stored: load() reconstructs an ephemeral
 * document from rows and save() performs row-scoped upserts.</p>
 */
@Component
public class MatrixE2eeRelationalStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    MatrixE2eeRelationalStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public boolean durable() {
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<SnapshotDocument> load(String tenantId) {
        long sequence = currentSequence(tenantId);
        List<Map<String, Object>> devices = jdbc.query(
                """
                select user_id, device_id, device_keys_json, changed_revision, revoked
                from weave_matrix_devices
                where tenant_id = ?
                order by user_id, device_id
                """,
                (rs, ignored) -> deviceProjection(tenantId, rs),
                tenantId);
        List<Map<String, Object>> crossSigning = jdbc.query(
                """
                select user_id, usage, key_json
                from weave_matrix_cross_signing_keys
                where tenant_id = ?
                order by user_id, usage
                """,
                (rs, ignored) -> Map.<String, Object>of(
                        "userId", rs.getString("user_id"),
                        "usage", rs.getString("usage"),
                        "key", readObject(rs.getString("key_json"))),
                tenantId);
        List<Map<String, Object>> groupedCrossSigning = groupCrossSigning(crossSigning);
        List<Map<String, Object>> toDevice = jdbc.query(
                """
                select sequence_id, target_user_id, target_device_id, sender_user_id, event_type, content_json
                from weave_matrix_to_device_messages
                where tenant_id = ?
                order by sequence_id
                """,
                (rs, ignored) -> Map.<String, Object>of(
                        "sequence", rs.getLong("sequence_id"),
                        "tenantId", tenantId,
                        "targetUserId", rs.getString("target_user_id"),
                        "targetDeviceId", rs.getString("target_device_id"),
                        "senderUserId", rs.getString("sender_user_id"),
                        "eventType", rs.getString("event_type"),
                        "content", readObject(rs.getString("content_json"))),
                tenantId);
        List<Map<String, Object>> transactions = jdbc.query(
                """
                select distinct sender_user_id, transaction_id
                from weave_matrix_to_device_messages
                where tenant_id = ?
                order by sender_user_id, transaction_id
                """,
                (rs, ignored) -> Map.<String, Object>of(
                        "tenantId", tenantId,
                        "userId", rs.getString("sender_user_id"),
                        "transactionId", rs.getString("transaction_id")),
                tenantId);
        List<Map<String, Object>> backups = loadBackups(tenantId);
        Map<String, Long> backupSequences = backups.stream().collect(Collectors.toMap(
                value -> String.valueOf(value.get("userId")),
                value -> Long.parseLong(String.valueOf(value.get("version"))),
                Math::max,
                LinkedHashMap::new));
        Map<String, Map<String, Map<String, Object>>> accountData = loadAccountData(tenantId);
        List<Map<String, Object>> oidcBindings = jdbc.query(
                """
                select user_id, oidc_session_hash, device_id
                from weave_matrix_oidc_device_bindings
                where tenant_id = ?
                order by user_id, oidc_session_hash
                """,
                (rs, ignored) -> Map.<String, Object>of(
                        "userId", rs.getString("user_id"),
                        "sessionHash", rs.getString("oidc_session_hash"),
                        "deviceId", rs.getString("device_id")),
                tenantId);

        if (sequence == 0 && devices.isEmpty() && crossSigning.isEmpty() && toDevice.isEmpty()
                && backups.isEmpty() && accountData.isEmpty() && oidcBindings.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("devices", devices);
        projection.put("crossSigning", groupedCrossSigning);
        projection.put("toDeviceEvents", toDevice);
        projection.put("toDeviceTransactions", transactions);
        projection.put("backups", backups);
        projection.put("backupVersionSequences", backupSequences);
        projection.put("accountData", accountData);
        projection.put("oidcSessionBindings", oidcBindings);
        return Optional.of(new SnapshotDocument(sequence, writeJson(projection)));
    }

    @Transactional
    public void save(String tenantId, long sequence, String projectionJson) {
        JsonNode root = readTree(projectionJson);
        upsertHead(tenantId, sequence);
        for (JsonNode device : root.path("devices")) {
            saveDevice(tenantId, device);
        }
        for (JsonNode signing : root.path("crossSigning")) {
            saveCrossSigning(tenantId, signing);
        }
        for (JsonNode backup : root.path("backups")) {
            saveBackup(tenantId, backup);
        }
        JsonNode accountData = root.path("accountData");
        if (accountData.isObject()) {
            accountData.properties().forEach(user -> user.getValue().properties().forEach(event -> jdbc.update(
                    """
                    insert into weave_matrix_account_data(tenant_id,user_id,event_type,content_json,changed_revision)
                    values (?,?,?,?,?)
                    on conflict (tenant_id,user_id,event_type) do update
                    set content_json=excluded.content_json, changed_revision=excluded.changed_revision
                    """,
                    tenantId,
                    user.getKey(),
                    event.getKey(),
                    writeJson(event.getValue()),
                    sequence)));
        }
        for (JsonNode binding : root.path("oidcSessionBindings")) {
            jdbc.update(
                    """
                    insert into weave_matrix_oidc_device_bindings(tenant_id,user_id,oidc_session_hash,device_id)
                    values (?,?,?,?)
                    on conflict (tenant_id,user_id,oidc_session_hash) do update set device_id=excluded.device_id
                    """,
                    tenantId,
                    binding.path("userId").asText(),
                    binding.path("sessionHash").asText(),
                    binding.path("deviceId").asText());
        }
    }

    /** Atomic, cross-instance one-time-key claim. */
    @Transactional
    public Optional<ClaimedKey> claimOneTimeKey(
            String tenantId, String userId, String deviceId, String algorithm) {
        List<ClaimedKey> claimed = jdbc.query(
                """
                delete from weave_matrix_one_time_keys
                where (tenant_id,user_id,device_id,key_id) = (
                    select tenant_id,user_id,device_id,key_id
                    from weave_matrix_one_time_keys
                    where tenant_id=? and user_id=? and device_id=? and algorithm=? and claimed_at_utc is null
                    order by key_id
                    for update skip locked
                    limit 1
                )
                returning key_id,key_json
                """,
                (rs, ignored) -> new ClaimedKey(rs.getString("key_id"), readValue(rs.getString("key_json")), false),
                tenantId, userId, deviceId, algorithm);
        if (!claimed.isEmpty()) {
            return Optional.of(claimed.getFirst());
        }
        List<ClaimedKey> fallback = jdbc.query(
                """
                update weave_matrix_fallback_keys
                set used=true, updated_at_utc=now()
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
                (rs, ignored) -> new ClaimedKey(rs.getString("key_id"), readValue(rs.getString("key_json")), true),
                tenantId, userId, deviceId, algorithm);
        return fallback.stream().findFirst();
    }

    @Transactional
    public boolean appendToDevice(
            long sequence,
            String tenantId,
            String targetUserId,
            String targetDeviceId,
            String senderUserId,
            String eventType,
            String transactionId,
            Map<String, Object> content) {
        int inserted = jdbc.update(
                """
                insert into weave_matrix_to_device_messages(
                    sequence_id,tenant_id,target_user_id,target_device_id,sender_user_id,event_type,transaction_id,content_json)
                values (?,?,?,?,?,?,?,?)
                on conflict (tenant_id,sender_user_id,transaction_id,target_user_id,target_device_id,event_type) do nothing
                """,
                sequence,
                tenantId,
                targetUserId,
                targetDeviceId,
                senderUserId,
                eventType,
                transactionId,
                writeJson(content));
        upsertHead(tenantId, sequence);
        return inserted == 1;
    }

    @Transactional(readOnly = true)
    public List<RelationalToDeviceEvent> toDeviceEvents(
            String tenantId, String userId, String deviceId, long afterSequence, long highWater) {
        return jdbc.query(
                """
                select sequence_id,sender_user_id,event_type,content_json
                from weave_matrix_to_device_messages
                where tenant_id=? and target_user_id=? and target_device_id=?
                  and sequence_id>? and sequence_id<=?
                order by sequence_id
                """,
                (rs, ignored) -> new RelationalToDeviceEvent(
                        rs.getLong("sequence_id"),
                        rs.getString("sender_user_id"),
                        rs.getString("event_type"),
                        readObject(rs.getString("content_json"))),
                tenantId, userId, deviceId, afterSequence, highWater);
    }

    @Transactional
    public void recordDeviceSyncProgress(String tenantId, String userId, String deviceId, long sequence) {
        jdbc.update(
                """
                insert into weave_matrix_device_sync_progress(
                    tenant_id,user_id,device_id,to_device_sequence,device_list_revision,row_version,updated_at_utc)
                values (?,?,?,?,0,0,now())
                on conflict (tenant_id,user_id,device_id) do update
                set to_device_sequence=greatest(weave_matrix_device_sync_progress.to_device_sequence,excluded.to_device_sequence),
                    row_version=weave_matrix_device_sync_progress.row_version+1,
                    updated_at_utc=now()
                """,
                tenantId, userId, deviceId, sequence);
    }

    @Transactional
    public boolean bindOidcSession(String tenantId, String userId, String sessionHash, String deviceId) {
        List<String> existing = jdbc.query(
                """
                select device_id from weave_matrix_oidc_device_bindings
                where tenant_id=? and user_id=? and oidc_session_hash=?
                for update
                """,
                (rs, ignored) -> rs.getString(1), tenantId, userId, sessionHash);
        if (!existing.isEmpty()) {
            return existing.getFirst().equals(deviceId);
        }
        jdbc.update(
                "insert into weave_matrix_oidc_device_bindings(tenant_id,user_id,oidc_session_hash,device_id) values (?,?,?,?)",
                tenantId, userId, sessionHash, deviceId);
        return true;
    }

    private Map<String, Object> deviceProjection(String tenantId, ResultSet rs) throws SQLException {
        String userId = rs.getString("user_id");
        String deviceId = rs.getString("device_id");
        Map<String, Object> oneTimeKeys = new LinkedHashMap<>();
        jdbc.query(
                "select key_id,key_json from weave_matrix_one_time_keys where tenant_id=? and user_id=? and device_id=? order by key_id",
                row -> oneTimeKeys.put(row.getString(1), readValue(row.getString(2))),
                tenantId, userId, deviceId);
        Map<String, Object> fallbackKeys = new LinkedHashMap<>();
        Set<String> used = jdbc.query(
                "select key_id,algorithm,key_json,used from weave_matrix_fallback_keys where tenant_id=? and user_id=? and device_id=? order by key_id",
                (row, ignored) -> {
                    fallbackKeys.put(row.getString("key_id"), readValue(row.getString("key_json")));
                    return row.getBoolean("used") ? row.getString("algorithm") : null;
                }, tenantId, userId, deviceId).stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("deviceId", deviceId);
        result.put("deviceKeys", readObject(rs.getString("device_keys_json")));
        result.put("oneTimeKeys", oneTimeKeys);
        result.put("fallbackKeys", fallbackKeys);
        result.put("usedFallbackAlgorithms", used);
        result.put("changedSequence", rs.getLong("changed_revision"));
        result.put("revoked", rs.getBoolean("revoked"));
        return result;
    }

    private List<Map<String, Object>> groupCrossSigning(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String user = String.valueOf(row.get("userId"));
            Map<String, Object> target = grouped.computeIfAbsent(user, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("userId", user);
                value.put("masterKey", Map.of());
                value.put("selfSigningKey", Map.of());
                value.put("userSigningKey", Map.of());
                return value;
            });
            String field = switch (String.valueOf(row.get("usage"))) {
                case "master" -> "masterKey";
                case "self_signing" -> "selfSigningKey";
                case "user_signing" -> "userSigningKey";
                default -> throw new IllegalStateException("invalid persisted Matrix cross-signing usage");
            };
            target.put(field, row.get("key"));
        }
        return List.copyOf(grouped.values());
    }

    private List<Map<String, Object>> loadBackups(String tenantId) {
        return jdbc.query(
                """
                select user_id,version_id,algorithm,auth_data_json,current_version,revision
                from weave_matrix_key_backup_versions where tenant_id=? order by user_id,version_id
                """,
                (rs, ignored) -> {
                    String user = rs.getString("user_id");
                    long version = rs.getLong("version_id");
                    List<Map<String, Object>> sessions = jdbc.query(
                            """
                            select room_id,session_id,payload_json from weave_matrix_key_backup_sessions
                            where tenant_id=? and user_id=? and version_id=? order by room_id,session_id
                            """,
                            (session, unused) -> Map.<String, Object>of(
                                    "roomId", session.getString("room_id"),
                                    "sessionId", session.getString("session_id"),
                                    "payload", readObject(session.getString("payload_json"))),
                            tenantId, user, version);
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("userId", user);
                    value.put("version", Long.toString(version));
                    value.put("algorithm", rs.getString("algorithm"));
                    value.put("authData", readObject(rs.getString("auth_data_json")));
                    value.put("current", rs.getBoolean("current_version"));
                    value.put("revision", rs.getLong("revision"));
                    value.put("sessions", sessions);
                    return value;
                }, tenantId);
    }

    private Map<String, Map<String, Map<String, Object>>> loadAccountData(String tenantId) {
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();
        jdbc.query(
                "select user_id,event_type,content_json from weave_matrix_account_data where tenant_id=? order by user_id,event_type",
                rs -> result.computeIfAbsent(rs.getString("user_id"), ignored -> new LinkedHashMap<>())
                        .put(rs.getString("event_type"), readObject(rs.getString("content_json"))),
                tenantId);
        return result;
    }

    private void saveDevice(String tenantId, JsonNode device) {
        String user = device.path("userId").asText();
        String id = device.path("deviceId").asText();
        jdbc.update(
                """
                insert into weave_matrix_devices(tenant_id,user_id,device_id,device_keys_json,changed_revision,revoked,row_version,updated_at_utc)
                values (?,?,?,?,?,?,0,now())
                on conflict (tenant_id,user_id,device_id) do update
                set device_keys_json=excluded.device_keys_json, changed_revision=excluded.changed_revision,
                    revoked=excluded.revoked,row_version=weave_matrix_devices.row_version+1,updated_at_utc=now()
                """,
                tenantId, user, id, writeJson(device.path("deviceKeys")),
                device.path("changedSequence").asLong(), device.path("revoked").asBoolean(false));
        jdbc.update("delete from weave_matrix_one_time_keys where tenant_id=? and user_id=? and device_id=?", tenantId, user, id);
        device.path("oneTimeKeys").properties().forEach(key -> jdbc.update(
                "insert into weave_matrix_one_time_keys(tenant_id,user_id,device_id,key_id,algorithm,key_json) values (?,?,?,?,?,?)",
                tenantId, user, id, key.getKey(), algorithm(key.getKey()), writeJson(key.getValue())));
        jdbc.update("delete from weave_matrix_fallback_keys where tenant_id=? and user_id=? and device_id=?", tenantId, user, id);
        Set<String> used = new java.util.HashSet<>();
        device.path("usedFallbackAlgorithms").forEach(value -> used.add(value.asText()));
        device.path("fallbackKeys").properties().forEach(key -> jdbc.update(
                "insert into weave_matrix_fallback_keys(tenant_id,user_id,device_id,key_id,algorithm,key_json,used) values (?,?,?,?,?,?,?)",
                tenantId, user, id, key.getKey(), algorithm(key.getKey()), writeJson(key.getValue()), used.contains(algorithm(key.getKey()))));
    }

    private void saveCrossSigning(String tenantId, JsonNode signing) {
        String user = signing.path("userId").asText();
        saveSigningKey(tenantId, user, "master", signing.path("masterKey"));
        saveSigningKey(tenantId, user, "self_signing", signing.path("selfSigningKey"));
        saveSigningKey(tenantId, user, "user_signing", signing.path("userSigningKey"));
    }

    private void saveSigningKey(String tenantId, String user, String usage, JsonNode key) {
        if (!key.isObject() || key.isEmpty()) {
            jdbc.update("delete from weave_matrix_cross_signing_keys where tenant_id=? and user_id=? and usage=?", tenantId, user, usage);
            return;
        }
        String keyId = key.path("keys").properties().hasNext()
                ? key.path("keys").properties().next().getKey()
                : usage;
        jdbc.update(
                """
                insert into weave_matrix_cross_signing_keys(tenant_id,user_id,usage,key_id,key_json,changed_revision)
                values (?,?,?,?,?,0)
                on conflict (tenant_id,user_id,usage) do update set key_id=excluded.key_id,key_json=excluded.key_json
                """,
                tenantId, user, usage, keyId, writeJson(key));
    }

    private void saveBackup(String tenantId, JsonNode backup) {
        String user = backup.path("userId").asText();
        long version = Long.parseLong(backup.path("version").asText());
        if (backup.path("current").asBoolean(false)) {
            jdbc.update("update weave_matrix_key_backup_versions set current_version=false where tenant_id=? and user_id=?", tenantId, user);
        }
        jdbc.update(
                """
                insert into weave_matrix_key_backup_versions(tenant_id,user_id,version_id,algorithm,auth_data_json,current_version,revision)
                values (?,?,?,?,?,?,?)
                on conflict (tenant_id,user_id,version_id) do update
                set algorithm=excluded.algorithm,auth_data_json=excluded.auth_data_json,current_version=excluded.current_version,revision=excluded.revision
                """,
                tenantId, user, version, backup.path("algorithm").asText(), writeJson(backup.path("authData")),
                backup.path("current").asBoolean(false), backup.path("revision").asLong(1));
        jdbc.update("delete from weave_matrix_key_backup_sessions where tenant_id=? and user_id=? and version_id=?", tenantId, user, version);
        for (JsonNode session : backup.path("sessions")) {
            jdbc.update(
                    "insert into weave_matrix_key_backup_sessions(tenant_id,user_id,version_id,room_id,session_id,payload_json) values (?,?,?,?,?,?)",
                    tenantId, user, version, session.path("roomId").asText(), session.path("sessionId").asText(), writeJson(session.path("payload")));
        }
    }

    private long currentSequence(String tenantId) {
        Long head = jdbc.query(
                "select revision from weave_chat_sync_heads where tenant_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L,
                tenantId);
        Long toDevice = jdbc.query(
                "select coalesce(max(sequence_id),0) from weave_matrix_to_device_messages where tenant_id=?",
                rs -> rs.next() ? rs.getLong(1) : 0L,
                tenantId);
        return Math.max(head == null ? 0 : head, toDevice == null ? 0 : toDevice);
    }

    private void upsertHead(String tenantId, long sequence) {
        jdbc.update(
                """
                insert into weave_chat_sync_heads(tenant_id,revision,row_version,updated_at_utc)
                values (?, ?, 0, now())
                on conflict (tenant_id) do update
                set revision=greatest(weave_chat_sync_heads.revision,excluded.revision),
                    row_version=weave_chat_sync_heads.row_version+1,updated_at_utc=now()
                """,
                tenantId, sequence);
    }

    private String algorithm(String keyId) {
        int separator = keyId.indexOf(':');
        return separator > 0 ? keyId.substring(0, separator) : keyId;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Matrix relational persistence JSON is invalid", exception);
        }
    }

    private Map<String, Object> readObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = objectMapper.readValue(json, Map.class);
            return value == null ? Map.of() : Map.copyOf(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Matrix relational persistence object is invalid", exception);
        }
    }

    private Object readValue(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Matrix relational persistence value is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Matrix relational persistence value could not be serialized", exception);
        }
    }

    public record SnapshotDocument(long sequence, String payloadJson) {}
    public record ClaimedKey(String keyId, Object value, boolean fallback) {}
    public record RelationalToDeviceEvent(long sequence, String senderUserId, String eventType, Map<String, Object> content) {}
}
