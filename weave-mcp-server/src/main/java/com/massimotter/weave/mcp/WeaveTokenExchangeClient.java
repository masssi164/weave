package com.massimotter.weave.mcp;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.TokenExchangeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/** Exchanges the authenticated member token for a backend-only delegated token. */
@Component
final class WeaveTokenExchangeClient implements BackendAccessTokenProvider {

    private final RestClientTokenExchangeTokenResponseClient responseClient;
    private final ClientRegistration clientRegistration;
    private final String inboundAudience;
    private final String inboundAuthorizedParty;
    private final String inboundScope;

    @Autowired
    WeaveTokenExchangeClient(
            @Value("${weave.oidc.token-uri:}") String tokenUri,
            @Value("${weave.oidc.mcp-client-id:weave-mcp-server}") String clientId,
            @Value("${weave.oidc.mcp-client-secret:}") String clientSecret,
            @Value("${weave.oidc.backend-audience:weave-backend}") String backendAudience,
            @Value("${weave.oidc.backend-scope:weave:mcp-backend}") String backendScope,
            @Value("${weave.oidc.inbound-audience:weave-mcp-server}") String inboundAudience,
            @Value("${weave.oidc.inbound-authorized-party:weave-app}") String inboundAuthorizedParty,
            @Value("${weave.oidc.inbound-scope:weave:mcp}") String inboundScope) {
        this(new RestClientTokenExchangeTokenResponseClient(), tokenUri, clientId, clientSecret,
                backendAudience, backendScope, inboundAudience, inboundAuthorizedParty, inboundScope);
    }

    WeaveTokenExchangeClient(
            RestClientTokenExchangeTokenResponseClient responseClient,
            String tokenUri,
            String clientId,
            String clientSecret,
            String backendAudience,
            String backendScope,
            String inboundAudience,
            String inboundAuthorizedParty,
            String inboundScope) {
        this.responseClient = responseClient;
        this.inboundAudience = required(inboundAudience, "inbound audience");
        this.inboundAuthorizedParty = required(inboundAuthorizedParty, "inbound authorized party");
        this.inboundScope = required(inboundScope, "inbound scope");
        String targetAudience = required(backendAudience, "backend audience");
        String targetScope = required(backendScope, "backend scope");
        this.clientRegistration = ClientRegistration.withRegistrationId("weave-mcp-backend-token-exchange")
                .clientId(required(clientId, "MCP client ID"))
                .clientSecret(required(clientSecret, "MCP client secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .tokenUri(required(tokenUri, "OIDC token URI"))
                .scope(targetScope)
                .build();
        this.responseClient.addParametersConverter(ignored -> {
            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.set(OAuth2ParameterNames.AUDIENCE, targetAudience);
            return parameters;
        });
    }

    @Override
    public String exchangeCurrentMemberToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new McpBoundaryException("mcp-member-token-missing");
        }
        Jwt jwt = jwtAuthentication.getToken();
        validateInbound(jwt);
        OAuth2AccessToken subjectToken = new OAuth2AccessToken(
                TokenType.BEARER,
                jwt.getTokenValue(),
                requiredInstant(jwt.getIssuedAt(), "issued-at"),
                requiredInstant(jwt.getExpiresAt(), "expiry"),
                scopes(jwt));
        OAuth2AccessTokenResponse response = responseClient.getTokenResponse(
                new TokenExchangeGrantRequest(clientRegistration, subjectToken, null));
        String exchangedToken = response == null || response.getAccessToken() == null
                ? null
                : response.getAccessToken().getTokenValue();
        if (!StringUtils.hasText(exchangedToken)) {
            throw new McpBoundaryException("mcp-token-exchange-empty");
        }
        return exchangedToken;
    }

    private void validateInbound(Jwt jwt) {
        String subject = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        if (!StringUtils.hasText(subject)
                || subject.startsWith("service-account-")
                || (username != null && username.startsWith("service-account-"))) {
            throw new McpBoundaryException("mcp-member-subject-required");
        }
        if (!jwt.getAudience().contains(inboundAudience)) {
            throw new McpBoundaryException("mcp-token-audience-invalid");
        }
        if (!claimMatchesExactly(jwt, "azp", inboundAuthorizedParty)
                || !optionalClaimMatches(jwt, "client_id", inboundAuthorizedParty)) {
            throw new McpBoundaryException("mcp-token-authorized-party-invalid");
        }
        if (!scopes(jwt).contains(inboundScope)) {
            throw new McpBoundaryException("mcp-token-scope-invalid");
        }
    }

    private static boolean claimMatchesExactly(Jwt jwt, String claim, String expected) {
        return expected.equals(jwt.getClaimAsString(claim));
    }

    private static boolean optionalClaimMatches(Jwt jwt, String claim, String expected) {
        String value = jwt.getClaimAsString(claim);
        return value == null || expected.equals(value);
    }

    private static Set<String> scopes(Jwt jwt) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        String scope = jwt.getClaimAsString("scope");
        if (StringUtils.hasText(scope)) {
            Arrays.stream(scope.trim().split("\\s+")).filter(StringUtils::hasText).forEach(scopes::add);
        }
        List<String> scp = jwt.getClaimAsStringList("scp");
        if (scp != null) {
            scp.stream().filter(StringUtils::hasText).forEach(scopes::add);
        }
        return Set.copyOf(scopes);
    }

    private static Instant requiredInstant(Instant value, String field) {
        if (value == null) {
            throw new McpBoundaryException("mcp-member-token-" + field + "-missing");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(field + " must be configured");
        }
        return value.trim();
    }
}
