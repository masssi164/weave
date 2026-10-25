package com.massimotter.weave.backend.security;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;

/** OIDC session revocation shared by the User API and optional domain facades. */
public class MemberSessionRevocationService {
    private final MemberSessionStateStore stateStore;
    private final OrganizationIdentityContextResolver identityContextResolver;
    private final Clock clock;

    @Autowired
    public MemberSessionRevocationService(
            @Qualifier("memberSessionStateStore") MemberSessionStateStore stateStore,
            OrganizationIdentityContextResolver identityContextResolver) {
        this(stateStore, identityContextResolver, Clock.systemUTC());
    }

    MemberSessionRevocationService(MemberSessionStateStore stateStore,
            OrganizationIdentityContextResolver identityContextResolver, Clock clock) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.identityContextResolver = Objects.requireNonNull(identityContextResolver, "identityContextResolver");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void revoke(Jwt jwt) {
        String tokenIdentity = tokenIdentity(jwt);
        if (tokenIdentity != null) {
            String hash = tokenHash(tokenIdentity);
            Instant now = clock.instant();
            // A refreshed bearer has a new jti but retains the OIDC session.
            // Keep the session tombstone beyond ordinary access-token expiry
            // so an older or concurrently issued bearer cannot regain access.
            Instant minimumExpiry = now.plusSeconds(30L * 86_400L);
            Instant expires = jwt.getExpiresAt() == null
                    ? minimumExpiry
                    : jwt.getExpiresAt().isAfter(minimumExpiry)
                            ? jwt.getExpiresAt() : minimumExpiry;
            stateStore.deleteExpiredSessions(now);
            stateStore.revokeSession(hash, now, expires);
        }
    }

    public boolean revoked(Jwt jwt) {
        String tokenIdentity = tokenIdentity(jwt);
        if (tokenIdentity == null) {
            return false;
        }
        String hash = tokenHash(tokenIdentity);
        Instant now = clock.instant();
        stateStore.deleteExpiredSessions(now);
        return stateStore.isSessionRevoked(hash, now);
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
        String session = jwt.getClaimAsString("sid");
        if (session == null || session.isBlank()) {
            session = jwt.getClaimAsString("session_state");
        }
        if (session != null && !session.isBlank()) {
            return tenant + "\u0000" + issuer + "\u0000" + subject
                    + "\u0000session:" + session.trim();
        }
        String tokenId = jwt.getClaimAsString("jti");
        if (tokenId != null && !tokenId.isBlank()) {
            return tenant + "\u0000" + issuer + "\u0000" + subject + "\u0000jti:" + tokenId.trim();
        }
        return tenant + "\u0000" + issuer + "\u0000" + subject
                + "\u0000issued:" + jwt.getIssuedAt() + "\u0000expires:" + jwt.getExpiresAt();
    }

}
