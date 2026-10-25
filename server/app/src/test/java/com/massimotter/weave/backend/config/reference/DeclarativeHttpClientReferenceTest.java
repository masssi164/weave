package com.massimotter.weave.backend.config.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.ImportHttpServices;

/** Executable wiring example, not a replacement for the production Keycloak security contracts. */
class DeclarativeHttpClientReferenceTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpServiceClientPropertiesAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    HttpServiceClientAutoConfiguration.class))
            .withUserConfiguration(ClientComposition.class, MockTransports.class)
            .withPropertyValues(
                    "spring.http.serviceclient.keycloak-reference.base-url=https://identity.invalid",
                    "spring.http.serviceclient.public-reference.base-url=https://public.invalid");

    @Test
    void registeredClientsDoNotReadCredentialsOrContactProvidersDuringStartup() {
        runner.withBean(AccessTokens.class, () -> () -> {
            throw new AssertionError("Dormant clients must not resolve credentials during startup");
        }).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KeycloakAdminClient.class)
                    .hasSingleBean(PublicStatusClient.class);
            MockTransports transports = context.getBean(MockTransports.class);
            assertThat(transports.servers).containsOnlyKeys("keycloak-reference", "public-reference");
            transports.servers.values().forEach(MockRestServiceServer::verify);
        });
    }

    @Test
    void groupScopedAuthenticationReadsCurrentTokenAtRequestTimeAndDoesNotLeakToOtherClients() {
        AtomicReference<String> token = new AtomicReference<>("first-fixture-token");
        AtomicInteger reads = new AtomicInteger();
        runner.withBean(AccessTokens.class, () -> () -> {
            reads.incrementAndGet();
            return token.get();
        }).run(context -> {
            assertThat(context).hasNotFailed();
            MockTransports transports = context.getBean(MockTransports.class);
            MockRestServiceServer identity = transports.servers.get("keycloak-reference");
            identity.expect(requestTo("https://identity.invalid/admin/realms/weave/users/member-1"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer first-fixture-token"))
                    .andRespond(withSuccess("{\"id\":\"member-1\",\"enabled\":true}", MediaType.APPLICATION_JSON));
            identity.expect(requestTo("https://identity.invalid/admin/realms/weave/users/member-2"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer second-fixture-token"))
                    .andRespond(withSuccess("{\"id\":\"member-2\",\"enabled\":false}", MediaType.APPLICATION_JSON));
            MockRestServiceServer publicService = transports.servers.get("public-reference");
            publicService.expect(requestTo("https://public.invalid/ready"))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess("ready", MediaType.TEXT_PLAIN));

            assertThat(reads).hasValue(0);
            KeycloakAdminClient client = context.getBean(KeycloakAdminClient.class);
            assertThat(client.user("weave", "member-1")).isEqualTo(new User("member-1", true));
            token.set("second-fixture-token");
            assertThat(client.user("weave", "member-2")).isEqualTo(new User("member-2", false));
            assertThat(context.getBean(PublicStatusClient.class).readiness()).isEqualTo("ready");
            assertThat(reads).hasValue(2);
            transports.servers.values().forEach(MockRestServiceServer::verify);
        });
    }

    @Test
    void missingDormantCredentialDoesNotPreventStartupButBlocksTheRequestBeforeTransport() {
        runner.withBean(AccessTokens.class, () -> () -> {
            throw new IllegalStateException("Credential is not configured");
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(KeycloakAdminClient.class).user("weave", "member-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Credential is not configured");
            context.getBean(MockTransports.class).servers.values().forEach(MockRestServiceServer::verify);
        });
    }

    @Test
    void unauthorizedResponseIsNotImplicitlyRetried() {
        runner.withBean(AccessTokens.class, () -> () -> "fixture-token").run(context -> {
            MockRestServiceServer identity = context.getBean(MockTransports.class).servers.get("keycloak-reference");
            identity.expect(requestTo("https://identity.invalid/admin/realms/weave/users/member-1"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> context.getBean(KeycloakAdminClient.class).user("weave", "member-1"))
                    .isInstanceOf(RestClientResponseException.class)
                    .satisfies(failure -> assertThat(((RestClientResponseException) failure).getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED));
            identity.verify();
        });
    }

    @HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
    interface KeycloakAdminClient {
        @GetExchange("/admin/realms/{realm}/users/{subject}")
        User user(@PathVariable("realm") String realm, @PathVariable("subject") String subject);
    }

    interface PublicStatusClient {
        @GetExchange("/ready")
        String readiness();
    }

    public record User(String id, boolean enabled) {}

    @FunctionalInterface
    interface AccessTokens {
        String current();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ImportHttpServices(group = "keycloak-reference", types = KeycloakAdminClient.class)
    @ImportHttpServices(group = "public-reference", types = PublicStatusClient.class)
    static class ClientComposition {
        @Bean
        RestClientHttpServiceGroupConfigurer keycloakAuthentication(AccessTokens tokens) {
            return groups -> groups.filterByName("keycloak-reference").forEachClient((group, builder) ->
                    builder.requestInterceptor((request, body, execution) -> {
                        request.getHeaders().setBearerAuth(tokens.current());
                        return execution.execute(request, body);
                    }));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockTransports {
        private final Map<String, MockRestServiceServer> servers = new HashMap<>();

        @Bean
        RestClientHttpServiceGroupConfigurer isolatedTransports() {
            return groups -> groups.forEachClient((group, builder) -> {
                servers.put(group.name(), MockRestServiceServer.bindTo(builder).build());
            });
        }
    }
}
