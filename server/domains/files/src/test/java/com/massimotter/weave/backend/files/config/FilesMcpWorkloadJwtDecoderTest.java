package com.massimotter.weave.backend.files.config;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.massimotter.weave.backend.support.SignedJwtTestSupport.JwksServer;
import static com.massimotter.weave.backend.support.SignedJwtTestSupport.rsaSigningKey;
import static com.massimotter.weave.backend.support.SignedJwtTestSupport.signedToken;

class FilesMcpWorkloadJwtDecoderTest {
    private static final String ISSUER_URI = "https://auth.weave.test/realms/weave";

    @Test
    void filesWorkloadDecoderAcceptsOnlyExactSignedFilesReadScope() throws Exception {
        RSAKey signingKey = rsaSigningKey();
        try (JwksServer jwksServer = JwksServer.start(signingKey)) {
            OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwksServer.jwkSetUri());
            JwtDecoder decoder = new FilesWebDavSecurityConfiguration().filesMcpWorkloadJwtDecoder(
                    properties, new WeaveSecurityProperties("https://api.weave.test/api", "weave-app"));

            assertThat(decoder.decode(signedToken(signingKey, ISSUER_URI,
                    List.of("https://api.weave.test/api"), "weave-mcp-server",
                    new JOSEObjectType("at+jwt"), "cell-subject", "files.read"))
                    .getClaimAsString("scope")).isEqualTo("files.read");
            for (String scope : List.of("calendar.read", "files.read calendar.read",
                    "files.write", "files.read files.read")) {
                assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(signingKey,
                        ISSUER_URI, List.of("https://api.weave.test/api"), "weave-mcp-server",
                        new JOSEObjectType("at+jwt"), "cell-subject", scope)));
            }
            assertThrows(JwtValidationException.class, () -> decoder.decode(signedToken(signingKey,
                    ISSUER_URI, List.of("https://api.weave.test/api"), "weave-app",
                    new JOSEObjectType("at+jwt"), "member", "files.read")));
        }
    }

    @Test
    void signedProfileRejectsWrongIssuerAudienceTypeClientAndExpiredBearer() throws Exception {
        RSAKey key = rsaSigningKey();
        try (JwksServer jwks = JwksServer.start(key)) {
            var properties = new OAuth2ResourceServerProperties();
            properties.getJwt().setIssuerUri(ISSUER_URI);
            properties.getJwt().setJwkSetUri(jwks.jwkSetUri());
            JwtDecoder decoder = new FilesWebDavSecurityConfiguration().filesMcpWorkloadJwtDecoder(
                    properties, new WeaveSecurityProperties("https://api.weave.test/api", "weave-app"));
            String scope = "files.read";
            var audience = List.of("https://api.weave.test/api");
            var type = new JOSEObjectType("at+jwt");
            assertThat(decoder.decode(signedToken(key, ISSUER_URI, audience,
                    "weave-mcp-server", type, "expiry-subject", scope)).getSubject()).isEqualTo("expiry-subject");
            for (String token : List.of(
                    signedToken(key, "https://wrong.example/issuer", audience, "weave-mcp-server", type, "subject", scope),
                    signedToken(key, ISSUER_URI, List.of("https://api.weave.test/api", "extra"), "weave-mcp-server", type, "subject", scope),
                    signedToken(key, ISSUER_URI, audience, "weave-mcp-server", new JOSEObjectType("JWT"), "subject", scope),
                    signedToken(key, ISSUER_URI, audience, "weave-app", type, "subject", scope))) {
                assertThrows(JwtException.class, () -> decoder.decode(token));
            }
            Instant now = Instant.now();
            String expired = signedToken(key, ISSUER_URI, audience, "weave-mcp-server", type,
                    "expiry-subject", scope, now.minusSeconds(600), now.minusSeconds(120));
            JwtValidationException denied = assertThrows(JwtValidationException.class, () -> decoder.decode(expired));
            assertThat(denied.getErrors()).anySatisfy(error -> assertThat(error.getDescription()).contains("expired"));
        }
    }

}
