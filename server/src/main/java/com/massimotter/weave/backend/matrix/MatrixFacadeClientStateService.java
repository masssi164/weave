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

    private static final int MAX_FILTERS_PER_USER = 50;

    private final MatrixProtocolCoreService matrixProtocolCoreService;
    private final ConcurrentMap<String, ChatActorRef> actorsByMatrixUserId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Map<String, Object>>> filtersByUser =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Object>> accountDataByUser =
            new ConcurrentHashMap<>();
    private final java.util.Set<String> revokedTokenHashes = ConcurrentHashMap.newKeySet();
    private final AtomicLong filterSequence = new AtomicLong();

    public MatrixFacadeClientStateService(MatrixProtocolCoreService matrixProtocolCoreService) {
        this.matrixProtocolCoreService = matrixProtocolCoreService;
    }

    public MatrixIdentity register(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new MatrixProtocolException("M_MISSING_TOKEN", "A Matrix bearer token is required.");
        }
        Object rawUserId = matrixProtocolCoreService.whoami(jwt.getSubject()).get("user_id");
        if (!(rawUserId instanceof String userId) || userId.isBlank()) {
            throw new MatrixProtocolException("M_WEAVE_MATRIX_CORE_ERROR", "Matrix identity could not be projected.");
        }
        ChatActorRef actorRef = new ChatActorRef("user:" + jwt.getSubject());
        actorsByMatrixUserId.put(userId, actorRef);
        return new MatrixIdentity(userId, actorRef);
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

    public void putAccountData(String userId, String eventType, Object content) {
        accountDataByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(eventType, content == null ? Map.of() : content);
    }

    public Map<String, Object> accountData(String userId) {
        return Map.copyOf(accountDataByUser.getOrDefault(userId, new ConcurrentHashMap<>()));
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

    public record MatrixIdentity(String userId, ChatActorRef actorRef) {
    }
}
