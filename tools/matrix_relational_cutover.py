#!/usr/bin/env python3
from pathlib import Path

# First normalize the small Spring/Jackson/Jdbc API differences discovered by
# the compile-only cutover gate. This keeps the actual service transformation
# deterministic and repeatable.
store = Path('server/src/main/java/com/massimotter/weave/backend/matrix/MatrixE2eeRelationalStore.java')
store_text = store.read_text()
store_text = store_text.replace(
'''        jdbc.query(
                "select key_id,key_json from weave_matrix_one_time_keys where tenant_id=? and user_id=? and device_id=? order by key_id",
                row -> oneTimeKeys.put(row.getString(1), readValue(row.getString(2))),
                tenantId, userId, deviceId);''',
'''        jdbc.query(
                "select key_id,key_json from weave_matrix_one_time_keys where tenant_id=? and user_id=? and device_id=? order by key_id",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        row -> oneTimeKeys.put(row.getString(1), readValue(row.getString(2))),
                tenantId, userId, deviceId);''')
store_text = store_text.replace(
'''        jdbc.query(
                "select user_id,event_type,content_json from weave_matrix_account_data where tenant_id=? order by user_id,event_type",
                rs -> result.computeIfAbsent(rs.getString("user_id"), ignored -> new LinkedHashMap<>())
                        .put(rs.getString("event_type"), readObject(rs.getString("content_json"))),
                tenantId);''',
'''        jdbc.query(
                "select user_id,event_type,content_json from weave_matrix_account_data where tenant_id=? order by user_id,event_type",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> result.computeIfAbsent(rs.getString("user_id"), ignored -> new LinkedHashMap<>())
                                .put(rs.getString("event_type"), readObject(rs.getString("content_json"))),
                tenantId);''')
store_text = store_text.replace(
'''        String keyId = key.path("keys").properties().hasNext()
                ? key.path("keys").properties().next().getKey()
                : usage;''',
'''        var keyProperties = key.path("keys").properties();
        String keyId = keyProperties.isEmpty()
                ? usage
                : keyProperties.iterator().next().getKey();''')
store.write_text(store_text)

p = Path('server/src/main/java/com/massimotter/weave/backend/matrix/MatrixE2eeStateService.java')
s = p.read_text()

