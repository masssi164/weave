package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatIdentityRef;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
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
import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class MatrixFacadeClientStateService {

    public static final String DEVICE_ID_HEADER = "X-Weave-Matrix-Device-Id";
    private static final int MAX_FILTERS_PER_USER = 50;

    private final MatrixProtocolCoreService matrixProtocolCoreService;
    private final ConcurrentMap<ScopedMatrixUser, ChatResolvedIdentity> actorsByMatrixUserId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Map<String, Object>>> filtersByUser =
            new ConcurrentHashMap<>();
    private final java.util.Set<String> revokedTokenHashes = ConcurrentHashMap.newKeySet();
    private final MatrixFacadeClientStateStore stateStore;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final OrganizationIdentityContextResolver identityContextResolver;
    private final AtomicLong filterSequence = new AtomicLong();

    public MatrixFacadeClientStateService(
            MatrixProtocolCoreService matrixProtocolCoreService,
            MatrixFacadeClientStateStore stateStore,
            ContextAuthorizationProperties contextAuthorizationProperties,
            OrganizationIdentityContextResolver identityContextResolver) {
        this.matrixProtocolCoreService = java.util.Objects.requireNonNull(
                matrixProtocolCoreService, "matrixProtocolCoreService");
        this.stateStore = java.util.Objects.requireNonNull(stateStore, "stateStore");
        this.contextAuthorizationProperties = java.util.Objects.requireNonNull(
                contextAuthorizationProperties, "contextAuthorizationProperties");
        this.identityContextResolver = java.util.Objects.requireNonNull(
                identityContextResolver, "identityContextResolver");
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
        OrganizationIdentityContext identityContext = requireIdentityContext(jwt);
        String tenantId = identityContext.organizationId();
        String identityIssuer = identityContext.issuer();
        ChatActorRef actorRef = new ChatActorRef("user:" + identityContext.subject());
        String policyClaim = jwt.getClaimAsString(contextAuthorizationProperties.principalClaim());
        String authorizationPrincipalRef = contextAuthorizationProperties.principalRef(policyClaim);
        if (authorizationPrincipalRef == null) {
            throw new MatrixProtocolException("M_FORBIDDEN", "The Context authorization identity is missing.");
        }
        ChatResolvedIdentity resolvedIdentity = new ChatResolvedIdentity(
                new ChatIdentityRef(tenantId, identityIssuer, actorRef), authorizationPrincipalRef);
        ScopedMatrixUser scopedUser = new ScopedMatrixUser(tenantId, identityIssuer, userId);
        actorsByMatrixUserId.put(scopedUser, resolvedIdentity);
        persistIdentityProjection(scopedUser, resolvedIdentity);
        return new MatrixIdentity(
                userId,
                actorRef,
                deviceId,
                tenantId,
                identityIssuer,
                oidcSessionHash(jwt));
    }

    public Optional<ChatResolvedIdentity> identityForMatrixUserId(
            String userId,
            String tenantId,
            String identityIssuer) {
        if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()
                || identityIssuer == null || identityIssuer.isBlank()) {
            return Optional.empty();
        }
        ScopedMatrixUser scopedUser = new ScopedMatrixUser(tenantId, identityIssuer, userId);
        ChatResolvedIdentity cached = actorsByMatrixUserId.get(scopedUser);
        if (cached != null) {
            return Optional.ofNullable(cached);
        }
        Optional<ChatResolvedIdentity> projected = stateStore
                .identityProjection(tenantId, identityIssuer, userId)
                .map(projection -> new ChatResolvedIdentity(
                        new ChatIdentityRef(
                                tenantId,
                                identityIssuer,
                                new ChatActorRef(projection.actorRef())),
                        projection.authorizationPrincipalRef()));
        projected.ifPresent(identity -> actorsByMatrixUserId.putIfAbsent(scopedUser, identity));
        return projected;
    }

    private void persistIdentityProjection(
            ScopedMatrixUser scopedUser,
            ChatResolvedIdentity identity) {
        stateStore.saveIdentityProjection(new MatrixFacadeClientStateStore.IdentityProjection(
                scopedUser.tenantId(),
                scopedUser.identityIssuer(),
                scopedUser.userId(),
                identity.identity().actorRef().value(),
                identity.authorizationPrincipalRef(),
                Instant.now()));
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
            String hash = tokenHash(tokenIdentity);
            revokedTokenHashes.add(hash);
            Instant now = Instant.now();
            Instant expires = jwt.getExpiresAt() == null
                    ? now.plusSeconds(86_400)
                    : jwt.getExpiresAt();
            stateStore.revokeSession(hash, now, expires);
        }
    }

    public boolean revoked(Jwt jwt) {
        String tokenIdentity = tokenIdentity(jwt);
        if (tokenIdentity == null) {
            return false;
        }
        String hash = tokenHash(tokenIdentity);
        if (revokedTokenHashes.contains(hash)) {
            return true;
        }
        Instant now = Instant.now();
        stateStore.deleteExpiredSessions(now);
        return stateStore.isSessionRevoked(hash, now);
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
        OrganizationIdentityContext identityContext;
        try {
            identityContext = identityContextResolver.resolve(jwt);
        } catch (ApiErrorException invalidIdentity) {
            return null;
        }
        String tenant = identityContext.organizationId();
        String issuer = identityContext.issuer();
        String subject = identityContext.subject();
        String tokenId = jwt.getClaimAsString("jti");
        if (tokenId != null && !tokenId.isBlank()) {
            return tenant + "\u0000" + issuer + "\u0000" + subject + "\u0000jti:" + tokenId;
        }
        String session = jwt.getClaimAsString("sid");
        if (session == null || session.isBlank()) {
            session = jwt.getClaimAsString("session_state");
        }
        return tenant + "\u0000" + issuer + "\u0000" + subject
                + "\u0000session:" + session
                + "\u0000issued:" + jwt.getIssuedAt() + "\u0000expires:" + jwt.getExpiresAt();
    }

    private OrganizationIdentityContext requireIdentityContext(Jwt jwt) {
        try {
            return identityContextResolver.resolve(jwt);
        } catch (ApiErrorException invalidIdentity) {
            throw new MatrixProtocolException(
                    "M_FORBIDDEN",
                    "The OIDC identity is not valid for the Matrix projection.");
        }
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

    public record MatrixIdentity(
            String userId,
            ChatActorRef actorRef,
            String deviceId,
            String tenantId,
            String identityIssuer,
            String oidcSessionHash) {

        public MatrixIdentity(
                String userId,
                ChatActorRef actorRef,
                String deviceId,
                String tenantId,
                String identityIssuer) {
            this(userId, actorRef, deviceId, tenantId, identityIssuer, null);
        }
    }

    private record ScopedMatrixUser(String tenantId, String identityIssuer, String userId) {
    }
}
