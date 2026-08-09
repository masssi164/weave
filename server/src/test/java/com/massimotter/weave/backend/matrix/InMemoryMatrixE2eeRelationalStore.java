package com.massimotter.weave.backend.matrix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Test-only implementation of the normalized Matrix persistence boundary. */
final class InMemoryMatrixE2eeRelationalStore extends MatrixE2eeRelationalStore {

    private final ObjectMapper objectMapper;
    private final Map<String, SnapshotDocument> documents = new ConcurrentHashMap<>();

    InMemoryMatrixE2eeRelationalStore(ObjectMapper objectMapper) {
        super(new JdbcTemplate(), objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized Optional<SnapshotDocument> load(String tenantId) {
        return Optional.ofNullable(documents.get(tenantId));
    }

    @Override
    public synchronized void save(String tenantId, long sequence, String projectionJson) {
        documents.put(tenantId, new SnapshotDocument(sequence, projectionJson));
    }

    @Override
    public synchronized Optional<ClaimedKey> claimOneTimeKey(
            String tenantId, String userId, String deviceId, String algorithm) {
        Map<String, Object> projection = projection(tenantId);
        for (Map<String, Object> device : objectList(projection.get("devices"))) {
            if (!userId.equals(device.get("userId")) || !deviceId.equals(device.get("deviceId"))) {
                continue;
            }
            Map<String, Object> oneTimeKeys = mutableMap(device.get("oneTimeKeys"));
            String claimed = oneTimeKeys.keySet().stream()
                    .filter(key -> algorithm(key).equals(algorithm))
                    .sorted()
                    .findFirst()
                    .orElse(null);
            if (claimed != null) {
                Object value = oneTimeKeys.remove(claimed);
                device.put("oneTimeKeys", oneTimeKeys);
                persistProjection(tenantId, projection);
                return Optional.of(new ClaimedKey(claimed, value, false));
            }
            Map<String, Object> fallback = mutableMap(device.get("fallbackKeys"));
            String fallbackId = fallback.keySet().stream()
                    .filter(key -> algorithm(key).equals(algorithm))
                    .sorted()
                    .findFirst()
                    .orElse(null);
            if (fallbackId != null) {
                List<Object> used = new ArrayList<>(list(device.get("usedFallbackAlgorithms")));
                if (!used.contains(algorithm)) {
                    used.add(algorithm);
                }
                device.put("usedFallbackAlgorithms", used);
                persistProjection(tenantId, projection);
                return Optional.of(new ClaimedKey(fallbackId, fallback.get(fallbackId), true));
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized boolean appendToDevice(
            long sequence,
            String tenantId,
            String targetUserId,
            String targetDeviceId,
            String senderUserId,
            String eventType,
            String transactionId,
            Map<String, Object> content) {
        Map<String, Object> projection = projection(tenantId);
        List<Map<String, Object>> transactions = objectList(projection.get("toDeviceTransactions"));
        boolean duplicate = transactions.stream().anyMatch(tx ->
                tenantId.equals(tx.get("tenantId"))
                        && senderUserId.equals(tx.get("userId"))
                        && transactionId.equals(tx.get("transactionId")));
        if (duplicate) {
            return false;
        }
        transactions.add(new LinkedHashMap<>(Map.of(
                "tenantId", tenantId,
                "userId", senderUserId,
                "transactionId", transactionId)));
        projection.put("toDeviceTransactions", transactions);

        List<Map<String, Object>> events = objectList(projection.get("toDeviceEvents"));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sequence", sequence);
        event.put("tenantId", tenantId);
        event.put("targetUserId", targetUserId);
        event.put("targetDeviceId", targetDeviceId);
        event.put("senderUserId", senderUserId);
        event.put("eventType", eventType);
        event.put("content", content);
        events.add(event);
        projection.put("toDeviceEvents", events);
        persistProjection(tenantId, sequence, projection);
        return true;
    }

    @Override
    public synchronized void recordDeviceSyncProgress(
            String tenantId, String userId, String deviceId, long sequence) {
        // Progress is deliberately independent of the protocol projection in this test store.
    }

    @Override
    public synchronized boolean bindOidcSession(
            String tenantId, String userId, String sessionHash, String deviceId) {
        Map<String, Object> projection = projection(tenantId);
        List<Map<String, Object>> bindings = objectList(projection.get("oidcSessionBindings"));
        for (Map<String, Object> binding : bindings) {
            if (userId.equals(binding.get("userId")) && sessionHash.equals(binding.get("sessionHash"))) {
                return deviceId.equals(binding.get("deviceId"));
            }
        }
        bindings.add(new LinkedHashMap<>(Map.of(
                "userId", userId,
                "sessionHash", sessionHash,
                "deviceId", deviceId)));
        projection.put("oidcSessionBindings", bindings);
        persistProjection(tenantId, projection);
        return true;
    }

    private Map<String, Object> projection(String tenantId) {
        SnapshotDocument document = documents.get(tenantId);
        if (document == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("devices", new ArrayList<>());
            empty.put("crossSigning", new ArrayList<>());
            empty.put("toDeviceEvents", new ArrayList<>());
            empty.put("toDeviceTransactions", new ArrayList<>());
            empty.put("backups", new ArrayList<>());
            empty.put("backupVersionSequences", new LinkedHashMap<>());
            empty.put("accountData", new LinkedHashMap<>());
            empty.put("oidcSessionBindings", new ArrayList<>());
            return empty;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(document.payloadJson(), Map.class);
            return deepMutable(parsed);
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void persistProjection(String tenantId, Map<String, Object> projection) {
        long sequence = Optional.ofNullable(documents.get(tenantId)).map(SnapshotDocument::sequence).orElse(0L);
        persistProjection(tenantId, sequence, projection);
    }

    private void persistProjection(String tenantId, long sequence, Map<String, Object> projection) {
        try {
            documents.put(tenantId, new SnapshotDocument(sequence, objectMapper.writeValueAsString(projection)));
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?> values)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            result.add(new LinkedHashMap<>((Map<String, Object>) item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return value instanceof List<?> values ? new ArrayList<>((List<Object>) values) : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMutable(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> map) {
                result.put(key, deepMutable((Map<String, Object>) map));
            } else if (value instanceof List<?> values) {
                List<Object> copy = new ArrayList<>();
                for (Object item : values) {
                    copy.add(item instanceof Map<?, ?> map
                            ? deepMutable((Map<String, Object>) map)
                            : item);
                }
                result.put(key, copy);
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    private String algorithm(String keyId) {
        int separator = keyId.indexOf(':');
        return separator > 0 ? keyId.substring(0, separator) : keyId;
    }
}
