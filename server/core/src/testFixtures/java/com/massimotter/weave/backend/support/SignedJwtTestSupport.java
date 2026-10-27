package com.massimotter.weave.backend.support;

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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Signed tokens and a loopback JWKS endpoint shared by actual decoder tests. */
public final class SignedJwtTestSupport {
    private SignedJwtTestSupport() {}

    public static RSAKey rsaSigningKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
    }

    public static String signedToken(RSAKey signingKey, String issuerUri) throws Exception {
        return signedToken(
                signingKey,
                issuerUri,
                List.of("https://api.weave.test/api"),
                "weave-app");
    }

    public static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty) throws Exception {
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, null);
    }

    public static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty,
            JOSEObjectType type) throws Exception {
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, type, "user-123");
    }

    public static String signedToken(
            RSAKey signingKey,
            String issuerUri,
            List<String> audiences,
            String authorizedParty,
            JOSEObjectType type,
            String subject) throws Exception {
        return signedToken(signingKey, issuerUri, audiences, authorizedParty, type, subject,
                "weave:workspace");
    }

    public static String signedToken(
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

    public static String signedToken(
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

    public static final class JwksServer implements AutoCloseable {

        private final HttpServer server;
        private final AtomicInteger requests;

        private JwksServer(HttpServer server, AtomicInteger requests) {
            this.server = server;
            this.requests = requests;
        }

        public static JwksServer start(RSAKey signingKey) throws IOException {
            return start(signingKey, 0);
        }

        public static JwksServer start(RSAKey signingKey, long responseDelayMillis) throws IOException {
            AtomicInteger requests = new AtomicInteger();
            String jwksJson = new JWKSet(signingKey.toPublicJWK()).toString();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                requests.incrementAndGet();
                try {
                    Thread.sleep(responseDelayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
                byte[] response = jwksJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
            return new JwksServer(server, requests);
        }

        public String jwkSetUri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
