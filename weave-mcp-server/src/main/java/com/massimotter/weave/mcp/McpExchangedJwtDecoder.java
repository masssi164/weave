package com.massimotter.weave.mcp;

import org.springframework.security.oauth2.jwt.Jwt;

@FunctionalInterface
interface McpExchangedJwtDecoder {
    Jwt decode(String token);
}
