package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeaveTokenExchangeClientTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exchangesCurrentMemberTokenForExactBackendAudienceAndScope() {
        RestClient.Builder builder = RestClient.builder()
                .configureMessageConverters(converters ->
                        converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientTokenExchangeTokenResponseClient responseClient = new RestClientTokenExchangeTokenResponseClient();
        responseClient.setRestClient(builder.build());
        WeaveTokenExchangeClient client = client(responseClient);
        authenticate(memberToken("member-token", "member-123", List.of("weave-mcp-server"), "weave-app", "weave:mcp"));

        server.expect(requestTo("https://auth.weave.test/realms/weave/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic d2VhdmUtbWNwLXNlcnZlcjp0ZXN0LXNlY3JldA=="))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"),
                        org.hamcrest.Matchers.containsString("subject_token=member-token"),
                        org.hamcrest.Matchers.containsString("audience=weave-backend"),
                        org.hamcrest.Matchers.containsString("scope=weave%3Amcp-backend"))))
                .andRespond(withSuccess("""
                        {"access_token":"delegated-token","issued_token_type":"urn:ietf:params:oauth:token-type:access_token","token_type":"Bearer","expires_in":60,"scope":"weave:mcp-backend"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCurrentMemberToken()).isEqualTo("delegated-token");
        server.verify();
    }

    @Test
    void rejectsClientCredentialsAndWrongAudienceBeforeCallingTheTokenEndpoint() {
        WeaveTokenExchangeClient client = client(new RestClientTokenExchangeTokenResponseClient());

        Jwt serviceToken = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject("2f802c16-24a5-471c-9312-7f5ace77dd04")
                .audience(List.of("weave-mcp-server"))
                .claim("azp", "weave-mcp-server")
                .claim("scope", "weave:mcp")
                .claim("preferred_username", "service-account-weave-mcp-server")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        authenticate(serviceToken);
        assertThatThrownBy(client::exchangeCurrentMemberToken)
                .isInstanceOf(McpBoundaryException.class)
                .hasMessage("mcp-member-subject-required");

        authenticate(memberToken("wrong-aud", "member-123", List.of("weave-backend"), "weave-app", "weave:mcp"));
        assertThatThrownBy(client::exchangeCurrentMemberToken)
                .isInstanceOf(McpBoundaryException.class)
                .hasMessage("mcp-token-audience-invalid");
    }

    private WeaveTokenExchangeClient client(RestClientTokenExchangeTokenResponseClient responseClient) {
        return new WeaveTokenExchangeClient(
                responseClient,
                "https://auth.weave.test/realms/weave/protocol/openid-connect/token",
                "weave-mcp-server",
                "test-secret",
                "weave-backend",
                "weave:mcp-backend",
                "weave-mcp-server",
                "weave-app",
                "weave:mcp");
    }

    private void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_weave:mcp"))));
    }

    private Jwt memberToken(String value, String subject, List<String> audience, String azp, String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(value)
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(subject)
                .audience(audience)
                .claim("azp", azp)
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
