package com.massimotter.weave.backend.security;

import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateStore;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Durable member revocation epoch shared by validated User, Admin and Matrix bearers. */
@Service
public class MemberSessionCutoffService {

    // There is no enforced maximum accepted human JWT lifetime. A bounded tombstone
    // could resurrect a still-valid bearer after cleanup; later OIDC issuance is
    // admitted by its iat, so retaining this hashed epoch does not block reauth.
    private static final Instant RETAIN_UNTIL = Instant.parse("9999-12-31T23:59:59Z");

    private final MatrixFacadeClientStateStore stateStore;
    private final OrganizationIdentityContextResolver identities;
    private final Clock clock;

    @Autowired
    public MemberSessionCutoffService(MatrixFacadeClientStateStore stateStore,
            OrganizationIdentityContextResolver identities) {
        this(stateStore, identities, Clock.systemUTC());
    }

    MemberSessionCutoffService(MatrixFacadeClientStateStore stateStore,
            OrganizationIdentityContextResolver identities, Clock clock) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void advance(String organizationId, String trustedIssuer, String subject, Instant revokedBefore) {
        stateStore.advanceMemberCutoff(memberHash(organizationId, trustedIssuer, subject),
                Objects.requireNonNull(revokedBefore, "revokedBefore"), RETAIN_UNTIL);
    }

    public boolean revoked(Jwt jwt) {
        OrganizationIdentityContext member = identities.resolve(jwt);
        return stateStore.memberCutoff(memberHash(member.organizationId(), member.issuer(), member.subject()),
                        clock.instant())
                .map(cutoff -> jwt.getIssuedAt() == null || !jwt.getIssuedAt().isAfter(cutoff))
                .orElse(false);
    }

    private static String memberHash(String organizationId, String issuer, String subject) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("weave-member-revoked-before-v1".getBytes(StandardCharsets.UTF_8));
            for (String component : new String[] {organizationId, issuer, subject}) {
                String value = Objects.requireNonNull(component, "member identity component");
                if (value.isBlank()) {
                    throw new IllegalArgumentException("member identity component is blank");
                }
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
