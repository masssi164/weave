package com.massimotter.weave.keycloak.events;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

/** Emits only the support-safe fact needed to reconcile a Keycloak-owned organization join. */
public final class WeaveIdentityEventListenerProvider implements EventListenerProvider {
  private static final Logger LOG = Logger.getLogger(WeaveIdentityEventListenerProvider.class);
  private static final Pattern ORGANIZATION_MEMBERSHIP_PATH =
      Pattern.compile("(?:^|/)organizations/([^/]+)/(?:members|memberships)/([^/]+)(?:$|/)");

  private final KeycloakSession session;
  private final URI endpoint;
  private final byte[] signingSecret;
  private final Duration timeout;
  private final HttpClient httpClient;

  WeaveIdentityEventListenerProvider(
      KeycloakSession session,
      URI endpoint,
      byte[] signingSecret,
      Duration timeout,
      HttpClient httpClient) {
    this.session = session;
    this.endpoint = endpoint;
    this.signingSecret = signingSecret.clone();
    this.timeout = timeout;
    this.httpClient = httpClient;
  }

  @Override
  public void onEvent(Event event) {
    if (event.getError() != null || event.getUserId() == null) {
      return;
    }
    var details = event.getDetails() == null ? Map.<String, String>of() : event.getDetails();
    var organizationId = firstPresent(details, "organization_id", "organization", "org_id", "kc.org");
    if (organizationId == null || !isJoinEvent(event.getType().name())) {
      return;
    }
    emit(event.getRealmId(), organizationId, event.getUserId());
  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
    if (event.getError() != null || !"CREATE".equals(event.getOperationType().name())) {
      return;
    }
    var matcher = ORGANIZATION_MEMBERSHIP_PATH.matcher(event.getResourcePath());
    if (matcher.find()) {
      emit(event.getRealmId(), matcher.group(1), matcher.group(2));
    }
  }

  private static boolean isJoinEvent(String eventType) {
    return "REGISTER".equals(eventType)
        || "IDENTITY_PROVIDER_FIRST_LOGIN".equals(eventType)
        || "UPDATE_PROFILE".equals(eventType);
  }

  private void emit(String realmId, String organizationId, String userSubject) {
    try {
      var realm = session.realms().getRealm(realmId);
      var user = realm == null ? null : session.users().getUserById(realm, userSubject);
      var emailHash = user == null || !user.isEmailVerified() || user.getEmail() == null
          ? null
          : sha256(user.getEmail().strip().toLowerCase(java.util.Locale.ROOT));
      if (emailHash == null) {
        LOG.warn("Weave identity event omitted because the Keycloak user has no verified correlation email");
        return;
      }

      var eventId = UUID.randomUUID().toString();
      var occurredAt = Instant.now();
      var timestamp = occurredAt.toString();
      var body = "{" +
          "\"schemaVersion\":1," +
          "\"eventId\":\"" + json(eventId) + "\"," +
          "\"occurredAt\":\"" + json(occurredAt.toString()) + "\"," +
          "\"realmId\":\"" + json(realmId) + "\"," +
          "\"organizationId\":\"" + json(organizationId) + "\"," +
          "\"userSubject\":\"" + json(userSubject) + "\"," +
          "\"eventType\":\"organization_membership_added\"," +
          "\"invitedEmailHash\":\"" + emailHash + "\"}";

      var request = HttpRequest.newBuilder(endpoint)
          .timeout(timeout)
          .header("Content-Type", "application/json")
          .header("X-Weave-Event-Id", eventId)
          .header("X-Weave-Event-Timestamp", timestamp)
          .header("X-Weave-Event-Signature", hmac(timestamp + "." + body))
          .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
          .build();
      // Login/registration must not depend on the projection callback. Missed delivery is repaired
      // by Weave during the first authenticated bootstrap.
      httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
          .orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
          .thenAccept(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
              LOG.warnf("Weave identity event delivery failed support-safely with HTTP %d", response.statusCode());
            }
          })
          .exceptionally(failure -> {
            LOG.warnf("Weave identity event delivery failed support-safely: %s", failure.getClass().getSimpleName());
            return null;
          });
    } catch (Exception failure) {
      LOG.warnf("Weave identity event delivery failed support-safely: %s", failure.getClass().getSimpleName());
    }
  }

  private String hmac(String value) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String firstPresent(Map<String, String> details, String... keys) {
    for (var key : keys) {
      var value = details.get(key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Override
  public void close() {}
}
