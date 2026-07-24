package com.massimotter.weave.mcp;

import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.domain.ExchangedWorkloadToken;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/** Verifies the exchanged JWT locally before it can enter the shared application core. */
final class McpExchangedTokenAuthenticator {
    private final McpExchangedJwtDecoder decoder;
    private final McpExchangedTokenPolicy policy;

    McpExchangedTokenAuthenticator(
            McpExchangedJwtDecoder decoder,
            McpExchangedTokenPolicy policy) {
        this.decoder = decoder;
        this.policy = policy;
    }

    ExchangedWorkloadToken authenticate(
            McpCellWorkloadPrincipal inbound,
            ExchangedAccessToken exchangeResponse) {
        try {
            Jwt jwt = decoder.decode(exchangeResponse.value());
            ExchangedWorkloadToken authenticated = policy.resolve(jwt);
            if (!authenticated.subject().equals(inbound.subject())
                    || !authenticated.subject().equals(exchangeResponse.subject())
                    || !authenticated.edgeClientId().equals(exchangeResponse.authorizedParty())
                    || !authenticated.scopes().equals(exchangeResponse.scopes())
                    || !Set.copyOf(jwt.getAudience()).equals(exchangeResponse.audiences())
                    || !authenticated.issuedAt().equals(exchangeResponse.issuedAt())
                    || !authenticated.expiresAt().equals(exchangeResponse.expiresAt())
                    || authenticated.expiresAt().isAfter(inbound.expiresAt())) {
                throw forbidden();
            }
            return authenticated;
        } catch (JwtException | IllegalArgumentException failure) {
            throw forbidden();
        }
    }

    private static McpAdmissionException forbidden() {
        return new McpAdmissionException(McpAdmissionException.Kind.FORBIDDEN);
    }
}
