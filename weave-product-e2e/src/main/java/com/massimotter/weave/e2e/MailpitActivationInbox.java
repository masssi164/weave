package com.massimotter.weave.e2e;

import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads one-time action links only into memory from the isolated loopback Mailpit API. */
final class MailpitActivationInbox {
  private static final Pattern ACTION_LINK =
      Pattern.compile(
          "https?://[^\\s\"'<>]+/realms/[^\\s\"'<>]+/login-actions/action-token[^\\s\"'<>]*",
          Pattern.CASE_INSENSITIVE);

  private final JsonHttpClient http;
  private final URI api;
  private final URI issuer;
  private final Duration timeout;

  MailpitActivationInbox(JsonHttpClient http, URI api, URI issuer, Duration timeout) {
    this.http = http;
    this.api = api;
    this.issuer = issuer;
    this.timeout = timeout;
  }

  URI awaitActivationLink(String email, Instant notBefore) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      JsonNode messages =
          http.json(
              "read isolated activation inbox",
              "GET",
              endpoint("messages"),
              Map.of(),
              null,
              Set.of(200));
      for (JsonNode candidate : messageArray(messages)) {
        if (!containsText(candidate, normalizedEmail)
            || messageTime(candidate).map(value -> value.isBefore(notBefore)).orElse(false)) {
          continue;
        }
        String id = firstText(candidate, "ID", "Id", "id");
        if (id.isBlank()) {
          continue;
        }
        JsonNode message =
            http.json(
                "read isolated activation message",
                "GET",
                endpoint("message/" + encodeSegment(id)),
                Map.of(),
                null,
                Set.of(200));
        URI link = actionLink(message);
        if (link != null) {
          return link;
        }
      }
      sleep();
    }
    throw new ProductFlowException("activation mail did not converge within the bounded timeout");
  }

  private List<JsonNode> messageArray(JsonNode payload) {
    JsonNode value = payload.path("messages");
    if (!value.isArray() && payload.isArray()) {
      value = payload;
    }
    if (!value.isArray()) {
      throw new ProductFlowException("Mailpit messages response has an invalid shape");
    }
    List<JsonNode> result = new ArrayList<>();
    value.forEach(result::add);
    return List.copyOf(result);
  }

  private URI actionLink(JsonNode message) {
    List<String> values = new ArrayList<>();
    collectStrings(message, values);
    for (String raw : values) {
      String decoded =
          raw.replace("&amp;", "&")
              .replace("&#38;", "&")
              .replace("=3D", "=")
              .replace("=\r\n", "")
              .replace("=\n", "");
      Matcher matcher = ACTION_LINK.matcher(decoded);
      if (matcher.find()) {
        URI candidate = URI.create(matcher.group());
        if ("https".equalsIgnoreCase(candidate.getScheme())
            && candidate.getHost() != null
            && candidate.getHost().equalsIgnoreCase(issuer.getHost())
            && effectivePort(candidate) == effectivePort(issuer)
            && candidate
                .getPath()
                .startsWith(
                    issuer.getPath().replaceAll("/+$", "")
                        + "/login-actions/action-token")
            && candidate.getUserInfo() == null
            && candidate.getFragment() == null) {
          return candidate;
        }
      }
    }
    return null;
  }

  private static int effectivePort(URI uri) {
    return uri.getPort() >= 0 ? uri.getPort() : 443;
  }

  private static void collectStrings(JsonNode node, List<String> target) {
    if (node.isString()) {
      target.add(node.stringValue());
      return;
    }
    if (node.isArray()) {
      node.forEach(value -> collectStrings(value, target));
      return;
    }
    if (node.isObject()) {
      for (JsonNode value : node.values()) {
        collectStrings(value, target);
      }
    }
  }

  private static boolean containsText(JsonNode node, String expected) {
    if (node.isString()) {
      return node.stringValue().trim().equalsIgnoreCase(expected);
    }
    if (node.isArray()) {
      for (JsonNode value : node) {
        if (containsText(value, expected)) {
          return true;
        }
      }
      return false;
    }
    if (node.isObject()) {
      for (JsonNode value : node.values()) {
        if (containsText(value, expected)) {
          return true;
        }
      }
    }
    return false;
  }

  private static java.util.Optional<Instant> messageTime(JsonNode node) {
    for (String field : List.of("Created", "created", "Date", "date")) {
      String value = node.path(field).asString("").trim();
      if (!value.isEmpty()) {
        try {
          return java.util.Optional.of(Instant.parse(value));
        } catch (RuntimeException ignored) {
          // Mailpit versions differ in summary timestamp formatting; the run-unique address
          // remains the primary isolation key.
        }
      }
    }
    return java.util.Optional.empty();
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asString("").trim();
      if (!value.isEmpty()) {
        return value;
      }
    }
    return "";
  }

  private URI endpoint(String suffix) {
    return URI.create(api.toString().replaceAll("/+$", "") + "/" + suffix);
  }

  private static String encodeSegment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static void sleep() {
    try {
      Thread.sleep(1_000);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("activation inbox wait was interrupted", interrupted);
    }
  }
}
