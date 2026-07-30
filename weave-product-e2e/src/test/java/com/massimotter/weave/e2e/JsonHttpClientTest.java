package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonHttpClientTest {

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
