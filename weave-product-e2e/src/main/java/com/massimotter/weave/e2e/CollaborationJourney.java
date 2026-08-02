package com.massimotter.weave.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Real, provider-backed, three-identity collaboration proof for one isolated stack. */
final class CollaborationJourney {
  private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
      PosixFilePermissions.fromString("rw-------");
  private static final String MATRIX_DEVICE_HEADER = "X-Weave-Matrix-Device-Id";
  private static final String MEGOLM = "m.megolm.v1.aes-sha2";
  private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration PROCESS_CLEANUP_TIMEOUT = Duration.ofSeconds(10);

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private RetainedRoom retainedFirstPass;

  CollaborationJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
  }

  PassProof runPass(
      int pass,
      OidcBrowserJourney.TokenSet author,
      OidcBrowserJourney.TokenSet collaborator,
      OidcBrowserJourney.TokenSet outsider,
      JsonNode authorClaims,
      JsonNode collaboratorClaims,
      JsonNode outsiderClaims) {
    if (pass < 1 || pass > 2) {
      throw new IllegalArgumentException("collaboration pass must be one or two");
    }
    Identity authorIdentity = identity(author, authorClaims, "author");
    Identity collaboratorIdentity = identity(collaborator, collaboratorClaims, "collaborator");
    Identity outsiderIdentity = identity(outsider, outsiderClaims, "outsider");
    requireDistinct(authorIdentity, collaboratorIdentity, outsiderIdentity);

    String suffix = runHash() + "-p" + pass;
    String fileName = "collaboration-" + suffix + ".txt";
    String eventUid = "collaboration-" + suffix;
    String roomId = null;
    String authorEventId = null;
    String collaboratorEventId = null;
    String outageEventId = null;
    boolean fileCreated = false;
    boolean calendarCreated = false;
    boolean restartContinuityVerified = false;
    try {
      MatrixIdentity collaboratorMatrix = matrixIdentity(collaboratorIdentity, pass);
      matrixIdentity(outsiderIdentity, pass);
      MatrixIdentity authorMatrix = matrixIdentity(authorIdentity, pass);
      if (pass == 2) {
        restartContinuityVerified =
            verifyAndCleanRetainedFirstPass(
                authorIdentity, collaboratorIdentity, outsiderIdentity, pass);
      }
      roomId = createEncryptedRoom(authorIdentity, collaboratorMatrix.userId(), pass);
      joinRoom(collaboratorIdentity, roomId, pass);

      String authorCiphertext = ciphertext("author", pass);
      String collaboratorCiphertext = ciphertext("collaborator", pass);
      authorEventId = sendEncrypted(authorIdentity, roomId, authorCiphertext, "author", pass);
      requireCiphertextObserved(collaboratorIdentity, roomId, authorCiphertext, "author", pass);
      collaboratorEventId =
          sendEncrypted(collaboratorIdentity, roomId, collaboratorCiphertext, "collaborator", pass);
      requireCiphertextObserved(authorIdentity, roomId, collaboratorCiphertext, "collaborator", pass);
      requireMatrixDenied(outsiderIdentity, roomId, pass);
      OutageProof outage =
          proveProviderOutageRecovery(authorIdentity, authorMatrix.userId(), roomId, pass);
      outageEventId = outage.eventId();

      String initialFile = "initial-" + Hashing.sha256(suffix).substring(0, 24);
      String updatedFile = "updated-" + Hashing.sha256(suffix).substring(0, 24);
      String initialEtag = createFile(authorIdentity, fileName, initialFile);
      fileCreated = true;
      requireBody(collaboratorIdentity, "/dav/files/" + encode(fileName), initialFile, "shared file");
      updateFile(collaboratorIdentity, fileName, initialEtag, updatedFile);
      requireBody(authorIdentity, "/dav/files/" + encode(fileName), updatedFile, "updated shared file");
      requireWebDavDenied(outsiderIdentity, fileName, pass);

      String initialCalendar = calendar(eventUid, "Initial " + suffix);
      String updatedCalendar = calendar(eventUid, "Updated " + suffix);
      String calendarEtag = createCalendar(authorIdentity, eventUid, initialCalendar);
      calendarCreated = true;
      requireCalendar(
          collaboratorIdentity,
          "/caldav/workspace/" + encode(eventUid) + ".ics",
          initialCalendar,
          "shared calendar event");
      updateCalendar(authorIdentity, eventUid, calendarEtag, updatedCalendar);
      requireCalendar(
          collaboratorIdentity,
          "/caldav/workspace/" + encode(eventUid) + ".ics",
          updatedCalendar,
          "updated shared calendar event");
      requireCalendarDenied(outsiderIdentity, eventUid, pass);

      proveProfileIsolation(pass, authorIdentity, collaboratorIdentity, outsiderIdentity);
      proveHomeProjection(authorIdentity, collaboratorIdentity, outsiderIdentity);

      JsonNode providerBeforeReplay =
          awaitProviderProof(
              roomId,
              authorIdentity,
              collaboratorIdentity,
              outsiderIdentity,
              List.of(
                  Hashing.sha256(authorCiphertext),
                  Hashing.sha256(collaboratorCiphertext),
                  Hashing.sha256(outage.ciphertext())));
      JsonNode callbackReplay = replayCapturedCallback();
      JsonNode providerProof =
          awaitProviderProof(
              roomId,
              authorIdentity,
              collaboratorIdentity,
              outsiderIdentity,
              List.of(
                  Hashing.sha256(authorCiphertext),
                  Hashing.sha256(collaboratorCiphertext),
                  Hashing.sha256(outage.ciphertext())));
      validateProviderProof(providerBeforeReplay, providerProof, callbackReplay);

      if (pass == 1) {
        retainedFirstPass =
            new RetainedRoom(
                roomId,
                authorEventId,
                collaboratorEventId,
                outageEventId,
                List.of(authorCiphertext, collaboratorCiphertext, outage.ciphertext()),
                List.of(
                    Hashing.sha256(authorCiphertext),
                    Hashing.sha256(collaboratorCiphertext),
                    Hashing.sha256(outage.ciphertext())));
        roomId = null;
        authorEventId = null;
        collaboratorEventId = null;
        outageEventId = null;
      } else {
        cleanRoomStrict(
            authorIdentity,
            collaboratorIdentity,
            roomId,
            authorEventId,
            collaboratorEventId,
            outageEventId,
            pass);
        roomId = null;
      }
      deleteStrict(authorIdentity, "/caldav/workspace/" + encode(eventUid) + ".ics", "calendar event");
      calendarCreated = false;
      deleteStrict(authorIdentity, "/dav/files/" + encode(fileName), "file");
      fileCreated = false;

      return new PassProof(
          pass,
          authorIdentity.referenceHash(),
          collaboratorIdentity.referenceHash(),
          outsiderIdentity.referenceHash(),
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          true,
          restartContinuityVerified,
          true,
          true,
          providerProof.path("correlationHash").asString());
    } finally {
      if (roomId != null) {
        redactBestEffort(authorIdentity, roomId, authorEventId, "author", pass);
        redactBestEffort(collaboratorIdentity, roomId, collaboratorEventId, "collaborator", pass);
        redactBestEffort(authorIdentity, roomId, outageEventId, "outage", pass);
        leaveBestEffort(collaboratorIdentity, roomId, pass);
        leaveBestEffort(authorIdentity, roomId, pass);
      }
      if (calendarCreated) {
        deleteBestEffort(
            authorIdentity,
            "/caldav/workspace/" + encode(eventUid) + ".ics",
            "calendar event");
      }
      if (fileCreated) {
        deleteBestEffort(authorIdentity, "/dav/files/" + encode(fileName), "file");
      }
    }
  }

  void restartCollaborationServices() {
    if (retainedFirstPass == null) {
      throw new ProductFlowException("first collaboration pass was not retained for restart proof");
    }
    control("collaboration-restart-proof", "WEAVE_COLLABORATION_RESTART_RESULT");
  }

  private boolean verifyAndCleanRetainedFirstPass(
      Identity author, Identity collaborator, Identity outsider, int pass) {
    RetainedRoom room = retainedFirstPass;
    if (room == null) {
      throw new ProductFlowException("restart continuity has no retained first-pass room");
    }
    for (String ciphertext : room.ciphertexts()) {
      requireCiphertextObserved(author, room.roomId(), ciphertext, "restart", pass);
      requireCiphertextObserved(collaborator, room.roomId(), ciphertext, "restart", pass);
    }
    requireMatrixDenied(outsider, room.roomId(), pass);
    JsonNode proof =
        awaitProviderProof(
            room.roomId(), author, collaborator, outsider, room.correlationHashes());
    validateStableProviderProof(proof);
    cleanRoomStrict(
        author,
        collaborator,
        room.roomId(),
        room.authorEventId(),
        room.collaboratorEventId(),
        room.outageEventId(),
        pass);
    retainedFirstPass = null;
    return true;
  }

  private Identity identity(
      OidcBrowserJourney.TokenSet session, JsonNode claims, String role) {
    String issuer = claims.path("iss").asString();
    String subject = claims.path("sub").asString();
    if (issuer.isBlank()
        || subject.isBlank()
        || !issuer.equals(environment.issuer().toString())) {
      throw new ProductFlowException(role + " collaboration identity claims are incomplete");
    }
    return new Identity(
        role,
        session.accessToken(),
        issuer,
        environment.tenantId(),
        "user:" + subject,
        "sha256:" + Hashing.sha256(issuer + "\u0000" + subject));
  }

  private static void requireDistinct(Identity... identities) {
    Set<String> hashes =
        java.util.Arrays.stream(identities)
            .map(Identity::referenceHash)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (hashes.size() != identities.length) {
      throw new ProductFlowException("collaboration identities are not distinct");
    }
    Set<String> tenants =
        java.util.Arrays.stream(identities)
            .map(Identity::tenant)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (tenants.size() != 1) {
      throw new ProductFlowException("collaboration identities do not share one isolated tenant");
    }
  }

  private MatrixIdentity matrixIdentity(Identity identity, int pass) {
    JsonNode response =
        http.json(
            "register " + identity.role() + " Matrix facade identity",
            "GET",
            environment.api("/_matrix/client/v3/account/whoami"),
            bearer(
                identity.token(),
                Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            null,
            Set.of(200));
    String userId = response.path("user_id").asString();
    String deviceId = response.path("device_id").asString();
    if (!userId.matches("@[A-Za-z0-9._=/-]+:[A-Za-z0-9.:-]+")
        || !deviceId.equals(deviceId(identity.role(), pass))) {
      throw new ProductFlowException(identity.role() + " Matrix identity is invalid");
    }
    return new MatrixIdentity(userId, deviceId);
  }

  private String createEncryptedRoom(Identity author, String collaboratorUserId, int pass) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("name", "Weave isolated collaboration " + runHash() + " pass " + pass);
    ArrayNode invite = request.putArray("invite");
    invite.add(collaboratorUserId);
    ArrayNode state = request.putArray("initial_state");
    ObjectNode encryption = state.addObject();
    encryption.put("type", "m.room.encryption");
    encryption.put("state_key", "");
    encryption.putObject("content").put("algorithm", MEGOLM);
    JsonNode response =
        http.json(
            "create encrypted shared room",
            "POST",
            environment.api("/_matrix/client/v3/createRoom"),
            bearer(
                author.token(),
                Map.of(
                    MATRIX_DEVICE_HEADER, deviceId(author.role(), pass),
                    "Idempotency-Key", "test-app-room-" + runHash() + "-" + pass)),
            request,
            Set.of(200));
    String roomId = response.path("room_id").asString();
    if (!roomId.matches("![^:]{1,200}:[A-Za-z0-9.:-]+")) {
      throw new ProductFlowException("Matrix room projection is invalid");
    }
    return roomId;
  }

  private void joinRoom(Identity identity, String roomId, int pass) {
    JsonNode response =
        http.json(
            "join invited collaborator",
            "POST",
            environment.api("/_matrix/client/v3/join/" + encode(roomId)),
            bearer(identity.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            http.mapper().createObjectNode(),
            Set.of(200));
    if (!roomId.equals(response.path("room_id").asString())) {
      throw new ProductFlowException("Matrix collaborator join did not converge");
    }
  }

  private String sendEncrypted(
      Identity identity, String roomId, String ciphertext, String actor, int pass) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("algorithm", MEGOLM);
    request.put("ciphertext", ciphertext);
    request.put("sender_key", "curve25519:" + Hashing.sha256(actor + runHash()).substring(0, 24));
    request.put("session_id", "session-" + runHash() + "-" + pass);
    request.put("device_id", deviceId(identity.role(), pass));
    String transaction = "test-app-" + actor + "-" + runHash() + "-" + pass;
    JsonNode response =
        http.json(
            "send opaque encrypted " + actor + " event",
            "PUT",
            environment.api(
                "/_matrix/client/v3/rooms/"
                    + encode(roomId)
                    + "/send/m.room.encrypted/"
                    + encode(transaction)),
            bearer(identity.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            request,
            Set.of(200));
    String eventId = response.path("event_id").asString();
    if (!eventId.startsWith("$") || eventId.length() > 512) {
      throw new ProductFlowException("Matrix encrypted event projection is invalid");
    }
    return eventId;
  }

  private void requireCiphertextObserved(
      Identity observer, String roomId, String ciphertext, String actor, int pass) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    while (Instant.now().isBefore(deadline)) {
      JsonNode response =
          http.json(
              "observe encrypted " + actor + " event",
              "GET",
              environment.api(
                  "/_matrix/client/v3/rooms/" + encode(roomId) + "/messages?limit=100"),
              bearer(
                  observer.token(),
                  Map.of(MATRIX_DEVICE_HEADER, deviceId(observer.role(), pass))),
              null,
              Set.of(200));
      for (JsonNode event : response.path("chunk")) {
        if ("m.room.encrypted".equals(event.path("type").asString())
            && ciphertext.equals(event.path("content").path("ciphertext").asString())
            && !event.path("content").has("body")) {
          return;
        }
      }
      sleep();
    }
    throw new ProductFlowException("encrypted " + actor + " event did not converge");
  }

  private void requireMatrixDenied(Identity outsider, String roomId, int pass) {
    http.send(
        "deny outsider Matrix room read",
        "GET",
        environment.api("/_matrix/client/v3/rooms/" + encode(roomId) + "/messages?limit=10"),
        bearer(outsider.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(outsider.role(), pass))),
        null,
        null,
        Set.of(403));
    ObjectNode encrypted = http.mapper().createObjectNode();
    encrypted.put("algorithm", MEGOLM);
    encrypted.put("ciphertext", ciphertext("outsider", pass));
    encrypted.put("sender_key", "curve25519:outsider");
    encrypted.put("session_id", "outsider-denied");
    encrypted.put("device_id", deviceId(outsider.role(), pass));
    http.send(
        "deny outsider Matrix room write",
        "PUT",
        environment.api(
            "/_matrix/client/v3/rooms/"
                + encode(roomId)
                + "/send/m.room.encrypted/outsider-denied-"
                + pass),
        bearer(outsider.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(outsider.role(), pass))),
        "application/json",
        jsonBytes(encrypted),
        Set.of(403));
  }

  private OutageProof proveProviderOutageRecovery(
      Identity author, String authorMatrixUserId, String roomId, int pass) {
    String ciphertext = ciphertext("outage", pass);
    ObjectNode payload = encryptedPayload(author, ciphertext, "outage", pass);
    String transaction = "test-app-outage-" + runHash() + "-" + pass;
    boolean providerStopped = false;
    try {
      control("chat-provider-stop-proof", "WEAVE_CHAT_PROVIDER_CONTROL_RESULT state=stopped");
      providerStopped = true;
      JsonNode platform =
          http.json(
              "keep platform configuration reachable during Chat outage",
              "GET",
              environment.api("/api/platform/config"),
              Map.of(),
              null,
              Set.of(200));
      if (platform.isMissingNode() || platform.isNull()) {
        throw new ProductFlowException("platform configuration failed during Chat outage");
      }
      http.json(
          "keep authenticated member surface reachable during Chat outage",
          "GET",
          environment.api("/api/me"),
          bearer(author.token(), Map.of()),
          null,
          Set.of(200));
      JsonHttpClient.Response unavailable =
          http.send(
              "reject Chat send while Synapse is unavailable",
              "PUT",
              sendUri(roomId, transaction),
              bearer(
                  author.token(),
                  Map.of(MATRIX_DEVICE_HEADER, deviceId(author.role(), pass))),
              "application/json",
              jsonBytes(payload),
              Set.of(429, 503));
      String diagnostic = unavailable.bodyText();
      if (diagnostic.contains(ciphertext)
          || diagnostic.contains("Authorization")
          || diagnostic.contains("http://")
          || diagnostic.contains("https://")) {
        throw new ProductFlowException("Chat outage response was not support-safe");
      }
    } finally {
      if (providerStopped) {
        control("chat-provider-start-proof", "WEAVE_CHAT_PROVIDER_CONTROL_RESULT state=healthy");
      }
    }
    awaitProviderBackoffRecovery(author, authorMatrixUserId, roomId, pass);
    String first = sendEncryptedPayload(author, roomId, transaction, payload, pass);
    String repeated = sendEncryptedPayload(author, roomId, transaction, payload, pass);
    if (!first.equals(repeated)) {
      throw new ProductFlowException("Chat outage retry was not exactly once");
    }
    requireCiphertextObserved(author, roomId, ciphertext, "outage", pass);
    return new OutageProof(first, ciphertext);
  }

  private void awaitProviderBackoffRecovery(
      Identity author, String authorMatrixUserId, String roomId, int pass) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    ObjectNode request = http.mapper().createObjectNode();
    request.put("typing", false);
    request.put("timeout", 0);
    while (Instant.now().isBefore(deadline)) {
      try {
        http.json(
            "observe Chat provider recovery",
            "PUT",
            environment.api(
                "/_matrix/client/v3/rooms/"
                    + encode(roomId)
                    + "/typing/"
                    + encode(authorMatrixUserId)),
            bearer(
                author.token(),
                Map.of(MATRIX_DEVICE_HEADER, deviceId(author.role(), pass))),
            request,
            Set.of(200));
        return;
      } catch (ProductFlowException unavailable) {
        sleep();
      }
    }
    throw new ProductFlowException("Chat provider recovery did not converge");
  }

  private ObjectNode encryptedPayload(
      Identity identity, String ciphertext, String actor, int pass) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("algorithm", MEGOLM);
    request.put("ciphertext", ciphertext);
    request.put("sender_key", "curve25519:" + Hashing.sha256(actor + runHash()).substring(0, 24));
    request.put("session_id", "session-" + runHash() + "-" + pass);
    request.put("device_id", deviceId(identity.role(), pass));
    return request;
  }

  private String sendEncryptedPayload(
      Identity identity, String roomId, String transaction, ObjectNode request, int pass) {
    JsonNode response =
        http.json(
            "send opaque encrypted event",
            "PUT",
            sendUri(roomId, transaction),
            bearer(
                identity.token(),
                Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            request,
            Set.of(200));
    String eventId = response.path("event_id").asString();
    if (!eventId.startsWith("$") || eventId.length() > 512) {
      throw new ProductFlowException("Matrix encrypted event projection is invalid");
    }
    return eventId;
  }

  private URI sendUri(String roomId, String transaction) {
    return environment.api(
        "/_matrix/client/v3/rooms/"
            + encode(roomId)
            + "/send/m.room.encrypted/"
            + encode(transaction));
  }

  private String createFile(Identity author, String fileName, String content) {
    JsonHttpClient.Response response =
        http.send(
            "create shared WebDAV file",
            "PUT",
            environment.api("/dav/files/" + encode(fileName)),
            bearer(author.token(), Map.of("If-None-Match", "*")),
            "text/plain; charset=utf-8",
            content.getBytes(StandardCharsets.UTF_8),
            Set.of(201));
    return requireEtag(response, "created WebDAV file");
  }

  private void updateFile(Identity collaborator, String fileName, String etag, String content) {
    JsonHttpClient.Response response =
        http.send(
            "update shared WebDAV file",
            "PUT",
            environment.api("/dav/files/" + encode(fileName)),
            bearer(collaborator.token(), Map.of("If-Match", etag)),
            "text/plain; charset=utf-8",
            content.getBytes(StandardCharsets.UTF_8),
            Set.of(204));
    requireEtag(response, "updated WebDAV file");
  }

  private void requireWebDavDenied(Identity outsider, String fileName, int pass) {
    http.send(
        "deny outsider WebDAV read",
        "GET",
        environment.api("/dav/files/" + encode(fileName)),
        bearer(outsider.token(), Map.of()),
        null,
        null,
        Set.of(403, 404));
    http.send(
        "deny outsider WebDAV write",
        "PUT",
        environment.api("/dav/files/outsider-denied-" + pass + ".txt"),
        bearer(outsider.token(), Map.of("If-None-Match", "*")),
        "text/plain; charset=utf-8",
        "denied".getBytes(StandardCharsets.UTF_8),
        Set.of(403));
  }

  private String createCalendar(Identity author, String uid, String content) {
    JsonHttpClient.Response response =
        http.send(
            "create shared CalDAV event",
            "PUT",
            environment.api("/caldav/workspace/" + encode(uid) + ".ics"),
            bearer(author.token(), Map.of("If-None-Match", "*")),
            "text/calendar; charset=utf-8",
            content.getBytes(StandardCharsets.UTF_8),
            Set.of(201));
    return requireEtag(response, "created CalDAV event");
  }

  private void updateCalendar(
      Identity collaborator, String uid, String etag, String content) {
    JsonHttpClient.Response response =
        http.send(
            "update shared CalDAV event",
            "PUT",
            environment.api("/caldav/workspace/" + encode(uid) + ".ics"),
            bearer(collaborator.token(), Map.of("If-Match", etag)),
            "text/calendar; charset=utf-8",
            content.getBytes(StandardCharsets.UTF_8),
            Set.of(204));
    requireEtag(response, "updated CalDAV event");
  }

  private void requireCalendarDenied(Identity outsider, String uid, int pass) {
    http.send(
        "deny outsider CalDAV read",
        "GET",
        environment.api("/caldav/workspace/" + encode(uid) + ".ics"),
        bearer(outsider.token(), Map.of()),
        null,
        null,
        Set.of(403, 404));
    String outsiderUid = "outsider-denied-" + runHash() + "-" + pass;
    http.send(
        "deny outsider CalDAV write",
        "PUT",
        environment.api("/caldav/workspace/" + outsiderUid + ".ics"),
        bearer(outsider.token(), Map.of("If-None-Match", "*")),
        "text/calendar; charset=utf-8",
        calendar(outsiderUid, "Denied").getBytes(StandardCharsets.UTF_8),
        Set.of(403));
  }

  private void requireBody(
      Identity identity, String path, String expected, String operation) {
    JsonHttpClient.Response response =
        http.send(
            "read " + operation,
            "GET",
            environment.api(path),
            bearer(identity.token(), Map.of()),
            null,
            null,
            Set.of(200));
    if (!expected.equals(response.bodyText())) {
      throw new ProductFlowException(operation + " did not match exactly");
    }
  }

  private void requireCalendar(
      Identity identity, String path, String expected, String operation) {
    JsonHttpClient.Response response =
        http.send(
            "read " + operation,
            "GET",
            environment.api(path),
            bearer(identity.token(), Map.of()),
            null,
            null,
            Set.of(200));
    IcalendarProjectionAssertions.requireWorkspaceProjection(
        expected, response.bodyText(), operation);
  }

  private void proveProfileIsolation(
      int pass, Identity author, Identity collaborator, Identity outsider) {
    Map<String, String> observed = new LinkedHashMap<>();
    for (Identity identity : List.of(author, collaborator, outsider)) {
      ObjectNode update = http.mapper().createObjectNode();
      String displayName = "Weave " + identity.role() + " " + runHash() + " " + pass;
      update.put("displayName", displayName);
      update.put("locale", "collaborator".equals(identity.role()) ? "de" : "en");
      update.put("timezone", "Europe/Berlin");
      update.put("profileVisibility", "private");
      update.putObject("accessibilityPreferences").put("reducedMotion", "true");
      JsonNode updated =
          http.json(
              "update " + identity.role() + " product profile",
              "PATCH",
              environment.api("/api/profile"),
              bearer(identity.token(), Map.of()),
              update,
              Set.of(200));
      if (!displayName.equals(updated.path("displayName").asString())
          || !"true".equals(
              updated.path("accessibilityPreferences").path("reducedMotion").asString())) {
        throw new ProductFlowException(identity.role() + " profile update did not persist");
      }
      JsonNode current =
          http.json(
              "read " + identity.role() + " product profile",
              "GET",
              environment.api("/api/profile"),
              bearer(identity.token(), Map.of()),
              null,
              Set.of(200));
      observed.put(identity.role(), current.path("userId").asString());
      if (!displayName.equals(current.path("displayName").asString())) {
        throw new ProductFlowException(identity.role() + " profile isolation did not persist");
      }
    }
    if (observed.values().stream().anyMatch(String::isBlank)
        || Set.copyOf(observed.values()).size() != 3) {
      throw new ProductFlowException("product profile identities are not isolated");
    }
  }

  private void proveHomeProjection(Identity author, Identity collaborator, Identity outsider) {
    for (Identity identity : List.of(author, collaborator)) {
      JsonNode home =
          http.json(
              "read shared Home activity as " + identity.role(),
              "GET",
              environment.api("/api/workspace/home"),
              bearer(identity.token(), Map.of()),
              null,
              Set.of(200));
      if (!home.path("supportSafe").asBoolean(false)
          || !home.path("recentActivity").isArray()
          || home.path("recentActivity").isEmpty()) {
        throw new ProductFlowException("shared Home activity did not converge");
      }
    }
    JsonNode outsiderHome =
        http.json(
            "filter outsider Home projection",
            "GET",
            environment.api("/api/workspace/home"),
            bearer(outsider.token(), Map.of()),
            null,
            Set.of(200));
    if (!outsiderHome.path("supportSafe").asBoolean(false)
        || !outsiderHome.path("recentActivity").isArray()
        || !outsiderHome.path("recentActivity").isEmpty()) {
      throw new ProductFlowException("outsider Home activity was not filtered");
    }
  }

  private JsonNode replayCapturedCallback() {
    Map<String, String> proofHeaders = proofHeaders();
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    while (Instant.now().isBefore(deadline)) {
      JsonNode readiness =
          http.json(
              "read callback replay readiness",
              "GET",
              proof("/api/internal/e2e/chat/provider-proof/callback-replay/readiness"),
              proofHeaders,
              null,
              Set.of(200));
      if (readiness.path("callbackReplayReady").asBoolean(false)) {
        ObjectNode request = http.mapper().createObjectNode();
        request.put("runId", environment.runId());
        JsonNode replay =
            http.json(
                "replay one genuine provider callback",
                "POST",
                proof("/api/internal/e2e/chat/provider-proof/callback-replay"),
                proofHeaders,
                request,
                Set.of(200));
        if (replay.path("replayed").asBoolean(false)
            && replay.path("supportSafe").asBoolean(false)) {
          return replay;
        }
      }
      sleep();
    }
    throw new ProductFlowException("provider callback replay did not become ready");
  }

  private JsonNode awaitProviderProof(
      String roomId,
      Identity author,
      Identity collaborator,
      Identity outsider,
      List<String> correlations) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("runId", environment.runId());
    request.put("tenantId", author.tenant());
    request.put("conversationId", conversationId(roomId));
    identityNode(request.putObject("author"), author);
    identityNode(request.putObject("collaborator"), collaborator);
    identityNode(request.putObject("outsider"), outsider);
    ArrayNode values = request.putArray("eventCorrelationSha256");
    correlations.forEach(values::add);
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    ProductFlowException lastFailure = null;
    while (Instant.now().isBefore(deadline)) {
      try {
        return http.json(
            "prove canonical and direct Synapse collaboration",
            "POST",
            proof("/api/internal/e2e/chat/provider-proof"),
            proofHeaders(),
            request,
            Set.of(200));
      } catch (ProductFlowException failure) {
        lastFailure = failure;
        sleep();
      }
    }
    throw new ProductFlowException("provider collaboration proof did not converge", lastFailure);
  }

  private static void identityNode(ObjectNode target, Identity identity) {
    target.put("identityIssuer", identity.issuer());
    target.put("actorRef", identity.actorRef());
  }

  private void validateProviderProof(JsonNode before, JsonNode proof, JsonNode replay) {
    boolean valid = stableProviderProofValid(proof)
            && proof.path("providerCapabilityState").asString().equals("available")
            && proof.path("providerConsecutiveFailures").asInt(-1) == 0
            && proof.path("providerObservationAgeSeconds").asLong(-1)
                <= environment.convergenceTimeout().toSeconds()
            && proof.path("callbackDuplicateCount").asLong()
                == before.path("callbackDuplicateCount").asLong(-1) + 1
            && proof.path("canonicalCommittedEventCount").asLong()
                == before.path("canonicalCommittedEventCount").asLong(-1)
            && proof.path("providerEncryptedEventCount").asLong()
                == before.path("providerEncryptedEventCount").asLong(-1)
            && proof.path("bridgeLedgerCount").asLong()
                == before.path("bridgeLedgerCount").asLong(-1)
            && proof.path("callbackTransactionCount").asLong()
                == before.path("callbackTransactionCount").asLong(-1)
            && replay.path("callbackCorrelationHash").asString().matches("[0-9a-f]{64}");
    if (!valid || !identitiesValid(proof)) {
      throw new ProductFlowException("canonical and direct Synapse collaboration proof is incomplete");
    }
  }

  private void validateStableProviderProof(JsonNode proof) {
    if (!stableProviderProofValid(proof) || !identitiesValid(proof)) {
      throw new ProductFlowException("post-restart provider collaboration proof is incomplete");
    }
  }

  private boolean identitiesValid(JsonNode proof) {
    boolean identitiesValid = proof.path("identities").isArray() && proof.path("identities").size() == 3;
    for (JsonNode identity : proof.path("identities")) {
      String role = identity.path("role").asString();
      if ("outsider".equals(role)) {
        identitiesValid &=
            !identity.path("providerMapped").asBoolean(true)
                && !identity.path("canonicalJoined").asBoolean(true)
                && !identity.path("providerJoined").asBoolean(true)
                && identity.path("providerReadDenied").asBoolean(false);
      } else if (Set.of("author", "collaborator").contains(role)) {
        identitiesValid &=
            identity.path("providerMapped").asBoolean(false)
                && identity.path("canonicalJoined").asBoolean(false)
                && identity.path("providerJoined").asBoolean(false)
                && !identity.path("providerReadDenied").asBoolean(true);
      } else {
        identitiesValid = false;
      }
    }
    return identitiesValid;
  }

  private boolean stableProviderProofValid(JsonNode proof) {
    return
        "chat-provider-proof-v1".equals(proof.path("contractVersion").asString())
            && proof.path("adapterConfigured").asBoolean(false)
            && "durable-relational-jpa-code-first".equals(proof.path("canonicalStorage").asString())
            && proof.path("providerCapabilityAvailable").asBoolean(false)
            && proof.path("providerMembershipExact").asBoolean(false)
            && proof.path("outsiderAbsent").asBoolean(false)
            && proof.path("outsiderReadDenied").asBoolean(false)
            && proof.path("providerEncryptionStateVerified").asBoolean(false)
            && proof.path("providerEventMappingExact").asBoolean(false)
            && proof.path("providerCiphertextCorrelationExact").asBoolean(false)
            && proof.path("canonicalConversationCount").asLong() == 1
            && proof.path("canonicalJoinedMemberCount").asLong() == 2
            && proof.path("canonicalEncryptedEventCount").asLong() == 3
            && proof.path("canonicalPlaintextEventCount").asLong(-1) == 0
            && proof.path("providerEncryptedEventCount").asLong() == 3
            && proof.path("providerPlaintextEventCount").asLong(-1) == 0
            && proof.path("pendingOperationCount").asLong(-1) == 0
            && proof.path("failedOperationCount").asLong(-1) == 0
            && proof.path("callbackSemanticMismatchCount").asLong(-1) == 0
            && proof.path("quarantineCount").asLong(-1) == 0
            && proof.path("degradedOperationCount").asLong(-1) == 0
            && proof.path("supportSafe").asBoolean(false);
  }

  private Map<String, String> proofHeaders() {
    return Map.of("Authorization", "Bearer " + readProofToken(), "Accept", "application/json");
  }

  private String readProofToken() {
    Path path = environment.chatProofToken();
    try {
      if (Files.isSymbolicLink(path)
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new ProductFlowException("isolated Chat proof SecretRef is unavailable");
      }
      try {
        if (!Files.getPosixFilePermissions(path).equals(OWNER_FILE_PERMISSIONS)) {
          throw new ProductFlowException("isolated Chat proof SecretRef must have mode 0600");
        }
      } catch (UnsupportedOperationException ignored) {
        // The private parent directory is the fallback on non-POSIX systems.
      }
      String value = Files.readString(path, StandardCharsets.UTF_8).strip();
      if (value.getBytes(StandardCharsets.UTF_8).length < 32
          || value.getBytes(StandardCharsets.UTF_8).length > 512) {
        throw new ProductFlowException("isolated Chat proof SecretRef has an invalid size");
      }
      return value;
    } catch (IOException failure) {
      throw new ProductFlowException("isolated Chat proof SecretRef could not be read", failure);
    }
  }

  private void control(String command, String marker) {
    Path output = environment.evidenceFile().resolveSibling(".collaboration-control.log");
    Process process = null;
    try {
      Files.deleteIfExists(output);
      Files.createFile(output);
      try {
        Files.setPosixFilePermissions(output, OWNER_FILE_PERMISSIONS);
      } catch (UnsupportedOperationException ignored) {
        // The parent evidence directory remains private.
      }
      process =
          new ProcessBuilder(
                  "bash",
                  environment.persistenceRestartCommand().toString(),
                  "test",
                  command)
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      if (!process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        BoundedProcessTree.terminate(process, PROCESS_CLEANUP_TIMEOUT);
        throw new ProductFlowException("collaboration service control exceeded its bounded timeout");
      }
      String diagnostic = Files.readString(output, StandardCharsets.UTF_8);
      if (process.exitValue() != 0 || !diagnostic.contains(marker)) {
        throw new ProductFlowException("collaboration service control failed");
      }
    } catch (IOException failure) {
      throw new ProductFlowException("collaboration service control could not execute", failure);
    } catch (InterruptedException interrupted) {
      try {
        if (process != null) {
          BoundedProcessTree.terminate(process, PROCESS_CLEANUP_TIMEOUT);
        }
      } finally {
        Thread.currentThread().interrupt();
      }
      throw new ProductFlowException("collaboration service control was interrupted", interrupted);
    } finally {
      try {
        Files.deleteIfExists(output);
      } catch (IOException ignored) {
        // Support-safe command output remains inside the private run directory.
      }
    }
  }

  private void redactBestEffort(
      Identity identity, String roomId, String eventId, String actor, int pass) {
    if (eventId == null) {
      return;
    }
    try {
      http.json(
          "redact isolated " + actor + " event",
          "PUT",
          environment.api(
              "/_matrix/client/v3/rooms/"
                  + encode(roomId)
                  + "/redact/"
                  + encode(eventId)
                  + "/cleanup-"
                  + actor
                  + "-"
                  + pass),
          bearer(
              identity.token(),
              Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
          http.mapper().createObjectNode(),
          Set.of(200));
    } catch (ProductFlowException cleanupFailure) {
      System.err.println("WEAVE_TEST_APP_CLEANUP_ERROR chat-redaction");
    }
  }

  private void leaveBestEffort(Identity identity, String roomId, int pass) {
    try {
      http.json(
          "leave isolated collaboration room",
          "POST",
          environment.api("/_matrix/client/v3/rooms/" + encode(roomId) + "/leave"),
          bearer(
              identity.token(),
              Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
          http.mapper().createObjectNode(),
          Set.of(200));
    } catch (ProductFlowException cleanupFailure) {
      System.err.println("WEAVE_TEST_APP_CLEANUP_ERROR chat-membership");
    }
  }

  private void deleteBestEffort(Identity identity, String path, String kind) {
    try {
      http.send(
          "delete isolated " + kind,
          "DELETE",
          environment.api(path),
          bearer(identity.token(), Map.of()),
          null,
          null,
          Set.of(204, 404));
    } catch (ProductFlowException cleanupFailure) {
      System.err.println("WEAVE_TEST_APP_CLEANUP_ERROR " + kind.replace(' ', '-'));
    }
  }

  private void cleanRoomStrict(
      Identity author,
      Identity collaborator,
      String roomId,
      String authorEventId,
      String collaboratorEventId,
      String outageEventId,
      int pass) {
    redactStrict(author, roomId, authorEventId, "author", pass);
    redactStrict(collaborator, roomId, collaboratorEventId, "collaborator", pass);
    redactStrict(author, roomId, outageEventId, "outage", pass);
    leaveStrict(collaborator, roomId, pass);
    leaveStrict(author, roomId, pass);
    for (Identity identity : List.of(author, collaborator)) {
      http.send(
          "verify post-leave Matrix denial",
          "GET",
          environment.api("/_matrix/client/v3/rooms/" + encode(roomId) + "/messages?limit=10"),
          bearer(
              identity.token(),
              Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
          null,
          null,
          Set.of(403));
    }
  }

  private void redactStrict(
      Identity identity, String roomId, String eventId, String actor, int pass) {
    if (eventId == null) {
      throw new ProductFlowException("isolated Chat cleanup event is missing");
    }
    JsonNode response =
        http.json(
            "redact isolated " + actor + " event",
            "PUT",
            environment.api(
                "/_matrix/client/v3/rooms/"
                    + encode(roomId)
                    + "/redact/"
                    + encode(eventId)
                    + "/cleanup-"
                    + actor
                    + "-"
                    + pass),
            bearer(
                identity.token(),
                Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            http.mapper().createObjectNode(),
            Set.of(200));
    if (!response.path("event_id").asString().startsWith("$")) {
      throw new ProductFlowException("isolated Chat redaction did not converge");
    }
  }

  private void leaveStrict(Identity identity, String roomId, int pass) {
    http.json(
        "leave isolated collaboration room",
        "POST",
        environment.api("/_matrix/client/v3/rooms/" + encode(roomId) + "/leave"),
        bearer(
            identity.token(),
            Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
        http.mapper().createObjectNode(),
        Set.of(200));
  }

  private void deleteStrict(Identity identity, String path, String kind) {
    http.send(
        "delete isolated " + kind,
        "DELETE",
        environment.api(path),
        bearer(identity.token(), Map.of()),
        null,
        null,
        Set.of(204));
  }

  private URI proof(String path) {
    return environment.chatProofOrigin().resolve(path);
  }

  private byte[] jsonBytes(JsonNode value) {
    try {
      return http.mapper().writeValueAsBytes(value);
    } catch (RuntimeException failure) {
      throw new ProductFlowException("collaboration request encoding failed", failure);
    }
  }

  private static String requireEtag(JsonHttpClient.Response response, String operation) {
    String etag = response.firstHeader("ETag");
    if (etag.isBlank() || etag.length() > 256) {
      throw new ProductFlowException(operation + " omitted its ETag");
    }
    return etag;
  }

  private static String calendar(String uid, String summary) {
    return "BEGIN:VCALENDAR\r\n"
        + "VERSION:2.0\r\n"
        + "PRODID:-//Weave//isolated testApp//EN\r\n"
        + "BEGIN:VEVENT\r\n"
        + "UID:"
        + uid
        + "\r\n"
        + "DTSTAMP:20300101T000000Z\r\n"
        + "DTSTART:20300102T100000Z\r\n"
        + "DTEND:20300102T110000Z\r\n"
        + "SUMMARY:"
        + summary
        + "\r\n"
        + "END:VEVENT\r\n"
        + "END:VCALENDAR\r\n";
  }

  private String ciphertext(String actor, int pass) {
    return "cipher-" + Hashing.sha256(environment.runId() + "\u0000" + actor + "\u0000" + pass);
  }

  private String deviceId(String role, int pass) {
    return ("WEAVE" + role + "PASS" + pass + runHash()).toUpperCase(java.util.Locale.ROOT);
  }

  private String runHash() {
    return Hashing.sha256(environment.runId()).substring(0, 20);
  }

  private static String conversationId(String roomId) {
    int separator = roomId.lastIndexOf(':');
    if (!roomId.startsWith("!") || separator < 2) {
      throw new ProductFlowException("Matrix room ID cannot be mapped to its canonical conversation");
    }
    return roomId.substring(1, separator);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static Map<String, String> bearer(String token, Map<String, String> additional) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.putAll(additional);
    return Map.copyOf(headers);
  }

  private static void sleep() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("collaboration convergence was interrupted", interrupted);
    }
  }

  record PassProof(
      int pass,
      String authorIdentityRefHash,
      String collaboratorIdentityRefHash,
      String outsiderIdentityRefHash,
      boolean freshAuthorizationCodePkce,
      boolean chatPassed,
      boolean filesPassed,
      boolean calendarPassed,
      boolean homePassed,
      boolean profilePassed,
      boolean outsiderDenied,
      boolean canonicalJpaVerified,
      boolean directSynapseVerified,
      boolean providerOutageExactlyOnceVerified,
      boolean restartContinuityVerified,
      boolean callbackReplayVerified,
      boolean cleanupComplete,
      String providerCorrelationHash) {}

  private record Identity(
      String role,
      String token,
      String issuer,
      String tenant,
      String actorRef,
      String referenceHash) {}

  private record MatrixIdentity(String userId, String deviceId) {}

  private record OutageProof(String eventId, String ciphertext) {}

  private record RetainedRoom(
      String roomId,
      String authorEventId,
      String collaboratorEventId,
      String outageEventId,
      List<String> ciphertexts,
      List<String> correlationHashes) {
    RetainedRoom {
      ciphertexts = List.copyOf(ciphertexts);
      correlationHashes = List.copyOf(correlationHashes);
    }
  }
}
