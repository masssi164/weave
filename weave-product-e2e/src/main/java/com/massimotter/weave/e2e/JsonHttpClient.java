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
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Bounded HTTP/JSON client that verifies the isolated stack CA and never logs payloads. */
final class JsonHttpClient {
  private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

  private final HttpClient client;
  private final JsonMapper mapper;

  JsonHttpClient(Path caCertificate) {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .sslContext(sslContext(caCertificate))
            .build());
  }

  JsonHttpClient(HttpClient client) {
    this.mapper = JsonMapper.builder().build();
    this.client = java.util.Objects.requireNonNull(client, "client");
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
    return parseJson(operation, response);
  }

  JsonNode jsonRetryingDependencyUnavailable(
      String operation,
      String method,
      URI uri,
      Map<String, String> headers,
      JsonNode body,
      Set<Integer> expectedStatuses,
      int maxAttempts,
      Duration retryDelay) {
    if (maxAttempts < 1 || retryDelay == null || retryDelay.isNegative()) {
      throw new IllegalArgumentException("retry policy must be bounded and non-negative");
    }
    byte[] payload;
    try {
      payload = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
    } catch (JacksonException failure) {
      throw new ProductFlowException(operation + " request encoding failed", failure);
    }
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      Response response =
          send(
              operation,
              method,
              uri,
              merge(headers, Map.of("Accept", "application/json")),
              body == null ? null : "application/json",
              payload,
              union(expectedStatuses, Set.of(503)));
      if (expectedStatuses.contains(response.status())) {
        return parseJson(operation, response);
      }
      String code = safeErrorCode(response);
      if (!"agent-runtime-dependency-unavailable".equals(code)) {
        throw failure(operation, response);
      }
      if (attempt == maxAttempts) {
        throw new ProductFlowException(
            operation
                + " failed after "
                + maxAttempts
                + " attempts with HTTP 503 code=agent-runtime-dependency-unavailable");
      }
      try {
        Thread.sleep(retryDelay.toMillis());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new ProductFlowException(operation + " retry was interrupted", interrupted);
      }
    }
    throw new IllegalStateException("bounded retry loop completed without a response");
  }

  JsonNode jsonRetryingTransport(
      String operation,
      String method,
      URI uri,
      Map<String, String> headers,
      JsonNode body,
      Set<Integer> expectedStatuses,
      int maxAttempts,
      Duration retryDelay) {
    return executeBoundedTransport(
        operation,
        maxAttempts,
        retryDelay,
        () -> json(operation, method, uri, headers, body, expectedStatuses));
  }

  JsonNode jsonRetryingMatrixIdentityConflict(
      String operation,
      URI uri,
      Map<String, String> headers,
      int maxAttempts,
      Duration retryDelay) {
    if (maxAttempts < 1 || retryDelay == null || retryDelay.isNegative()) {
      throw new IllegalArgumentException("Matrix identity retry policy must be bounded and non-negative");
    }
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      Response response =
          send(
              operation,
              "GET",
              uri,
              merge(headers, Map.of("Accept", "application/json")),
              null,
              new byte[0],
              Set.of(200, 500));
      if (response.status() == 200) {
        return parseJson(operation, response);
      }
      String errcode = safeMatrixErrcode(response);
      if (!"M_UNKNOWN".equals(errcode)) {
        throw failure(operation, response);
      }
      if (attempt == maxAttempts) {
        throw new ProductFlowException(
            operation
                + " failed after "
                + maxAttempts
                + " attempts with HTTP 500 errcode=M_UNKNOWN");
      }
      sleep(operation + " Matrix identity retry", retryDelay);
    }
    throw new IllegalStateException("bounded Matrix identity retry loop completed without a response");
  }

  static JsonNode executeBoundedTransport(
      String operation,
      int maxAttempts,
      Duration retryDelay,
      Supplier<JsonNode> attempt) {
    if (maxAttempts < 1 || retryDelay == null || retryDelay.isNegative() || attempt == null) {
      throw new IllegalArgumentException("transport retry policy must be bounded and non-negative");
    }
    for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
      try {
        return attempt.get();
      } catch (ProductFlowException failure) {
        if (!(failure.getCause() instanceof IOException)) {
          throw failure;
        }
        if (attemptNumber == maxAttempts) {
          throw new ProductFlowException(
              operation + " transport did not become ready after " + maxAttempts + " attempts");
        }
        sleep(operation + " transport retry", retryDelay);
      }
    }
    throw new IllegalStateException("bounded transport retry loop completed without a response");
  }

  private String safeMatrixErrcode(Response response) {
    try {
      String errcode = mapper.readTree(response.body()).path("errcode").asString("");
      return errcode.matches("M_[A-Z0-9_]{1,79}") ? errcode : "";
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static void sleep(String operation, Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException(operation + " was interrupted", interrupted);
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

  private JsonNode parseJson(String operation, Response response) {
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
            operation
                + " failed with HTTP "
                + response.statusCode()
                + safeFailureReference(response));
      }
      return new Response(response.statusCode(), response.headers().map(), response.body());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException(operation + " was interrupted", interrupted);
    } catch (IOException failure) {
      throw new ProductFlowException(operation + " transport failed", failure);
    }
  }

  private String safeFailureReference(HttpResponse<byte[]> response) {
    StringBuilder reference = new StringBuilder();
    try {
      JsonNode error = mapper.readTree(response.body());
      appendSafeText(reference, " code=", error.path("code").asString(""), "[a-z0-9][a-z0-9-]{0,79}");
      JsonNode providerStatus = error.path("details").path("providerStatus");
      if (providerStatus.isIntegralNumber()
          && providerStatus.asInt() >= 100
          && providerStatus.asInt() <= 599) {
        reference.append(" providerStatus=").append(providerStatus.asInt());
      }
      appendSafeText(
          reference,
          " failureCategory=",
          error.path("details").path("failureCategory").asString(""),
          "[a-z0-9][a-z0-9-]{0,79}");
      appendSafeText(
          reference,
          " providerOperation=",
          error.path("details").path("providerOperation").asString(""),
          "[a-z0-9][a-z0-9-]{0,79}");
      appendSafeText(
          reference,
          " requestId=",
          error.path("requestId").asString(""),
          "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    } catch (RuntimeException ignored) {
      // Non-Weave or malformed payloads are deliberately omitted from diagnostics.
    }
    if (reference.indexOf(" code=") < 0) {
      response
          .headers()
          .firstValue("X-Weave-Error-Code")
          .ifPresent(
              code ->
                  appendSafeText(
                      reference,
                      " code=",
                      code,
                      "[a-z0-9][a-z0-9-]{0,79}"));
    }
    if (reference.indexOf("requestId=") < 0) {
      response
          .headers()
          .firstValue("X-Request-Id")
          .ifPresent(
              requestId ->
                  appendSafeText(
                      reference,
                      " requestId=",
                      requestId,
                      "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"));
    }
    return reference.toString();
  }

  private ProductFlowException failure(String operation, Response response) {
    String code = safeErrorCode(response);
    String suffix = code.isBlank() ? "" : " code=" + code;
    return new ProductFlowException(
        operation + " failed with HTTP " + response.status() + suffix);
  }

  private String safeErrorCode(Response response) {
    try {
      String code = mapper.readTree(response.body()).path("code").asString("");
      if (code.matches("[a-z0-9][a-z0-9-]{0,79}")) {
        return code;
      }
    } catch (RuntimeException ignored) {
      // A malformed or provider-owned body must not enter diagnostics or retry decisions.
    }
    String header = response.firstHeader("X-Weave-Error-Code");
    return header.matches("[a-z0-9][a-z0-9-]{0,79}") ? header : "";
  }

  private static void appendSafeText(
      StringBuilder target, String prefix, String value, String allowedPattern) {
    if (value != null && value.matches(allowedPattern)) {
      target.append(prefix).append(value);
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

  private static Set<Integer> union(Set<Integer> first, Set<Integer> second) {
    java.util.HashSet<Integer> result = new java.util.HashSet<>(first);
    result.addAll(second);
    return Set.copyOf(result);
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
