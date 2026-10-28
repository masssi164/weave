package com.massimotter.weave.backend.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

class JwtDecoderSupportTest {
    @Test
    void issuerDiscoveryIsDeferredUntilVerificationThenReusesTheValidatedDecoderAndKeys() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("test-key")
                .algorithm(JWSAlgorithm.RS256).generate();
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realm";
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String response = exchange.getRequestURI().getPath().endsWith("/jwks")
                    ? new JWKSet(key.toPublicJWK()).toString()
                    : "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(issuer);
            var decoder = JwtDecoderSupport.configuredRfc9068Decoder(properties,
                    new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtIssuerValidator(issuer),
                            JwtDecoderSupport.rfc9068AccessTokenTypeValidator(),
                            JwtDecoderSupport.exactAudienceValidator(Set.of("api"))));
            assertThat(requests).hasValue(0);
            var token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(new JOSEObjectType("at+jwt")).keyID(key.getKeyID()).build(),
                    new JWTClaimsSet.Builder().issuer(issuer).subject("member").audience("api")
                            .issueTime(Date.from(Instant.now()))
                            .expirationTime(Date.from(Instant.now().plusSeconds(300))).build());
            token.sign(new RSASSASigner(key));
            assertThat(decoder.decode(token.serialize()).getSubject()).isEqualTo("member");
            int initializedRequests = requests.get();
            assertThat(initializedRequests).isGreaterThanOrEqualTo(2);
            assertThat(decoder.decode(token.serialize()).getSubject()).isEqualTo("member");
            assertThat(requests).hasValue(initializedRequests);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exactAudienceRejectsAdditionalResourcesRatherThanTreatingThemAsEquivalent() {
        var validator = JwtDecoderSupport.exactAudienceValidator(Set.of("https://api.weave.test/api"));
        assertThat(validator.validate(token(List.of("https://api.weave.test/api"), "native", "native", "files.read"))
                .hasErrors()).isFalse();
        assertThat(validator.validate(token(List.of("https://api.weave.test/api", "account"),
                "native", "native", "files.read")).hasErrors()).isTrue();
    }

    @Test
    void authorizedPartyClaimsMustAgreeEvenWhenEachIsSeparatelyAllowed() {
        var validator = JwtDecoderSupport.allowedAuthorizedPartiesValidator(List.of("native", "admin"));
        assertThat(validator.validate(token(List.of("api"), "native", "native", "files.read")).hasErrors())
                .isFalse();
        assertThat(validator.validate(token(List.of("api"), "native", "admin", "files.read")).hasErrors())
                .isTrue();
    }

    @Test
    void exactScopeRejectsDuplicateAndBroaderScopeSets() {
        var validator = JwtDecoderSupport.exactScopesValidator(Set.of("files.read"));
        assertThat(validator.validate(token(List.of("api"), "native", "native", "files.read")).hasErrors())
                .isFalse();
        assertThat(validator.validate(token(List.of("api"), "native", "native", "files.read files.read"))
                .hasErrors()).isTrue();
        assertThat(validator.validate(token(List.of("api"), "native", "native", "files.read calendar.read"))
                .hasErrors()).isTrue();
    }

    @Test
    void missingIssuerCannotBeReplacedByAnUnvalidatedDecoder() {
        var decoder = JwtDecoderSupport.configuredDecoder(new OAuth2ResourceServerProperties(),
                jwt -> OAuth2TokenValidatorResult.success());
        assertThatThrownBy(() -> decoder.decode("untrusted"))
                .isInstanceOf(BadJwtException.class).hasMessageContaining("issuer is not configured");
    }

    private static Jwt token(List<String> audience, String party, String client, String scope) {
        return Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject("member").audience(audience).claim("azp", party)
                .claim("client_id", client).claim("scope", scope).build();
    }
}
