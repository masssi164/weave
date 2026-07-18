package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatIdentityRef;
import com.massimotter.weave.backend.chat.domain.ChatResolvedIdentity;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final AtomicLong filterSequence = new AtomicLong();

    public MatrixFacadeClientStateService(MatrixProtocolCoreService matrixProtocolCoreService) {
        this(matrixProtocolCoreService, (JdbcTemplate) null, defaultContextAuthorizationProperties());
    }

    @Autowired
    public MatrixFacadeClientStateService(
            MatrixProtocolCoreService matrixProtocolCoreService,
            ObjectProvider<JdbcTemplate> jdbcProvider,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        this(matrixProtocolCoreService, jdbcProvider.getIfAvailable(), contextAuthorizationProperties);
    }

    MatrixFacadeClientStateService(
            MatrixProtocolCoreService matrixProtocolCoreService,
            JdbcTemplate jdbc,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        this.matrixProtocolCoreService = matrixProtocolCoreService;
        this.jdbc = jdbc;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
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
        String tenantId = tenantId(jwt);
        String identityIssuer = identityIssuer(jwt);
        ChatActorRef actorRef = new ChatActorRef("user:" + jwt.getSubject());
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
        if (cached != null || jdbc == null) {
            return Optional.ofNullable(cached);
        }
        Optional<ChatResolvedIdentity> projected = jdbc.query(
                "select actor_ref, authorization_principal_ref from weave_matrix_identity_projection "
                        + "where tenant_id = ? and identity_issuer = ? and matrix_user_id = ?",
                (rs, row) -> new ChatResolvedIdentity(
                        new ChatIdentityRef(
                                tenantId,
                                identityIssuer,
                                new ChatActorRef(rs.getString("actor_ref"))),
                        rs.getString("authorization_principal_ref")),
                tenantId,
                identityIssuer,
                userId).stream().findFirst();
        projected.ifPresent(identity -> actorsByMatrixUserId.putIfAbsent(scopedUser, identity));
        return projected;
    }

    private void persistIdentityProjection(
            ScopedMatrixUser scopedUser,
            ChatResolvedIdentity identity) {
        if (jdbc == null) {
            return;
        }
        Instant now = Instant.now();
        int updated = jdbc.update("update weave_matrix_identity_projection set actor_ref = ?, "
                        + "authorization_principal_ref = ?, updated_at_utc = ? "
                        + "where tenant_id = ? and identity_issuer = ? and matrix_user_id = ?",
                identity.identity().actorRef().value(),
                identity.authorizationPrincipalRef(),
                utc(now),
                scopedUser.tenantId(),
                scopedUser.identityIssuer(),
                scopedUser.userId());
        if (updated == 0) {
            jdbc.update("insert into weave_matrix_identity_projection "
                            + "(tenant_id, identity_issuer, matrix_user_id, actor_ref, "
                            + "authorization_principal_ref, updated_at_utc) values (?, ?, ?, ?, ?, ?) "
                            + "on conflict do nothing",
                    scopedUser.tenantId(),
                    scopedUser.identityIssuer(),
                    scopedUser.userId(),
                    identity.identity().actorRef().value(),
                    identity.authorizationPrincipalRef(),
                    utc(now));
        }
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
            if (jdbc == null) {
                revokedTokenHashes.add(hash);
                return;
            }
            Instant now = Instant.now();
            Instant expires = jwt.getExpiresAt() == null
                    ? now.plusSeconds(86_400)
                    : jwt.getExpiresAt();
            int updated = jdbc.update("update weave_matrix_revoked_sessions set revoked_at_utc = ?, "
                            + "expires_at_utc = ? where session_hash = ?",
                    utc(now), utc(expires), hash);
            if (updated == 0) {
                jdbc.update("insert into weave_matrix_revoked_sessions "
                                + "(session_hash, revoked_at_utc, expires_at_utc) values (?, ?, ?) "
                                + "on conflict do nothing",
                        hash, utc(now), utc(expires));
            }
        }
    }

    public boolean revoked(Jwt jwt) {
        String tokenIdentity = tokenIdentity(jwt);
        if (tokenIdentity == null) {
            return false;
        }
        String hash = tokenHash(tokenIdentity);
        if (jdbc == null) {
            return revokedTokenHashes.contains(hash);
        }
        Instant now = Instant.now();
        jdbc.update("delete from weave_matrix_revoked_sessions where expires_at_utc <= ?", utc(now));
        Long count = jdbc.queryForObject("select count(*) from weave_matrix_revoked_sessions "
                        + "where session_hash = ? and expires_at_utc > ?",
                Long.class, hash, utc(now));
        return count != null && count > 0;
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
        String tenant = jwt.getClaimAsString("weave_tenant_id");
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (tenant == null || tenant.isBlank() || issuer == null || issuer.isBlank()
                || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return null;
        }
        String tokenId = jwt.getClaimAsString("jti");
        if (tokenId != null && !tokenId.isBlank()) {
            return tenant + "\u0000" + issuer + "\u0000" + jwt.getSubject() + "\u0000jti:" + tokenId;
        }
        String session = jwt.getClaimAsString("sid");
        if (session == null || session.isBlank()) {
            session = jwt.getClaimAsString("session_state");
        }
        return tenant + "\u0000" + issuer + "\u0000" + jwt.getSubject()
                + "\u0000session:" + session
                + "\u0000issued:" + jwt.getIssuedAt() + "\u0000expires:" + jwt.getExpiresAt();
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
        String value = jwt.getClaimAsString("weave_tenant_id");
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new MatrixProtocolException(
                "M_FORBIDDEN",
                "The authoritative Weave tenant identity is missing.");
    }

    private String identityIssuer(Jwt jwt) {
        if (jwt.getIssuer() == null || jwt.getIssuer().toString().isBlank()) {
            throw new MatrixProtocolException("M_FORBIDDEN", "The OIDC identity issuer is missing.");
        }
        return jwt.getIssuer().toString();
    }

    private OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static ContextAuthorizationProperties defaultContextAuthorizationProperties() {
        return new ContextAuthorizationProperties(
                "weave_tenant_id", "tenant_id", "tenant-default", "sub", "user:",
                List.of(), List.of(), List.of());
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
