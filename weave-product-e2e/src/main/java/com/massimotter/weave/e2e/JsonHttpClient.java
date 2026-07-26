package com.massimotter.weave.e2e;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Bounded HTTP/JSON client that verifies the isolated stack CA and never logs payloads. */
final class JsonHttpClient {
  private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

  private final HttpClient client;
  private final JsonMapper mapper;

  JsonHttpClient(Path caCertificate) {
    this.mapper = JsonMapper.builder().build();
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .sslContext(sslContext(caCertificate))
            .build();
  }

  JsonMapper mapper() {
    return mapper;
  }

  JsonNode json(
      String operation,
      String method,
      URI uri,
      Map<String, String> headers,
      JsonNode body,
      Set<Integer> expectedStatuses) {
    byte[] payload;
    try {
      payload = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
    } catch (JacksonException failure) {
      throw new ProductFlowException(operation + " request encoding failed", failure);
    }
    Response response =
        send(
            operation,
            method,
            uri,
            merge(headers, Map.of("Accept", "application/json")),
            body == null ? null : "application/json",
            payload,
            expectedStatuses);
    try {
      return mapper.readTree(response.body());
    } catch (RuntimeException failure) {
      throw new ProductFlowException(operation + " returned invalid JSON", failure);
    }
  }

  JsonNode form(
      String operation,
      URI uri,
      Map<String, String> fields,
      Set<Integer> expectedStatuses) {
    String encoded =
        fields.entrySet().stream()
            .map(
                entry ->
                    encode(entry.getKey())
                        + "="
                        + encode(entry.getValue() == null ? "" : entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    Response response =
        send(
            operation,
            "POST",
            uri,
            Map.of("Accept", "application/json"),
            "application/x-www-form-urlencoded",
            encoded.getBytes(StandardCharsets.UTF_8),
            expectedStatuses);
    try {
      return mapper.readTree(response.body());
    } catch (RuntimeException failure) {
      throw new ProductFlowException(operation + " returned invalid JSON", failure);
    }
  }

  Response send(
      String operation,
      String method,
      URI uri,
      Map<String, String> headers,
      String contentType,
      byte[] body,
      Set<Integer> expectedStatuses) {
    if (uri == null || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("HTTP target must not contain user information");
    }
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(body);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .method(method, publisher);
    headers.forEach(request::header);
    if (contentType != null) {
      request.header("Content-Type", contentType);
    }
    try {
      HttpResponse<byte[]> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.body().length > MAX_RESPONSE_BYTES) {
        throw new ProductFlowException(operation + " response exceeded the safe bound");
      }
      if (!expectedStatuses.contains(response.statusCode())) {
        throw new ProductFlowException(
            operation + " failed with HTTP " + response.statusCode());
      }
      return new Response(response.statusCode(), response.headers().map(), response.body());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException(operation + " was interrupted", interrupted);
    } catch (IOException failure) {
      throw new ProductFlowException(operation + " transport failed", failure);
    }
  }

  private static SSLContext sslContext(Path caCertificate) {
    try {
      byte[] bytes = Files.readAllBytes(caCertificate);
      Certificate certificate;
      try {
        certificate =
            CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(bytes));
      } finally {
        java.util.Arrays.fill(bytes, (byte) 0);
      }
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      trustStore.setCertificateEntry("weave-isolated-ca", certificate);
      TrustManagerFactory trustManagers =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagers.init(trustStore);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers.getTrustManagers(), null);
      return context;
    } catch (GeneralSecurityException | IOException failure) {
      throw new IllegalArgumentException("Unable to initialize the isolated CA trust", failure);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static Map<String, String> merge(
      Map<String, String> first, Map<String, String> second) {
    Map<String, String> result = new LinkedHashMap<>();
    result.putAll(first);
    result.putAll(second);
    return Map.copyOf(result);
  }

  record Response(int status, Map<String, java.util.List<String>> headers, byte[] body) {
    Response {
      headers = Map.copyOf(headers);
      body = body.clone();
    }

    @Override
    public byte[] body() {
      return body.clone();
    }

    String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }

    String firstHeader(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .flatMap(entry -> entry.getValue().stream())
          .findFirst()
          .orElse("");
    }
  }
}
