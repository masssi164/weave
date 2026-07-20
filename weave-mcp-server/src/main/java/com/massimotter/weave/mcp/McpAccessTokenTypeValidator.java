package com.massimotter.weave.mcp;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Restricts the workload bridge to RFC 9068 access tokens. */
final class McpAccessTokenTypeValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_TOKEN_TYPE = new OAuth2Error(
            "invalid_token",
            "The MCP resource accepts only RFC 9068 at+jwt access tokens.",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object type = jwt == null ? null : jwt.getHeaders().get("typ");
        return type instanceof String value && "at+jwt".equalsIgnoreCase(value)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TOKEN_TYPE);
    }
}
