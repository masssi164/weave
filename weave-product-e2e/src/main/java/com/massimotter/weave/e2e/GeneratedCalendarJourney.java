package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.api.CalendarUserApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.CalendarEventRecurrence;
import com.massimotter.weave.userapi.model.CalendarEventWriteRequest;
import com.massimotter.weave.userapi.model.CalendarTimeValue;
import com.massimotter.weave.userapi.model.CalendarUserEvent;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real member Calendar proof with independently stated temporal and access expectations. */
final class GeneratedCalendarJourney {
  private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-10-23T00:00:00Z");
  private static final OffsetDateTime TO = OffsetDateTime.parse("2026-10-29T00:00:00Z");
  private static final String ZONE = "Europe/Berlin";
  private final CalendarUserApi calendar;

  GeneratedCalendarJourney(ProductFlowEnvironment environment) {
    ApiClient client = new ApiClient()
        .setHttpClientBuilder(HttpClient.newBuilder()
            .sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER))
        .setReadTimeout(Duration.ofSeconds(30));
    client.updateBaseUri(environment.apiOrigin().getScheme() + "://"
        + environment.apiOrigin().getRawAuthority());
    calendar = new CalendarUserApi(client);
  }

  Proof createAndVerify(String author, String collaborator, String outsider, String runId) {
    String stage = "discovery";
    try {
      var calendars = calendar.listUserCalendars(bearer(author));
      var workspace = calendars.getCalendars().stream()
          .filter(value -> "workspace-default".equals(value.getScope().getSpaceId()))
          .findFirst().orElseThrow(() -> failure("workspace calendar is unavailable"));
      String calendarId = workspace.getId();
      if (!calendarId.matches("calendar:[0-9a-f]{64}")) {
        throw failure("calendar identity is not an opaque Weave reference");
      }
      List<EventProof> events = new ArrayList<>();
      List<CalendarEventWriteRequest> fixtures = fixtures(runId);
      for (int index = 0; index < fixtures.size(); index++) {
        CalendarEventWriteRequest input = fixtures.get(index);
        String key = "calendar-create-" + Hashing.sha256(runId + ":" + index);
        stage = "create-" + index;
        CalendarUserEvent created = calendar.createCalendarEvent(calendarId, key, input, bearer(author));
        stage = "create-replay-" + index;
        CalendarUserEvent replayed = calendar.createCalendarEvent(calendarId, key, input, bearer(author));
        requireSame(created, replayed);
        requireContent(input, created);
        if (!created.getId().matches("event:[0-9a-f]{64}")
            || !calendarId.equals(created.getCalendarId())
            || created.getMeetingThreadRef().isBlank()) {
          throw failure("created identity or meeting correlation is invalid");
        }
        input.setTitle(input.getTitle() + " updated");
        expectStatus(Set.of(409), "changed create replay", () ->
            calendar.createCalendarEvent(calendarId, key, input, bearer(author)));
        stage = "shared-update-" + index;
        CalendarUserEvent updated = calendar.updateCalendarEvent(
            calendarId, created.getId(), created.getVersion(), input, bearer(collaborator));
        requireContent(input, updated);
        if (created.getVersion().equals(updated.getVersion())
            || !created.getId().equals(updated.getId())
            || !created.getScope().equals(updated.getScope())
            || !created.getMeetingThreadRef().equals(updated.getMeetingThreadRef())) {
          throw failure("update changed identity/scope or failed to advance the version");
        }
        expectStatus(Set.of(412), "stale identical update", () ->
            calendar.updateCalendarEvent(calendarId, updated.getId(), created.getVersion(),
                input, bearer(collaborator)));
        events.add(new EventProof(updated, expectedOccurrences(index)));
      }
      Proof proof = new Proof(calendarId, List.copyOf(events));
      verify(proof, author, collaborator, outsider);
      return proof;
    } catch (ApiException failure) {
      throw failure(stage + " failed with HTTP " + failure.getCode());
    }
  }

  void verify(Proof proof, String author, String collaborator, String outsider) {
    try {
      for (EventProof expected : proof.events()) {
        CalendarUserEvent event = expected.event();
        for (String token : List.of(author, collaborator)) {
          var response = calendar.getCalendarEventWithHttpInfo(
              proof.calendarId(), event.getId(), bearer(token));
          requireSame(event, response.getData());
          String etag = response.getHeaders().entrySet().stream()
              .filter(header -> header.getKey().equalsIgnoreCase("ETag"))
              .flatMap(header -> header.getValue().stream()).findFirst().orElse("");
          if (!event.getVersion().equals(etag)) throw failure("ETag and public version disagree");
        }
        expectStatus(Set.of(403, 404), "outsider read", () ->
            calendar.getCalendarEvent(proof.calendarId(), event.getId(), bearer(outsider)));
        expectStatus(Set.of(403, 404), "outsider update", () ->
            calendar.updateCalendarEvent(proof.calendarId(), event.getId(), event.getVersion(),
                event.getContent(), bearer(outsider)));
        expectStatus(Set.of(403, 404), "outsider delete", () -> {
          calendar.deleteCalendarEvent(proof.calendarId(), event.getId(), event.getVersion(), bearer(outsider));
        });
      }
      var agenda = calendar.queryCalendarAgenda(proof.calendarId(), FROM, TO, ZONE, bearer(collaborator));
      if (!proof.calendarId().equals(agenda.getCalendarId())
          || !ZONE.equals(agenda.getEvaluationTimeZone())) throw failure("agenda context changed");
      for (EventProof expected : proof.events()) {
        var event = agenda.getEvents().stream()
            .filter(value -> expected.event().getId().equals(value.getId())).findFirst()
            .orElseThrow(() -> failure("agenda omitted a persisted event"));
        requireSame(expected.event(), event);
        List<String> occurrences = agenda.getOccurrences().stream()
            .filter(value -> event.getId().equals(value.getEventId()))
            .map(value -> value.getStartsAt().toInstant() + "/" + value.getEndsAt().toInstant())
            .sorted().toList();
        if (!expected.occurrences().equals(occurrences)) {
          throw failure("agenda changed DATE/FLOATING/UTC/ZONED intent or recurrence across DST");
        }
      }
      expectStatus(Set.of(403, 404), "outsider agenda", () ->
          calendar.queryCalendarAgenda(proof.calendarId(), FROM, TO, ZONE, bearer(outsider)));
      var first = proof.events().getFirst().event();
      expectStatus(Set.of(403, 404), "outsider create", () ->
          calendar.createCalendarEvent(proof.calendarId(), "calendar-outsider-denied-create",
              first.getContent(), bearer(outsider)));
      // All denied mutations must leave the authoritative object intact.
      requireSame(first, calendar.getCalendarEvent(proof.calendarId(), first.getId(), bearer(author)));
    } catch (ApiException failure) {
      throw failure("verification failed with HTTP " + failure.getCode());
    }
  }

  void delete(Proof proof, String author) {
    try {
      for (EventProof expected : proof.events()) {
        var event = expected.event();
        calendar.deleteCalendarEvent(proof.calendarId(), event.getId(), event.getVersion(), bearer(author));
        expectStatus(Set.of(404), "deleted event read", () ->
            calendar.getCalendarEvent(proof.calendarId(), event.getId(), bearer(author)));
      }
    } catch (ApiException failure) {
      throw failure("cleanup failed with HTTP " + failure.getCode());
    }
  }

  private static List<CalendarEventWriteRequest> fixtures(String runId) {
    String title = "Calendar " + Hashing.sha256(runId).substring(0, 16);
    var date = content(title + " DATE", date("2026-10-25"), date("2026-10-26"));
    var floating = content(title + " FLOATING", local("FLOATING", "2026-10-24T09:00:00"),
        local("FLOATING", "2026-10-24T10:00:00"));
    var utc = content(title + " UTC", utc("2026-10-24T09:00:00Z"), utc("2026-10-24T10:00:00Z"));
    var zoned = content(title + " ZONED", local("ZONED", "2026-10-24T09:00:00"),
        local("ZONED", "2026-10-24T10:00:00"));
    zoned.setRecurrence(new CalendarEventRecurrence()
        .frequency(CalendarEventRecurrence.FrequencyEnum.DAILY).interval(1).count(3)
        .additionalDates(List.of()).excludedDates(List.of()).byDay(List.of())
        .byMonthDay(List.of()).byMonth(List.of()).bySetPos(List.of()));
    return List.of(date, floating, utc, zoned);
  }

  private static CalendarEventWriteRequest content(String title, CalendarTimeValue start, CalendarTimeValue end) {
    return new CalendarEventWriteRequest().title(title).description("Temporal intent and shared access")
        .location("Weave meeting room").start(start).end(end).attendees(List.of()).overrides(List.of());
  }

  private static CalendarTimeValue date(String value) {
    return new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE).date(LocalDate.parse(value));
  }

  private static CalendarTimeValue local(String kind, String value) {
    return new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.fromValue(kind)).localDateTime(value)
        .timeZone("ZONED".equals(kind) ? ZONE : null);
  }

  private static CalendarTimeValue utc(String value) {
    return new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.UTC).instant(OffsetDateTime.parse(value));
  }

  private static List<String> expectedOccurrences(int fixture) {
    return switch (fixture) {
      case 0 -> List.of("2026-10-24T22:00:00Z/2026-10-25T23:00:00Z");
      case 1 -> List.of("2026-10-24T07:00:00Z/2026-10-24T08:00:00Z");
      case 2 -> List.of("2026-10-24T09:00:00Z/2026-10-24T10:00:00Z");
      case 3 -> List.of("2026-10-24T07:00:00Z/2026-10-24T08:00:00Z",
          "2026-10-25T08:00:00Z/2026-10-25T09:00:00Z", "2026-10-26T08:00:00Z/2026-10-26T09:00:00Z");
      default -> throw new IllegalArgumentException("Unknown Calendar fixture");
    };
  }

  private static void requireContent(CalendarEventWriteRequest expected, CalendarUserEvent actual) {
    if (actual == null || !expected.equals(actual.getContent())) throw failure("event content changed on readback");
  }

  private static void requireSame(CalendarUserEvent expected, CalendarUserEvent actual) {
    if (!expected.equals(actual)) throw failure("stable identity, version, scope, thread or content changed");
  }

  private static void expectStatus(Set<Integer> statuses, String stage, Request request) {
    try {
      request.invoke();
    } catch (ApiException failure) {
      if (statuses.contains(failure.getCode())) return;
      throw failure(stage + " returned HTTP " + failure.getCode());
    }
    throw failure(stage + " unexpectedly succeeded");
  }

  private static Map<String, String> bearer(String token) { return Map.of("Authorization", "Bearer " + token); }
  private static ProductFlowException failure(String message) { return new ProductFlowException("generated Calendar " + message); }
  @FunctionalInterface private interface Request { void invoke() throws ApiException; }
  record EventProof(CalendarUserEvent event, List<String> occurrences) {}
  record Proof(String calendarId, List<EventProof> events) {
    String revisionEvidence() {
      return Hashing.sha256(events.stream().map(value -> value.event().getId() + value.event().getVersion())
          .reduce("", (left, right) -> left + "\u0000" + right));
    }
  }
}
