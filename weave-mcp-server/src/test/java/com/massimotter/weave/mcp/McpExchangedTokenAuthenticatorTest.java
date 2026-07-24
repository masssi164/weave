package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class McpExchangedTokenAuthenticatorTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String API_RESOURCE = "https://api.weave.test/api";
    private static final String SUBJECT = "service-account-cell-subject";
    private static final String EDGE = "weave-mcp-server";

    @Test
    void acceptsOnlyACryptographicallyVerifiedExactExchangeToken() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var trusted = generator.generateKeyPair();
        var attacker = generator.generateKeyPair();
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) trusted.getPublic())
                .validateType(false)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(ISSUER),
                new McpAccessTokenTypeValidator()));
        McpExchangedTokenAuthenticator authenticator = new McpExchangedTokenAuthenticator(
                decoder::decode,
                new McpExchangedTokenPolicy(API_RESOURCE, EDGE));
        Instant issuedAt = Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(30);
        McpCellWorkloadPrincipal inbound = inbound(expiresAt.plusSeconds(1));

        String valid = signed((RSAPrivateKey) trusted.getPrivate(), issuedAt, expiresAt, API_RESOURCE, "calendar.read");
        ExchangedAccessToken response = response(valid, issuedAt, expiresAt, API_RESOURCE, Set.of("calendar.read"));
        assertThat(authenticator.authenticate(inbound, response).subject()).isEqualTo(SUBJECT);

        String forged = signed((RSAPrivateKey) attacker.getPrivate(), issuedAt, expiresAt, API_RESOURCE, "calendar.read");
        assertThatThrownBy(() -> authenticator.authenticate(
                inbound,
                response(forged, issuedAt, expiresAt, API_RESOURCE, Set.of("calendar.read"))))
                .isInstanceOf(McpAdmissionException.class);
    }

    @Test
    void rejectsAudienceAndScopeMismatchAfterSignatureVerification() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) keys.getPublic())
                .validateType(false)
                .build();
        McpExchangedTokenAuthenticator authenticator = new McpExchangedTokenAuthenticator(
                decoder::decode,
                new McpExchangedTokenPolicy(API_RESOURCE, EDGE));
        Instant issuedAt = Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(30);

        String wrongAudience = signed(
                (RSAPrivateKey) keys.getPrivate(),
                issuedAt,
                expiresAt,
                "https://api.weave.test/other",
                "calendar.read");
        assertThatThrownBy(() -> authenticator.authenticate(
                inbound(expiresAt.plusSeconds(1)),
                response(wrongAudience, issuedAt, expiresAt, "https://api.weave.test/other", Set.of("calendar.read"))))
                .isInstanceOf(McpAdmissionException.class);

        String widerScope = signed(
                (RSAPrivateKey) keys.getPrivate(),
                issuedAt,
                expiresAt,
                API_RESOURCE,
                "calendar.read files.read");
        assertThatThrownBy(() -> authenticator.authenticate(
                inbound(expiresAt.plusSeconds(1)),
                response(widerScope, issuedAt, expiresAt, API_RESOURCE, Set.of("calendar.read"))))
                .isInstanceOf(McpAdmissionException.class);
    }

    private static McpCellWorkloadPrincipal inbound(Instant expiresAt) {
        return new McpCellWorkloadPrincipal(
                ISSUER,
                SUBJECT,
                "weaver-cell-test",
                Set.of("mcp.tools", "calendar.read"),
                expiresAt.minusSeconds(60),
                expiresAt,
                "cell-jti");
    }

    private static ExchangedAccessToken response(
            String token,
            Instant issuedAt,
            Instant expiresAt,
            String audience,
            Set<String> scopes) {
        return new ExchangedAccessToken(
                token,
                SUBJECT,
                EDGE,
                Set.of(audience),
                scopes,
                issuedAt,
                expiresAt);
    }

    private static String signed(
            RSAPrivateKey key,
            Instant issuedAt,
            Instant expiresAt,
            String audience,
            String scope) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(SUBJECT)
                .audience(List.of(audience))
                .claim("azp", EDGE)
                .claim("client_id", EDGE)
                .claim("scope", scope)
                .claim("realm_access", Map.of())
                .claim("resource_access", Map.of())
                .jwtID("exchange-jti")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(new JOSEObjectType("at+jwt"))
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
