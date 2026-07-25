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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtDecoderConfigTest {

    private static final String ISSUER_URI = "https://auth.weave.test/realms/weave";

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

    private JwtDecoder jwtDecoder(String jwkSetUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER_URI);
        properties.getJwt().setJwkSetUri(jwkSetUri);
        return new JwtDecoderConfig().jwtDecoder(properties, new WeaveSecurityProperties(null, null));
    }

    private JwtDecoder adminJwtDecoder(String jwkSetUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER_URI);
        properties.getJwt().setJwkSetUri(jwkSetUri);
        return new JwtDecoderConfig().agentRuntimeAdminJwtDecoder(
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
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuerUri)
                .subject("user-123")
                .audience(audiences)
                .claim("azp", authorizedParty)
                .claim("scope", "weave:workspace")
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)))
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
