package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class MatrixFacadeClientStateService {

    public static final String DEVICE_ID_HEADER = "X-Weave-Matrix-Device-Id";
    private static final int MAX_FILTERS_PER_USER = 50;

    private final MatrixProtocolCoreService matrixProtocolCoreService;
    private final ConcurrentMap<String, ChatActorRef> actorsByMatrixUserId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Map<String, Object>>> filtersByUser =
            new ConcurrentHashMap<>();
    private final java.util.Set<String> revokedTokenHashes = ConcurrentHashMap.newKeySet();
    private final AtomicLong filterSequence = new AtomicLong();

    public MatrixFacadeClientStateService(MatrixProtocolCoreService matrixProtocolCoreService) {
        this.matrixProtocolCoreService = matrixProtocolCoreService;
    }

    public MatrixIdentity register(Jwt jwt, String requestedDeviceId) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new MatrixProtocolException("M_MISSING_TOKEN", "A Matrix bearer token is required.");
        }
        String deviceId = deviceId(jwt, requestedDeviceId);
        Object rawUserId = matrixProtocolCoreService.whoami(jwt.getSubject(), deviceId).get("user_id");
        if (!(rawUserId instanceof String userId) || userId.isBlank()) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix identity could not be projected.");
        }
        ChatActorRef actorRef = new ChatActorRef("user:" + jwt.getSubject());
        actorsByMatrixUserId.put(userId, actorRef);
        return new MatrixIdentity(userId, actorRef, deviceId, tenantId(jwt), oidcSessionHash(jwt));
    }

    public Optional<ChatActorRef> actorForMatrixUserId(String userId) {
        return Optional.ofNullable(actorsByMatrixUserId.get(userId));
    }

    public String createFilter(String userId, Map<String, Object> filter) {
        ConcurrentMap<String, Map<String, Object>> filters =
                filtersByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        if (filters.size() >= MAX_FILTERS_PER_USER) {
            throw new MatrixProtocolException("M_LIMIT_EXCEEDED", "The Matrix filter limit was reached.");
        }
        String filterId = "weave-filter-" + filterSequence.incrementAndGet();
        filters.put(filterId, immutableMap(filter));
        return filterId;
    }

    public Map<String, Object> filter(String userId, String filterId) {
        Map<String, Object> filter = filtersByUser.getOrDefault(userId, new ConcurrentHashMap<>()).get(filterId);
        if (filter == null) {
            throw new MatrixProtocolException("M_NOT_FOUND", "The Matrix filter was not found.");
        }
        return filter;
    }

    public Map<String, Object> pushRules() {
        Map<String, Object> emptyGlobal = Map.of(
                "content", List.of(),
                "override", List.of(),
                "room", List.of(),
                "sender", List.of(),
                "underride", List.of());
        return Map.of("global", emptyGlobal);
    }

    public void revoke(Jwt jwt) {
        String tokenIdentity = tokenIdentity(jwt);
        if (tokenIdentity != null) {
            revokedTokenHashes.add(tokenHash(tokenIdentity));
        }
    }

    public boolean revoked(Jwt jwt) {
        String tokenIdentity = tokenIdentity(jwt);
        return tokenIdentity != null && revokedTokenHashes.contains(tokenHash(tokenIdentity));
    }

    private Map<String, Object> immutableMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }

    private String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String tokenIdentity(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String tokenId = jwt.getClaimAsString("jti");
        if (tokenId != null && !tokenId.isBlank()) {
            return "jti:" + tokenId;
        }
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return null;
        }
        return "subject:" + jwt.getSubject()
                + ":issued:" + jwt.getIssuedAt()
                + ":expires:" + jwt.getExpiresAt();
    }

    private String oidcSessionHash(Jwt jwt) {
        for (String claim : List.of("sid", "session_state")) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return tokenHash(claim + ":" + value.trim());
            }
        }
        return null;
    }

    private String deviceId(Jwt jwt, String requestedDeviceId) {
        if (requestedDeviceId != null && !requestedDeviceId.isBlank()) {
            return requireDeviceId(requestedDeviceId.trim());
        }
        for (String claim : List.of("weave_matrix_device_id", "device_id", "sid")) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                String trimmed = value.trim();
                if (validDeviceId(trimmed)) {
                    return trimmed;
                }
                return "WEAVE" + tokenHash(claim + ":" + trimmed).substring(0, 36);
            }
        }
        throw new MatrixProtocolException(
                "M_INVALID_PARAM",
                "A stable Matrix device identity is required for this OIDC session.");
    }

    private String requireDeviceId(String value) {
        if (!validDeviceId(value)) {
            throw new MatrixProtocolException("M_INVALID_PARAM", "The Matrix device identity is invalid.");
        }
        return value;
    }

    private boolean validDeviceId(String value) {
        return value.matches("[A-Za-z0-9._=-]{8,128}");
    }

    private String tenantId(Jwt jwt) {
        for (String claim : List.of("weave_tenant_id", "tenant_id", "org_id")) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "tenant-default";
    }

    public record MatrixIdentity(
            String userId,
            ChatActorRef actorRef,
            String deviceId,
            String tenantId,
            String oidcSessionHash) {

        public MatrixIdentity(String userId, ChatActorRef actorRef, String deviceId, String tenantId) {
            this(userId, actorRef, deviceId, tenantId, null);
        }
    }
}
