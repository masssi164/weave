package com.massimotter.weave.backend.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import com.massimotter.weave.backend.security.MemberSessionCutoffService;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.testing.InMemoryMatrixFacadeClientStateStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtDecoderConfigTest {

    private static final String ISSUER_URI = "https://auth.weave.test/realms/weave";

    @Test
    void issuerEpochAheadOfBackendClockRejectsRetainedSignedBearerAndAdmitsLaterIssuance()
            throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            WeaveSecurityProperties security =
                    new WeaveSecurityProperties("https://api.weave.test/api", "weave-app");
            var identities = OrganizationIdentityContextResolver.configured(
                    new ContextAuthorizationProperties("weave_tenant_id", "tenant_id", "acme",
                            "sub", "user:", List.of(), List.of(), List.of()));
            var cutoffs = new MemberSessionCutoffService(
                    new InMemoryMatrixFacadeClientStateStore(), identities);
            Instant backendNow = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            Instant issuerNotBefore = backendNow.plusSeconds(20);
            cutoffs.advance("acme", ISSUER_URI, "revoked-subject", backendNow);
            cutoffs.advance("acme", ISSUER_URI, "revoked-subject", issuerNotBefore);
            var sessions = org.mockito.Mockito.mock(
                    com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService.class);
            @SuppressWarnings("unchecked")
            org.springframework.beans.factory.ObjectProvider<
                    com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService> sessionsProvider =
                    org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
            @SuppressWarnings("unchecked")
            org.springframework.beans.factory.ObjectProvider<MemberSessionCutoffService> cutoffProvider =
                    org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
            org.mockito.Mockito.when(sessionsProvider.getObject()).thenReturn(sessions);
            org.mockito.Mockito.when(cutoffProvider.getObject()).thenReturn(cutoffs);
            JwtDecoderConfig config = new JwtDecoderConfig();
            JwtDecoder user = config.memberJwtDecoder(properties, security, sessionsProvider, cutoffProvider);
            JwtDecoder admin = config.adminApiJwtDecoder(properties, security, cutoffProvider);
            for (String client : List.of("weave-app", "weave-admin-console")) {
                JwtDecoder decoder = "weave-app".equals(client) ? user : admin;
                String retained = signedToken(signingKey, ISSUER_URI,
                        List.of("https://api.weave.test/api"), client, null, "revoked-subject",
                        "weave:workspace", backendNow.plusSeconds(10), backendNow.plusSeconds(300));
                String later = signedToken(signingKey, ISSUER_URI,
                        List.of("https://api.weave.test/api"), client, null, "revoked-subject",
                        "weave:workspace", issuerNotBefore.plusSeconds(1), backendNow.plusSeconds(300));
                assertThrows(JwtException.class, () -> decoder.decode(retained));
                assertThat(decoder.decode(later).getSubject()).isEqualTo("revoked-subject");
            }
        }
    }

    @Test
    void validatedUserAndAdminBearersObeyMemberCutoffWithoutAdmittingWrongPrincipal() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            WeaveSecurityProperties security =
                    new WeaveSecurityProperties("https://api.weave.test/api", "weave-app");
            var sessions = org.mockito.Mockito.mock(
                    com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService.class);
            var cutoffs = org.mockito.Mockito.mock(MemberSessionCutoffService.class);
            @SuppressWarnings("unchecked")
            org.springframework.beans.factory.ObjectProvider<
                    com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService> sessionsProvider =
                    org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
            @SuppressWarnings("unchecked")
            org.springframework.beans.factory.ObjectProvider<MemberSessionCutoffService> cutoffProvider =
                    org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
            org.mockito.Mockito.when(sessionsProvider.getObject()).thenReturn(sessions);
            org.mockito.Mockito.when(cutoffProvider.getObject()).thenReturn(cutoffs);
            Instant cutoff = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minusSeconds(5);
            org.mockito.Mockito.when(cutoffs.revoked(org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(call -> {
                        Jwt jwt = call.getArgument(0);
                        return jwt.getIssuedAt() == null || !jwt.getIssuedAt().isAfter(cutoff);
                    });
            JwtDecoderConfig config = new JwtDecoderConfig();
            JwtDecoder user = config.memberJwtDecoder(properties, security, sessionsProvider, cutoffProvider);
            JwtDecoder admin = config.adminApiJwtDecoder(properties, security, cutoffProvider);
            for (String client : List.of("weave-app", "weave-admin-console")) {
                JwtDecoder decoder = "weave-app".equals(client) ? user : admin;
                String old = signedToken(signingKey, ISSUER_URI,
                        List.of("https://api.weave.test/api"), client, null, "revoked-subject",
                        "weave:workspace", cutoff.minusSeconds(30), cutoff.plusSeconds(300));
                String fresh = signedToken(signingKey, ISSUER_URI,
                        List.of("https://api.weave.test/api"), client, null, "revoked-subject",
                        "weave:workspace", cutoff.plusSeconds(1), cutoff.plusSeconds(300));
                assertThrows(JwtException.class, () -> decoder.decode(old));
                assertThat(decoder.decode(fresh).getSubject()).isEqualTo("revoked-subject");
            }
            org.mockito.Mockito.clearInvocations(cutoffs);
            assertThrows(JwtValidationException.class, () -> admin.decode(signedToken(signingKey,
                    ISSUER_URI, List.of("https://api.weave.test/api"), "weave-app")));
            assertThrows(JwtValidationException.class, () -> user.decode(signedToken(signingKey,
                    "https://wrong-issuer.invalid", List.of("https://api.weave.test/api"), "weave-app")));
            org.mockito.Mockito.verifyNoInteractions(cutoffs);
        }
    }

    @Test
    void memberDecoderRejectsRevokedBearerAfterSignatureAndClaimValidation() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            var sessions = org.mockito.Mockito.mock(
                    com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService.class);
            @SuppressWarnings("unchecked")
            org.springframework.beans.factory.ObjectProvider<
                    com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService> provider =
                    org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
            org.mockito.Mockito.when(provider.getObject()).thenReturn(sessions);
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            JwtDecoder decoder = new JwtDecoderConfig().memberJwtDecoder(
                    properties, new WeaveSecurityProperties(null, null), provider);
            String bearer = signedToken(signingKey, ISSUER_URI);
            assertThat(decoder.decode(bearer).getSubject()).isNotBlank();
            org.mockito.Mockito.when(sessions.revoked(org.mockito.ArgumentMatchers.any())).thenReturn(true);
            assertThrows(JwtException.class, () -> decoder.decode(bearer));
            org.mockito.Mockito.clearInvocations(provider, sessions);
            assertThrows(JwtException.class,
                    () -> decoder.decode(signedToken(signingKey, "https://wrong-issuer.invalid")));
            org.mockito.Mockito.verifyNoInteractions(provider, sessions);
        }
    }

    @Test
    void usesConfiguredJwkSetUriAndValidatesPublicIssuer() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            JwtDecoder jwtDecoder = jwtDecoder(jwksServer.jwkSetUri());

            Jwt jwt = jwtDecoder.decode(signedToken(signingKey, ISSUER_URI));

            assertThat(jwt.getIssuer()).hasToString(ISSUER_URI);
        }
    }

    @Test
    void rejectsWrongIssuerWhenConfiguredWithJwkSetUri() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            JwtDecoder jwtDecoder = jwtDecoder(jwksServer.jwkSetUri());

            assertThrows(JwtValidationException.class,
                    () -> jwtDecoder.decode(signedToken(signingKey, "https://wrong.example.invalid/realms/weave")));
        }
    }

    @Test
    void rejectsExpiredSignedUserAdminAndMcpCredentialsAfterAcceptingFreshControls() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            WeaveSecurityProperties security =
                    new WeaveSecurityProperties("https://api.weave.test/api", "weave-app");
            JwtDecoderConfig config = new JwtDecoderConfig();
            record Credential(JwtDecoder decoder, String client, JOSEObjectType type, String scope) {}
            List<Credential> credentials = List.of(
                    new Credential(config.jwtDecoder(properties, security), "weave-app", null, "weave:workspace"),
                    new Credential(config.adminApiJwtDecoder(properties, security),
                            "weave-admin-console", null, "weave:workspace"),
                    new Credential(config.filesMcpWorkloadJwtDecoder(properties, security),
                            "weave-mcp-server", new JOSEObjectType("at+jwt"), "files.read"),
                    new Credential(config.calendarMcpWorkloadJwtDecoder(properties, security),
                            "weave-mcp-server", new JOSEObjectType("at+jwt"), "calendar.write"));
            for (Credential credential : credentials) {
                assertThat(credential.decoder().decode(signedToken(signingKey, ISSUER_URI,
                        List.of("https://api.weave.test/api"), credential.client(),
                        credential.type(), "expiry-subject", credential.scope())).getSubject())
                        .isEqualTo("expiry-subject");
                Instant now = Instant.now();
                String expired = signedToken(signingKey, ISSUER_URI,
                        List.of("https://api.weave.test/api"), credential.client(),
                        credential.type(), "expiry-subject", credential.scope(),
                        now.minusSeconds(600), now.minusSeconds(120));
                JwtValidationException denied = assertThrows(
                        JwtValidationException.class, () -> credential.decoder().decode(expired));
                assertThat(denied.getErrors()).anySatisfy(
                        error -> assertThat(error.getDescription()).contains("expired"));
            }
        }
    }

    @Test
    void humanDecodersRejectSignedTokensWithoutAnImmutableSubject() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            JwtDecoder userDecoder = jwtDecoder(jwksServer.jwkSetUri());
            JwtDecoder adminDecoder = productAdminJwtDecoder(jwksServer.jwkSetUri());
            JwtDecoder runtimeAdminDecoder = adminJwtDecoder(jwksServer.jwkSetUri());

            for (String subject : new String[] {null, "", "   "}) {
                assertThrows(JwtValidationException.class, () -> userDecoder.decode(signedToken(
                        signingKey, ISSUER_URI, List.of("https://api.weave.test/api"), "weave-app", null, subject)));
                assertThrows(JwtValidationException.class, () -> adminDecoder.decode(signedToken(
                        signingKey, ISSUER_URI, List.of("https://api.weave.test/api"), "weave-admin-console", null, subject)));
                assertThrows(JwtValidationException.class, () -> runtimeAdminDecoder.decode(signedToken(
                        signingKey, ISSUER_URI, List.of("https://api.weave.test/api"),
                        AgentRuntimeAdminSecurityConfiguration.CLIENT_ID, null, subject)));
            }
        }
    }

    @Test
    void nativeUserDecoderRejectsExtraAudienceAndAdminClient() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            JwtDecoder decoder = jwtDecoder(jwksServer.jwkSetUri());
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey, ISSUER_URI,
                    List.of("https://api.weave.test/api", "account"), "weave-app")));
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey, ISSUER_URI,
                    List.of("https://api.weave.test/api"), "weave-admin-console")));
        }
    }

    @Test
    void failsClosedWhenIssuerIsMissing() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();

        JwtDecoder jwtDecoder = new JwtDecoderConfig().jwtDecoder(properties, new WeaveSecurityProperties(null, null));

        assertThrows(JwtException.class, () -> jwtDecoder.decode("token-value"));
    }

    @Test
    void workloadTypeValidatorAcceptsOnlyRfc9068AccessTokens() {
        Instant now = Instant.now();
        Jwt accessToken = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .subject("service-account")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        Jwt genericJwt = Jwt.withTokenValue("generic-jwt")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject("service-account")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();

        assertThat(JwtDecoderConfig.rfc9068AccessTokenTypeValidator().validate(accessToken).hasErrors())
                .isFalse();
        assertThat(JwtDecoderConfig.rfc9068AccessTokenTypeValidator().validate(genericJwt).hasErrors())
                .isTrue();
    }

    @Test
    void exchangedMcpTokenRequiresExactBackendAudiencePrincipalTypeAndFilesScope() {
        Instant now = Instant.now();
        var validator = new DelegatingOAuth2TokenValidator<Jwt>(
                JwtDecoderConfig.exactAudienceValidator(java.util.Set.of("https://api.weave.test/api")),
                JwtDecoderConfig.requiredAuthorizedPartyValidator("weave-mcp-server"),
                JwtDecoderConfig.rfc9068AccessTokenTypeValidator(),
                JwtDecoderConfig.exactScopesValidator(java.util.Set.of("files.read")));
        Jwt accepted = exchangedToken(now, List.of("https://api.weave.test/api"), "at+jwt");
        Jwt broadAudience = exchangedToken(
                now, List.of("https://api.weave.test/api", "account"), "at+jwt");
        Jwt genericType = exchangedToken(now, List.of("https://api.weave.test/api"), "JWT");
        Jwt wrongScope = Jwt.withTokenValue("exchange")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .audience(List.of("https://api.weave.test/api"))
                .claim("azp", "weave-mcp-server")
                .claim("scope", "calendar.read")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        Jwt humanToken = Jwt.withTokenValue("human")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .audience(List.of("https://api.weave.test/api", "account"))
                .claim("azp", "weave-app")
                .claim("scope", "files.read")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();

        assertThat(validator.validate(accepted).hasErrors()).isFalse();
        assertThat(validator.validate(broadAudience).hasErrors()).isTrue();
        assertThat(validator.validate(genericType).hasErrors()).isTrue();
        assertThat(validator.validate(wrongScope).hasErrors()).isTrue();
        assertThat(validator.validate(humanToken).hasErrors()).isTrue();
    }

    @Test
    void calendarWorkloadDecoderAcceptsOneExactSignedCalendarScope() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            JwtDecoder decoder = new JwtDecoderConfig().calendarMcpWorkloadJwtDecoder(
                    properties, new WeaveSecurityProperties("https://api.weave.test/api", "weave-app"));

            assertThat(decoder.decode(signedToken(signingKey, ISSUER_URI,
                    List.of("https://api.weave.test/api"), "weave-mcp-server",
                    new JOSEObjectType("at+jwt"), "cell-subject", "calendar.read"))
                    .getClaimAsString("scope")).isEqualTo("calendar.read");
            assertThat(decoder.decode(signedToken(signingKey, ISSUER_URI,
                    List.of("https://api.weave.test/api"), "weave-mcp-server",
                    new JOSEObjectType("at+jwt"), "cell-subject", "calendar.write"))
                    .getClaimAsString("scope")).isEqualTo("calendar.write");
            for (String scope : List.of("files.read", "files.read calendar.read",
                    "calendar.read calendar.write", "calendar.write calendar.write")) {
                assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(signingKey,
                        ISSUER_URI, List.of("https://api.weave.test/api"), "weave-mcp-server",
                        new JOSEObjectType("at+jwt"), "cell-subject", scope)));
            }
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(signingKey,
                    ISSUER_URI, List.of("https://api.weave.test/api"), "weave-app",
                    new JOSEObjectType("at+jwt"), "member", "calendar.read")));
        }
    }

    @Test
    void rfc9068DecoderAcceptsAtJwtAndRejectsGenericJwtBeforeClaimsAreTrusted() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            JwtDecoder decoder = JwtDecoderConfig.configuredRfc9068Decoder(
                    properties, JwtDecoderConfig.rfc9068AccessTokenTypeValidator());

            Jwt accepted = decoder.decode(signedToken(
                    signingKey,
                    ISSUER_URI,
                    List.of("https://api.weave.test/api"),
                    "weave-mcp-server",
                    new JOSEObjectType("at+jwt")));
            assertThat(accepted.getHeaders().get("typ")).isEqualTo("at+jwt");

            assertThrows(JwtException.class, () -> decoder.decode(signedToken(
                    signingKey,
                    ISSUER_URI,
                    List.of("https://api.weave.test/api"),
                    "weave-mcp-server",
                    JOSEObjectType.JWT)));
        }
    }

    @Test
    void adminDecoderAcceptsOnlyTheAdminConsoleWithTheExactApiAudience() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            JwtDecoder decoder = adminJwtDecoder(jwksServer.jwkSetUri());

            Jwt accepted = decoder.decode(signedToken(
                    signingKey,
                    ISSUER_URI,
                    List.of("https://api.weave.test/api"),
                    AgentRuntimeAdminSecurityConfiguration.CLIENT_ID));
            assertThat(accepted.getAudience()).containsExactly("https://api.weave.test/api");

            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey,
                    ISSUER_URI,
                    List.of("https://api.weave.test/api"),
                    "weave-app")));
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey,
                    ISSUER_URI,
                    List.of("https://api.weave.test/api", "account"),
                    AgentRuntimeAdminSecurityConfiguration.CLIENT_ID)));
        }
    }

    @Test
    void productAdminApiDecoderSeparatesBrowserAdminFromNativeUserTokens() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            JwtDecoder decoder = new JwtDecoderConfig().adminApiJwtDecoder(
                    properties, new WeaveSecurityProperties("https://api.weave.test/api", "weave-app"));

            assertThat(decoder.decode(signedToken(
                    signingKey, ISSUER_URI, List.of("https://api.weave.test/api"), "weave-admin-console"))
                    .getSubject()).isEqualTo("user-123");
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey, ISSUER_URI, List.of("https://api.weave.test/api"), "weave-app")));
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey, ISSUER_URI, List.of("https://api.weave.test/api", "account"),
                    "weave-admin-console")));
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(
                    signingKey, "https://wrong.example.invalid/realms/weave",
                    List.of("https://api.weave.test/api"), "weave-admin-console")));
        }
    }

    private JwtDecoder jwtDecoder(String jwkSetUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER_URI);
        properties.getJwt().setJwkSetUri(jwkSetUri);
        return new JwtDecoderConfig().jwtDecoder(properties, new WeaveSecurityProperties(null, null));
    }

    private static Jwt exchangedToken(Instant now, List<String> audience, String type) {
        return Jwt.withTokenValue("exchange")
                .header("alg", "RS256")
                .header("typ", type)
                .audience(audience)
                .claim("azp", "weave-mcp-server")
                .claim("scope", "files.read")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    private JwtDecoder adminJwtDecoder(String jwkSetUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER_URI);
        properties.getJwt().setJwkSetUri(jwkSetUri);
        return new JwtDecoderConfig().agentRuntimeAdminJwtDecoder(
                properties,
                new WeaveSecurityProperties("https://api.weave.test/api", "weave-app"));
    }

    private JwtDecoder productAdminJwtDecoder(String jwkSetUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER_URI);
        properties.getJwt().setJwkSetUri(jwkSetUri);
        return new JwtDecoderConfig().adminApiJwtDecoder(
                properties,
                new WeaveSecurityProperties("https://api.weave.test/api", "weave-app"));
    }

    private static RSAKey rsaSigningKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
    }

    private static String signedToken(RSAKey signingKey, String issuerUri) throws Exception {
        return signedToken(
                signingKey,
                issuerUri,
                List.of("https://api.weave.test/api"),
                "weave-app");
    }

    private static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty) throws Exception {
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, null);
    }

    private static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty,
            JOSEObjectType type) throws Exception {
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, type, "user-123");
    }

    private static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty,
            JOSEObjectType type,
            String subject) throws Exception {
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, type, subject,
                "weave:workspace");
    }

    private static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty,
            JOSEObjectType type,
            String subject,
            String scope) throws Exception {
        Instant now = Instant.now();
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, type, subject, scope,
                now, now.plusSeconds(300));
    }

    private static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty,
            JOSEObjectType type,
            String subject,
            String scope,
            Instant issuedAt,
            Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuerUri)
                .subject(subject)
                .audience(audiences)
                .claim("azp", authorizedParty)
                .claim("scope", scope)
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt.minusSeconds(30)))
                .expirationTime(Date.from(expiresAt))
                .build();
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID());
        if (type != null) {
            header.type(type);
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        JWSSigner signer = new RSASSASigner(signingKey);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private static final class JwksServer implements AutoCloseable {

        private final HttpServer server;

        private JwksServer(HttpServer server) {
            this.server = server;
        }

        static JwksServer start(RSAKey signingKey) throws IOException {
            String jwksJson = new JWKSet(signingKey.toPublicJWK()).toString();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] response = jwksJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
            return new JwksServer(server);
        }

        String jwkSetUri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
