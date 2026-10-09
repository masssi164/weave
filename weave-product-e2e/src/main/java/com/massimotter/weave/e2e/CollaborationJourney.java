package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.model.AuthenticatedUserResponse;
import com.massimotter.weave.userapi.model.PlatformConfigResponse;
import com.massimotter.weave.userapi.model.ProductProfileResponse;
import com.massimotter.weave.userapi.model.WorkspaceHomeRecentActivityResponse;
import com.massimotter.weave.userapi.model.WorkspaceHomeResponse;
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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
  private static final String MATRIX_DEVICE_PROOF_HEADER = "X-Weave-Matrix-Device-Proof";
  private static final String MEGOLM = "m.megolm.v1.aes-sha2";
  private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration PROCESS_CLEANUP_TIMEOUT = Duration.ofSeconds(10);

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private RetainedRoom retainedFirstPass;
  private final GeneratedCalendarJourney calendar;
  private final GeneratedFilesJourney files;
  private final GeneratedProfileHomeApi profileHome;
  private final SecureRandom deviceProofRandom = new SecureRandom();
  private final Map<String, String> deviceProofs = new LinkedHashMap<>();

  CollaborationJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
    this.calendar = new GeneratedCalendarJourney(environment);
    this.files = new GeneratedFilesJourney(environment);
    this.profileHome = new GeneratedProfileHomeApi(environment);
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
    String roomId = null;
    String authorEventId = null;
    String collaboratorEventId = null;
    String outageEventId = null;
    GeneratedFilesJourney.MemberProof fileProof = null;
    GeneratedCalendarJourney.Proof calendarProof = null;
    boolean restartContinuityVerified = false;
    String nativeRevisionHash = null;
    try {
      if (pass == 1) {
        requirePublicMatrixDiscovery();
      }
      MatrixIdentity collaboratorMatrix = matrixIdentity(collaboratorIdentity, pass);
      matrixIdentity(outsiderIdentity, pass);
      MatrixIdentity authorMatrix = matrixIdentity(authorIdentity, pass);
      if (pass == 1 && Boolean.getBoolean("weave.e2e.release-mcp")) {
        proveOpenClawBusinessRoom(authorIdentity, authorMatrix.userId(), pass);
      }
      if (pass == 2) {
        restartContinuityVerified =
            verifyAndCleanRetainedFirstPass(
                authorIdentity, collaboratorIdentity, outsiderIdentity, pass);
      }
      roomId = createEncryptedRoom(authorIdentity, collaboratorMatrix.userId(), pass);
      joinRoom(collaboratorIdentity, roomId, pass);
      String collaboratorSyncCursor = matrixSyncCursor(collaboratorIdentity, roomId, pass);

      String authorCiphertext = ciphertext("author", pass);
      String collaboratorCiphertext = ciphertext("collaborator", pass);
      authorEventId = sendEncrypted(authorIdentity, roomId, authorCiphertext, "author", pass);
      requireCiphertextObserved(collaboratorIdentity, roomId, authorCiphertext, "author", pass);
      requireSyncObserved(
          collaboratorIdentity, roomId, authorEventId, authorCiphertext,
          collaboratorSyncCursor, pass);
      collaboratorEventId =
          sendEncrypted(collaboratorIdentity, roomId, collaboratorCiphertext, "collaborator", pass);
      requireCiphertextObserved(authorIdentity, roomId, collaboratorCiphertext, "collaborator", pass);
      requireMatrixDenied(outsiderIdentity, roomId, pass);
      String initialFile = "initial-" + Hashing.sha256(suffix).substring(0, 24);
      String updatedFile = "updated-" + Hashing.sha256(suffix).substring(0, 24);
      fileProof = files.createMemberProof(authorIdentity.token(), fileName, initialFile,
          "collaboration-file-create-" + suffix);
      fileProof = files.updateMemberProof(fileProof, authorIdentity.token(), updatedFile,
          "collaboration-file-update-" + suffix);
      files.verifyMemberProof(fileProof, authorIdentity.token(), collaboratorIdentity.token(),
          outsiderIdentity.token());

      calendarProof = calendar.createAndVerify(
          authorIdentity.token(), collaboratorIdentity.token(), outsiderIdentity.token(), suffix);
      nativeRevisionHash =
          Hashing.sha256(
              authorEventId
                  + "\u0000"
                  + collaboratorEventId
                  + "\u0000"
                  + fileProof.revision()
                  + "\u0000"
                  + calendarProof.revisionEvidence());

      proveProfileIsolation(pass, authorIdentity, collaboratorIdentity, outsiderIdentity);
      proveHomeProjection(authorIdentity, collaboratorIdentity, outsiderIdentity);

      if (pass == 1) {
        retainedFirstPass =
            new RetainedRoom(
                roomId,
                authorEventId,
                collaboratorEventId,
                outageEventId,
                List.of(authorCiphertext, collaboratorCiphertext),
                List.of(
                    Hashing.sha256(authorCiphertext),
                    Hashing.sha256(collaboratorCiphertext)),
                fileProof,
                calendarProof);
        roomId = null;
        authorEventId = null;
        collaboratorEventId = null;
        outageEventId = null;
        calendarProof = null;
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
      if (pass == 2) {
        calendar.delete(calendarProof, authorIdentity.token());
        calendarProof = null;
      }

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
          false,
          restartContinuityVerified,
          true,
          nativeRevisionHash);
    } finally {
      if (roomId != null) {
        redactBestEffort(authorIdentity, roomId, authorEventId, "author", pass);
        redactBestEffort(collaboratorIdentity, roomId, collaboratorEventId, "collaborator", pass);
        redactBestEffort(authorIdentity, roomId, outageEventId, "outage", pass);
        leaveBestEffort(collaboratorIdentity, roomId, pass);
        leaveBestEffort(authorIdentity, roomId, pass);
      }
      // Exact namespace teardown removes temporary Files objects; successful Calendar
      // paths delete their events through the generated API with current versions.
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
    files.verifyMemberProof(room.fileProof(), author.token(), collaborator.token(), outsider.token());
    calendar.verify(room.calendarProof(), author.token(), collaborator.token(), outsider.token());
    cleanRoomStrict(
        author,
        collaborator,
        room.roomId(),
        room.authorEventId(),
        room.collaboratorEventId(),
        room.outageEventId(),
        pass);
    calendar.delete(room.calendarProof(), author.token());
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

  private void requirePublicMatrixDiscovery() {
    JsonNode wellKnown = http.json(
        "discover Weave Matrix facade", "GET",
        environment.apiOrigin().resolve("/.well-known/matrix/client"),
        Map.of(), null, Set.of(200));
    String expectedOrigin = environment.apiOrigin().getScheme() + "://"
        + environment.apiOrigin().getRawAuthority();
    if (!expectedOrigin.equals(
        wellKnown.path("m.homeserver").path("base_url").asString())) {
      throw new ProductFlowException("Matrix discovery does not point to the Weave API authority");
    }
    JsonNode versions = http.json(
        "discover public Matrix versions", "GET",
        environment.api("/_matrix/client/versions"), Map.of(), null, Set.of(200));
    if (!versions.path("versions").isArray()
        || !"v1.18".equals(versions.path("versions").path(0).asString())) {
      throw new ProductFlowException("public Matrix version discovery is unavailable");
    }
    JsonNode login = http.json(
        "discover Matrix login policy", "GET",
        environment.api("/_matrix/client/v3/login"), Map.of(), null, Set.of(200));
    if (!login.path("flows").isArray() || login.path("flows").size() != 0) {
      throw new ProductFlowException("Matrix discovery advertised an unavailable second login");
    }
  }

  private MatrixIdentity matrixIdentity(Identity identity, int pass) {
    JsonNode response =
        http.jsonRetryingMatrixIdentityConflict(
            "register " + identity.role() + " Matrix facade identity",
            environment.api("/_matrix/client/v3/account/whoami"),
            bearer(
                identity.token(),
                Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            6,
            Duration.ofSeconds(1));
    String userId = response.path("user_id").asString();
    String deviceId = response.path("device_id").asString();
    if (!userId.matches("@[A-Za-z0-9._=/-]+:[A-Za-z0-9.:-]+")
        || !deviceId.equals(deviceId(identity.role(), pass))) {
      throw new ProductFlowException(identity.role() + " Matrix identity is invalid");
    }
    requireDeviceProofDenial(identity, pass);
    return new MatrixIdentity(userId, deviceId);
  }

  private void proveOpenClawBusinessRoom(Identity member, String userId, int pass) {
    String script = System.getProperty("weave.e2e.openclaw-matrix-script", "");
    if (script.isBlank() || !Path.of(script).isAbsolute()
        || !Files.isRegularFile(Path.of(script))) {
      throw new ProductFlowException("real OpenClaw Matrix proof script is unavailable");
    }
    ObjectNode request = http.mapper().createObjectNode();
    request.put("name", "Weave business Chat " + runHash());
    JsonNode created = http.json(
        "create authorized non-encrypted business room", "POST",
        environment.api("/_matrix/client/v3/createRoom"),
        bearer(member.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(member.role(), pass))),
        request, Set.of(200));
    String roomId = created.path("room_id").asString();
    if (!roomId.matches("![^:]{1,200}:[A-Za-z0-9.:-]+")) {
      throw new ProductFlowException("business Matrix room projection is invalid");
    }
    String marker = "Weave OpenClaw Matrix E2E " + runHash();
    try {
      ProcessBuilder builder = new ProcessBuilder(
          "python3", script,
          "--homeserver", environment.apiOrigin().toString(),
          "--user", userId,
          "--room", roomId,
          "--message", marker,
          "--ca", environment.caCertificate().toString(),
          "--private-root", environment.evidenceFile().getParent().toString());
      builder.environment().put("WEAVE_MATRIX_MEMBER_TOKEN", member.token());
      Process process = builder.redirectErrorStream(true).start();
      if (!process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new ProductFlowException("real OpenClaw Matrix send exceeded its deadline");
      }
      String output = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8);
      if (process.exitValue() != 0
          || !output.contains("WEAVE_OPENCLAW_MATRIX_RESULT status=passed")) {
        String diagnostic = output.lines()
            .filter(line -> line.startsWith("WEAVE_OPENCLAW_MATRIX_ERROR "))
            .findFirst().orElse("WEAVE_OPENCLAW_MATRIX_ERROR unavailable");
        throw new ProductFlowException(diagnostic);
      }
      System.out.println(output.trim());
      Instant deadline = Instant.now().plus(environment.convergenceTimeout());
      while (Instant.now().isBefore(deadline)) {
        JsonNode messages = http.json(
            "independently read OpenClaw business-room event", "GET",
            environment.api("/_matrix/client/v3/rooms/" + encode(roomId) + "/messages?limit=100"),
            bearer(member.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(member.role(), pass))),
            null, Set.of(200));
        for (JsonNode event : messages.path("chunk")) {
          if ("m.room.message".equals(event.path("type").asString())
              && marker.equals(event.path("content").path("body").asString())
              && userId.equals(event.path("sender").asString())
              && event.path("event_id").asString().startsWith("$")) {
            return;
          }
        }
        sleep();
      }
      throw new ProductFlowException("OpenClaw business-room event did not converge");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("real OpenClaw Matrix proof was interrupted", failure);
    } catch (IOException failure) {
      throw new ProductFlowException("real OpenClaw Matrix proof could not start", failure);
    } finally {
      leaveBestEffort(member, roomId, pass);
    }
  }

  private void requireDeviceProofDenial(Identity identity, int pass) {
    Map<String, String> headers = new LinkedHashMap<>(
        bearer(identity.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))));
    byte[] unboundSecret = new byte[48];
    deviceProofRandom.nextBytes(unboundSecret);
    headers.put(MATRIX_DEVICE_PROOF_HEADER,
        Base64.getUrlEncoder().withoutPadding().encodeToString(unboundSecret));
    JsonNode denied = http.json(
        "deny unbound Matrix device proof",
        "GET",
        environment.api("/_matrix/client/v3/account/whoami"),
        Map.copyOf(headers),
        null,
        Set.of(401));
    if (!"M_UNKNOWN_TOKEN".equals(denied.path("errcode").asString())) {
      throw new ProductFlowException("unbound Matrix device proof was not rejected");
    }
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
    JsonNode retry =
        http.json(
            "retry opaque encrypted " + actor + " event idempotently",
            "PUT",
            environment.api(
                "/_matrix/client/v3/rooms/"
                    + encode(roomId)
                    + "/send/m.room.encrypted/"
                    + encode(transaction)),
            bearer(identity.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(identity.role(), pass))),
            request,
            Set.of(200));
    if (!eventId.equals(retry.path("event_id").asString())) {
      throw new ProductFlowException("Matrix transaction retry was not idempotent");
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

  private String matrixSyncCursor(Identity member, String roomId, int pass) {
    JsonNode response = http.json(
        "establish member Matrix sync cursor", "GET",
        environment.api("/_matrix/client/v3/sync?timeout=0"),
        bearer(member.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(member.role(), pass))),
        null, Set.of(200));
    String cursor = response.path("next_batch").asString();
    JsonNode encryption = response.path("rooms").path("join").path(roomId)
        .path("state").path("events");
    if (!cursor.startsWith("weave.s1.") || !encryption.isArray()) {
      throw new ProductFlowException("member Matrix sync did not establish a Weave room cursor");
    }
    boolean encrypted = false;
    for (JsonNode event : encryption) {
      if ("m.room.encryption".equals(event.path("type").asString())
          && MEGOLM.equals(event.path("content").path("algorithm").asString())) {
        encrypted = true;
      }
    }
    if (!encrypted) {
      throw new ProductFlowException("member Matrix sync omitted the room encryption policy");
    }
    return cursor;
  }

  private void requireSyncObserved(
      Identity member, String roomId, String eventId, String ciphertext,
      String since, int pass) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    while (Instant.now().isBefore(deadline)) {
      JsonNode response = http.json(
          "observe member Matrix incremental sync", "GET",
          environment.api("/_matrix/client/v3/sync?since=" + encode(since) + "&timeout=0"),
          bearer(member.token(), Map.of(MATRIX_DEVICE_HEADER, deviceId(member.role(), pass))),
          null, Set.of(200));
      String nextBatch = response.path("next_batch").asString();
      if (!nextBatch.startsWith("weave.s1.")) {
        throw new ProductFlowException("member Matrix sync returned an invalid Weave cursor");
      }
      if (syncContainsEncryptedEvent(response, roomId, eventId, ciphertext)) {
        if (since.equals(nextBatch)) {
          throw new ProductFlowException("member Matrix sync did not advance after an event");
        }
        return;
      }
      sleep();
    }
    throw new ProductFlowException("encrypted event did not converge through member Matrix sync");
  }

  static boolean syncContainsEncryptedEvent(
      JsonNode response, String roomId, String eventId, String ciphertext) {
    JsonNode events = response.path("rooms").path("join").path(roomId)
        .path("timeline").path("events");
    if (!events.isArray()) {
      return false;
    }
    for (JsonNode event : events) {
      if (eventId.equals(event.path("event_id").asString())
          && "m.room.encrypted".equals(event.path("type").asString())
          && ciphertext.equals(event.path("content").path("ciphertext").asString())
          && !event.path("content").has("body")) {
        return true;
      }
    }
    return false;
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
      PlatformConfigResponse platform =
          new GeneratedUserApi(environment.apiOrigin(), environment.caCertificate())
              .platformConfig();
      if (!Integer.valueOf(2).equals(platform.getSchemaVersion())
          || !environment.apiOrigin().resolve("/api").equals(platform.getUserApiBaseUrl())
          || platform.getProtocols() == null
          || platform.getProtocols().getMatrixClientServerBaseUrl() == null) {
        throw new ProductFlowException("platform configuration failed during Chat outage");
      }
      AuthenticatedUserResponse member =
          new GeneratedUserApi(environment.apiOrigin(), environment.caCertificate())
              .authenticatedUser(author.token());
      if (!author.issuer().equals(member.getIdentityIssuer())
          || !author.actorRef().equals("user:" + member.getSubject())
          || !author.tenant().equals(member.getOrganizationId())
          || !(("issuer+subject:" + author.issuer() + "#" + member.getSubject())
              .equals(member.getPrimaryIdentityKey()))) {
        throw new ProductFlowException("authenticated member identity changed during Chat outage");
      }
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

  private void proveProfileIsolation(
      int pass, Identity author, Identity collaborator, Identity outsider) {
    Map<String, String> observed = new LinkedHashMap<>();
    for (Identity identity : List.of(author, collaborator, outsider)) {
      String displayName = "Weave " + identity.role() + " " + runHash() + " " + pass;
      String locale = "collaborator".equals(identity.role()) ? "de" : "en";
      ProductProfileResponse updated = profileHome.updateProfile(identity.token(), displayName, locale);
      if (!displayName.equals(updated.getDisplayName())
          || updated.getAccessibilityPreferences() == null
          || !"true".equals(updated.getAccessibilityPreferences().get("reducedMotion"))) {
        throw new ProductFlowException(identity.role() + " profile update did not persist");
      }
      ProductProfileResponse current = profileHome.readProfile(identity.token());
      observed.put(identity.role(), current.getUserId());
      if (!displayName.equals(current.getDisplayName())
          || !locale.equals(current.getLocale())
          || !"Europe/Berlin".equals(current.getTimezone())
          || current.getProfileVisibility()
              != ProductProfileResponse.ProfileVisibilityEnum.PRIVATE
          || current.getAccessibilityPreferences() == null
          || !"true".equals(current.getAccessibilityPreferences().get("reducedMotion"))) {
        throw new ProductFlowException(identity.role() + " profile isolation did not persist");
      }
    }
    if (observed.values().stream().anyMatch(value -> value == null || value.isBlank())
        || Set.copyOf(observed.values()).size() != 3) {
      throw new ProductFlowException("product profile identities are not isolated");
    }
  }

  private void proveHomeProjection(Identity author, Identity collaborator, Identity outsider) {
    WorkspaceHomeResponse authorHome = home(author);
    String privateActivityRef = null;
    for (WorkspaceHomeRecentActivityResponse activity : authorHome.getRecentActivity()) {
      if ("files.user_write.completed".equals(activity.getAction())
          && WorkspaceHomeRecentActivityResponse.VisibilityEnum.PRIVATE.equals(activity.getVisibility())
          && Boolean.TRUE.equals(activity.getActorIsCurrentUser())) {
        privateActivityRef = activity.getActivityRef();
        break;
      }
    }
    if (privateActivityRef == null || privateActivityRef.isBlank()) {
      throw new ProductFlowException("completed User Files activity did not reach owner Home");
    }
    for (Identity identity : List.of(collaborator, outsider)) {
      WorkspaceHomeResponse otherHome = home(identity);
      for (WorkspaceHomeRecentActivityResponse activity : otherHome.getRecentActivity()) {
        if (privateActivityRef.equals(activity.getActivityRef())) {
          throw new ProductFlowException("private User Files activity leaked to " + identity.role());
        }
      }
    }
  }

  private WorkspaceHomeResponse home(Identity identity) {
    WorkspaceHomeResponse response = profileHome.home(identity.token());
    if (!Boolean.TRUE.equals(response.getSupportSafe()) || response.getRecentActivity() == null) {
      throw new ProductFlowException(identity.role() + " Home activity response is invalid");
    }
    return response;
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
                  "e2e",
                  command)
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      if (!process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
        throw BoundedProcessTree.terminatePreservingFailure(
            process,
            PROCESS_CLEANUP_TIMEOUT,
            new ProductFlowException(
                "collaboration service control exceeded its bounded timeout"));
      }
      String diagnostic = Files.readString(output, StandardCharsets.UTF_8);
      if (process.exitValue() != 0 || !diagnostic.contains(marker)) {
        throw new ProductFlowException(
            "collaboration service control failed reason="
                + supportSafeControlFailure(diagnostic));
      }
    } catch (IOException failure) {
      throw new ProductFlowException("collaboration service control could not execute", failure);
    } catch (InterruptedException interrupted) {
      throw BoundedProcessTree.interruptedFailure(
          process,
          PROCESS_CLEANUP_TIMEOUT,
          "collaboration service control was interrupted",
          interrupted);
    } finally {
      try {
        Files.deleteIfExists(output);
      } catch (IOException ignored) {
        // Support-safe command output remains inside the private run directory.
      }
    }
  }

  static String supportSafeControlFailure(String diagnostic) {
    if (diagnostic == null) {
      return "unspecified";
    }
    Map<String, String> allowlisted =
        Map.of(
            "collaboration service control exceeded its bounded timeout", "control-timeout",
            "collaboration service control Docker operation exceeded its bounded timeout",
                "docker-timeout",
            "collaboration service control Docker operation failed", "docker-operation",
            "collaboration service restart identity did not advance exactly",
                "restart-identity");
    return allowlisted.entrySet().stream()
        .filter(entry -> diagnostic.contains("WEAVE_COMPOSE_ERROR " + entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse("unspecified");
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
    if (outageEventId != null) {
      redactStrict(author, roomId, outageEventId, "outage", pass);
    }
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

  private Map<String, String> bearer(String token, Map<String, String> additional) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.putAll(additional);
    String deviceId = additional.get(MATRIX_DEVICE_HEADER);
    if (deviceId != null) {
      headers.put(MATRIX_DEVICE_PROOF_HEADER, deviceProofs.computeIfAbsent(deviceId, ignored -> {
        byte[] secret = new byte[48];
        deviceProofRandom.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
      }));
    }
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
      boolean nativePersistenceVerified,
      boolean idempotencyVerified,
      boolean southboundProviderDependencyObserved,
      boolean restartContinuityVerified,
      boolean cleanupComplete,
      String nativeRevisionHash) {}

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
      List<String> correlationHashes,
      GeneratedFilesJourney.MemberProof fileProof,
      GeneratedCalendarJourney.Proof calendarProof) {
    RetainedRoom {
      ciphertexts = List.copyOf(ciphertexts);
      correlationHashes = List.copyOf(correlationHashes);
    }
  }
}
