package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class McpAccessTokenTypeValidatorTest {
    private final McpAccessTokenTypeValidator validator = new McpAccessTokenTypeValidator();

    @Test
    void acceptsRfc9068AccessTokenType() {
        assertThat(validator.validate(token(headers -> headers.put("typ", "at+jwt"))).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsGenericJwtAndMissingTokenTypes() {
        assertThat(validator.validate(token(headers -> headers.put("typ", "JWT"))).hasErrors())
                .isTrue();
        assertThat(validator.validate(token(headers -> { })).hasErrors()).isTrue();
        assertThat(validator.validate(null).hasErrors()).isTrue();
    }

    private static Jwt token(Consumer<java.util.Map<String, Object>> headers) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .headers(headers)
                .claim("sub", "service-account")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
