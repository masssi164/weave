package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JsonHttpClientTest {

  @Test
  void retriesOnlyTransportFailuresUntilTheReadOnlyProbeSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    tools.jackson.databind.JsonNode expected =
        tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode().put("ready", true);

    tools.jackson.databind.JsonNode result =
        JsonHttpClient.executeBoundedTransport(
            "verify OIDC transport after collaboration restart",
            3,
            Duration.ZERO,
            () -> {
              if (attempts.incrementAndGet() < 3) {
                throw new ProductFlowException("support-safe transport failure", new IOException());
              }
              return expected;
            });

    assertThat(result).isSameAs(expected);
    assertThat(attempts).hasValue(3);
  }

  @Test
  void doesNotRetryAnHttpOrSemanticFailure() {
    AtomicInteger attempts = new AtomicInteger();

    org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
        () ->
            JsonHttpClient.executeBoundedTransport(
                "verify OIDC transport after collaboration restart",
                3,
                Duration.ZERO,
                () -> {
                  attempts.incrementAndGet();
                  throw new ProductFlowException("failed with HTTP 503");
                });

    assertThat(org.assertj.core.api.Assertions.catchThrowable(call))
        .hasMessage("failed with HTTP 503");
    assertThat(attempts).hasValue(1);
  }

  @Test
  void stopsTransportReadinessAtTheConfiguredAttemptLimit() {
    AtomicInteger attempts = new AtomicInteger();

    org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
        () ->
            JsonHttpClient.executeBoundedTransport(
                "verify OIDC transport after collaboration restart",
                3,
                Duration.ZERO,
                () -> {
                  attempts.incrementAndGet();
                  throw new ProductFlowException("untrusted transport detail", new IOException());
                });

    assertThat(org.assertj.core.api.Assertions.catchThrowable(call))
        .hasMessage(
            "verify OIDC transport after collaboration restart transport did not become ready after 3 attempts")
        .hasMessageNotContaining("untrusted");
    assertThat(attempts).hasValue(3);
  }

  @Test
  void retriesOnlyTheBoundedAgentRuntimeDependencyFailure() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/reconcile",
        exchange -> {
          int attempt = attempts.incrementAndGet();
          byte[] body =
              (attempt < 3
                      ? "{\"code\":\"agent-runtime-dependency-unavailable\"}"
                      : "{\"status\":\"accepted\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(attempt < 3 ? 503 : 202, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      JsonHttpClient client = new JsonHttpClient(HttpClient.newHttpClient());

      tools.jackson.databind.JsonNode response =
          client.jsonRetryingDependencyUnavailable(
              "reconcile Agent Runtime entitlement",
              "POST",
              java.net.URI.create(
                  "http://127.0.0.1:" + server.getAddress().getPort() + "/reconcile"),
              Map.of("Idempotency-Key", "stable-key"),
              null,
              Set.of(202),
              3,
              Duration.ZERO);

      assertThat(response.path("status").asString()).isEqualTo("accepted");
      assertThat(attempts).hasValue(3);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void doesNotRetryAnotherServiceUnavailableFailure() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/reconcile",
        exchange -> {
          attempts.incrementAndGet();
          byte[] body =
              "{\"code\":\"another-dependency-unavailable\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(503, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      JsonHttpClient client = new JsonHttpClient(HttpClient.newHttpClient());

      org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
          () ->
              client.jsonRetryingDependencyUnavailable(
                  "reconcile Agent Runtime entitlement",
                  "POST",
                  java.net.URI.create(
                      "http://127.0.0.1:" + server.getAddress().getPort() + "/reconcile"),
                  Map.of(),
                  null,
                  Set.of(202),
                  3,
                  Duration.ZERO);

      assertThat(org.assertj.core.api.Assertions.catchThrowable(call))
          .hasMessage(
              "reconcile Agent Runtime entitlement failed with HTTP 503"
                  + " code=another-dependency-unavailable");
      assertThat(attempts).hasValue(1);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void stopsRetryingAtTheConfiguredAttemptLimit() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/reconcile",
        exchange -> {
          attempts.incrementAndGet();
          byte[] body =
              "{\"code\":\"agent-runtime-dependency-unavailable\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(503, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      JsonHttpClient client = new JsonHttpClient(HttpClient.newHttpClient());

      org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
          () ->
              client.jsonRetryingDependencyUnavailable(
                  "reconcile Agent Runtime entitlement",
                  "POST",
                  java.net.URI.create(
                      "http://127.0.0.1:" + server.getAddress().getPort() + "/reconcile"),
                  Map.of(),
                  null,
                  Set.of(202),
                  3,
                  Duration.ZERO);

      assertThat(org.assertj.core.api.Assertions.catchThrowable(call))
          .hasMessage(
              "reconcile Agent Runtime entitlement failed after 3 attempts with HTTP 503"
                  + " code=agent-runtime-dependency-unavailable");
      assertThat(attempts).hasValue(3);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportsOnlyAllowlistedFailureMetadata() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/failure",
        exchange -> {
          byte[] body =
              """
              {
                "code":"identity-administration-failed",
                "message":"secret provider response",
                "details":{
                  "providerStatus":403,
                  "failureCategory":"oauth2-client",
                  "providerOperation":"organization-inventory",
                  "providerPayload":"do-not-leak"
                },
                "requestId":"request-123"
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(502, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      JsonHttpClient client = new JsonHttpClient(HttpClient.newHttpClient());

      org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
          () ->
              client.send(
                  "identity operation",
                  "GET",
                  java.net.URI.create(
                      "http://127.0.0.1:" + server.getAddress().getPort() + "/failure"),
                  Map.of(),
                  null,
                  null,
                  Set.of(200));

      assertThat(org.assertj.core.api.Assertions.catchThrowable(call))
          .hasMessage(
              "identity operation failed with HTTP 502"
                  + " code=identity-administration-failed"
                  + " providerStatus=403"
                  + " failureCategory=oauth2-client"
                  + " providerOperation=organization-inventory"
                  + " requestId=request-123")
          .hasMessageNotContaining("secret")
          .hasMessageNotContaining("providerPayload")
          .hasMessageNotContaining("do-not-leak");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportsAllowlistedWebDavErrorHeaderWithoutParsingXmlPayload() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/failure",
        exchange -> {
          byte[] body =
              """
              <?xml version="1.0" encoding="UTF-8"?>
              <d:error xmlns:d="DAV:"><d:message>provider secret must not leak</d:message></d:error>
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("X-Weave-Error-Code", "files-storage-unavailable");
          exchange.sendResponseHeaders(503, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      JsonHttpClient client = new JsonHttpClient(HttpClient.newHttpClient());

      org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
          () ->
              client.send(
                  "WebDAV operation",
                  "PUT",
                  java.net.URI.create(
                      "http://127.0.0.1:" + server.getAddress().getPort() + "/failure"),
                  Map.of(),
                  "text/plain",
                  new byte[] {1},
                  Set.of(201));

      assertThat(org.assertj.core.api.Assertions.catchThrowable(call))
          .hasMessage(
              "WebDAV operation failed with HTTP 503 code=files-storage-unavailable")
          .hasMessageNotContaining("provider secret")
          .hasMessageNotContaining("message");
    } finally {
      server.stop(0);
    }
  }
}