def replace_once(old: str, new: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one match, got {count}: {old[:120]!r}')
    s = s.replace(old, new, 1)

replace_once('import org.springframework.beans.factory.ObjectProvider;\n', '')
replace_once('    private final Set<String> loadedTenants = ConcurrentHashMap.newKeySet();\n', '')
replace_once('    private final MatrixE2eeSnapshotStore snapshotStore;\n', '    private final MatrixE2eeRelationalStore relationalStore;\n')
replace_once('''    public MatrixE2eeStateService(
            ObjectMapper objectMapper,
            ObjectProvider<MatrixE2eeSnapshotStore> snapshotStoreProvider) {
        this.objectMapper = objectMapper;
        this.snapshotStore = snapshotStoreProvider.getIfAvailable();
    }
''', '''    public MatrixE2eeStateService(
            ObjectMapper objectMapper,
            MatrixE2eeRelationalStore relationalStore) {
        this.objectMapper = objectMapper;
        this.relationalStore = relationalStore;
    }
''')
replace_once('''                Map<String, Object> claimed = claimOneTimeKey(state, algorithm);
''', '''                Map<String, Object> claimed = claimOneTimeKey(
                        identity.tenantId(), userEntry.getKey(), deviceEntry.getKey(), state, algorithm);
''')
replace_once('''        persist(identity.tenantId());
        return Map.of("one_time_keys", Map.copyOf(claimedUsers), "failures", Map.of());
''', '''        return Map.of("one_time_keys", Map.copyOf(claimedUsers), "failures", Map.of());
''')
replace_once('''                (event, value) -> toDeviceEvents.add(new ToDeviceEvent(
                        value,
                        identity.tenantId(),
                        event.targetUserId(),
                        event.targetDeviceId(),
                        identity.userId(),
                        eventType,
                        event.content())));
''', '''                (event, value) -> {
                    ToDeviceEvent publishedEvent = new ToDeviceEvent(
                            value,
                            identity.tenantId(),
                            event.targetUserId(),
                            event.targetDeviceId(),
                            identity.userId(),
                            eventType,
                            event.content());
                    if (relationalStore.appendToDevice(
                            value,
                            identity.tenantId(),
                            event.targetUserId(),
                            event.targetDeviceId(),
                            identity.userId(),
                            eventType,
                            transactionId,
                            event.content())) {
                        toDeviceEvents.add(publishedEvent);
                    }
                });
''')
replace_once('''        if (!published) {
            return;
        }
        persist(identity.tenantId());
''', '''        if (!published) {
            return;
        }
''')
replace_once('''        return snapshot.value();
    }

    /**
     * Returns aggregate, isolated-E2E-only evidence''', '''        MatrixProtocolCoreService.MatrixSyncCrypto result = snapshot.value();
        relationalStore.recordDeviceSyncProgress(
                identity.tenantId(), identity.userId(), identity.deviceId(), result.nextSequence());
        return result;
    }

    /**
     * Returns aggregate, isolated-E2E-only evidence''')

start = s.index('    private synchronized void prepare(MatrixFacadeClientStateService.MatrixIdentity identity) {')
end_marker = '    private PersistedSnapshot snapshot(String tenantId) {'
end = s.index(end_marker, start)
s = s[:start] + '''    private synchronized void prepare(MatrixFacadeClientStateService.MatrixIdentity identity) {
        String tenantId = identity.tenantId();
        clearTenantProjection(tenantId);
        try {
            relationalStore.load(tenantId).ifPresent(document -> restoreSnapshot(
                    tenantId,
                    document.sequence(),
                    document.payloadJson()));
        } catch (RuntimeException exception) {
            throw new MatrixProtocolException("M_UNAVAILABLE", "Matrix E2EE state could not be restored.");
        }
    }

    private synchronized void persist(String tenantId) {
        try {
            MatrixE2eeSequenceJournal.Snapshot<PersistedSnapshot> captured =
                    sequenceJournal.snapshot(ignored -> snapshot(tenantId));
            relationalStore.save(
                    tenantId,
                    captured.highWater(),
                    objectMapper.writeValueAsString(captured.value()));
        } catch (RuntimeException exception) {
            throw new MatrixProtocolException("M_UNAVAILABLE", "Matrix E2EE state could not be persisted.");
        }
    }

    private void clearTenantProjection(String tenantId) {
        devices.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        crossSigning.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        toDeviceEvents.removeIf(event -> event.tenantId().equals(tenantId));
        toDeviceTransactions.removeIf(key -> key.tenantId().equals(tenantId));
        backups.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        backupVersionSequences.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        accountData.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        devicesByOidcSession.keySet().removeIf(key -> key.tenantId().equals(tenantId));
        sharedUsersByDevice.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

''' + s[end:]

start = s.index('    private void bindOidcSession(MatrixFacadeClientStateService.MatrixIdentity identity) {')
end = s.index('    private BackupVersion requireBackup(', start)
s = s[:start] + '''    private void bindOidcSession(MatrixFacadeClientStateService.MatrixIdentity identity) {
        if (identity.oidcSessionHash() == null || identity.oidcSessionHash().isBlank()) {
            return;
        }
        if (!relationalStore.bindOidcSession(
                identity.tenantId(), identity.userId(), identity.oidcSessionHash(), identity.deviceId())) {
            throw new MatrixProtocolException(
                    "M_UNKNOWN_TOKEN",
                    "The OIDC session is bound to a different Matrix device.");
        }
        devicesByOidcSession.put(
                new OidcSessionKey(identity.tenantId(), identity.userId(), identity.oidcSessionHash()),
                identity.deviceId());
    }

''' + s[end:]

start = s.index('    private synchronized Map<String, Object> claimOneTimeKey(')
end = s.index('    private String algorithm(', start)
s = s[:start] + '''    private synchronized Map<String, Object> claimOneTimeKey(
            String tenantId,
            String userId,
            String deviceId,
            DeviceState state,
            String algorithm) {
        var claimed = relationalStore.claimOneTimeKey(tenantId, userId, deviceId, algorithm);
        if (claimed.isEmpty()) {
            return Map.of();
        }
        MatrixE2eeRelationalStore.ClaimedKey key = claimed.orElseThrow();
        if (key.fallback()) {
            state.usedFallbackAlgorithms.add(algorithm);
        } else {
            state.oneTimeKeys.remove(key.keyId());
        }
        return Map.of(key.keyId(), key.value());
    }

''' + s[end:]

p.write_text(s)
