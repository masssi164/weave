package com.massimotter.weave.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.userapi.api.CalendarUserApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.CalendarEventWriteRequest;
import com.massimotter.weave.userapi.model.CalendarUserAgenda;
import com.massimotter.weave.userapi.model.CalendarUserCalendar;
import com.massimotter.weave.userapi.model.CalendarUserCalendars;
import com.massimotter.weave.userapi.model.CalendarUserEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Generated User Calendar transport with bounded MCP result admission. */
@Component
final class CalendarUserApiClient {
  private static final int MAX_RESULT_ITEMS = 100;
  private static final int MAX_RESULT_BYTES = 262_144;

  private final CalendarUserApi calendars;
  private final McpInvocationCredentials credentials;
  private final ObjectMapper mapper;

  CalendarUserApiClient(McpWorkloadProperties properties, McpInvocationCredentials credentials) {
    this.credentials = credentials;
    URI backend = properties.backendApiUri();
    ApiClient client =
        new ApiClient()
            .setHttpClientBuilder(
                HttpClient.newBuilder()
                    .connectTimeout(properties.requestTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER))
            .setReadTimeout(properties.requestTimeout());
    client.updateBaseUri(backend.getScheme() + "://" + backend.getRawAuthority());
    this.calendars = new CalendarUserApi(client);
    this.mapper = client.getObjectMapper();
  }

  CalendarUserAgenda agenda(String calendarId, String from, String to, String evaluationTimeZone) {
    requireCalendarId(calendarId);
    OffsetDateTime start;
    OffsetDateTime end;
    String zone;
    try {
      start = OffsetDateTime.parse(from);
      end = OffsetDateTime.parse(to);
      if (!ZoneId.getAvailableZoneIds().contains(evaluationTimeZone)) {
        throw new IllegalArgumentException("An IANA evaluation time zone is required");
      }
      zone = ZoneId.of(evaluationTimeZone).getId();
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("A valid bounded Calendar interval and time zone are required");
    }
    Duration window = Duration.between(start.toInstant(), end.toInstant());
    if (window.isZero() || window.isNegative() || window.compareTo(Duration.ofDays(366)) > 0) {
      throw new IllegalArgumentException("The Calendar interval must be at most 366 days");
    }
    Map<String, String> headers = Map.of("Authorization", "Bearer " + credentials.exchangedBearer());
    try {
      CalendarUserCalendars visible = calendars.listUserCalendars(headers);
      if (visible == null || visible.getCalendars() == null
          || visible.getCalendars().stream().noneMatch(item -> visible(item, calendarId))) {
        throw new IllegalArgumentException("The Calendar is unavailable to the current member");
      }
      CalendarUserAgenda result = calendars.queryCalendarAgenda(calendarId, start, end, zone, headers);
      if (result == null || !calendarId.equals(result.getCalendarId())
          || result.getEvents() == null || result.getPreviews() == null
          || result.getOccurrences() == null || result.getPreviewOccurrences() == null
          || result.getEvents().size() + result.getPreviews().size() > MAX_RESULT_ITEMS
          || result.getOccurrences().size() + result.getPreviewOccurrences().size() > MAX_RESULT_ITEMS) {
        throw new IllegalStateException("The Calendar agenda exceeds the MCP result bound");
      }
      if (mapper.writeValueAsBytes(result).length > MAX_RESULT_BYTES) {
        throw new IllegalStateException("The Calendar agenda exceeds the MCP byte bound");
      }
      return result;
    } catch (ApiException failure) {
      throw new IllegalStateException("Calendar User API rejected request: HTTP " + failure.getCode());
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("The Calendar agenda is unavailable");
    }
  }

  CalendarUserEvent create(String calendarId, String idempotencyKey, CalendarEventWriteRequest event) {
    requireCalendarId(calendarId);
    if (idempotencyKey == null || idempotencyKey.isBlank() || event == null) {
      throw new IllegalArgumentException("Calendar create requires an idempotency key and complete event");
    }
    try {
      return boundedEvent(calendarId, calendars.createCalendarEvent(
          calendarId, idempotencyKey, event, authorization()));
    } catch (ApiException failure) {
      throw apiFailure(failure);
    }
  }

  CalendarEventWriteRequest eventRequest(Map<String, Object> event) {
    if (event == null || event.isEmpty()) {
      throw new IllegalArgumentException("Complete Calendar event content is required");
    }
    try {
      // Spring AI treats the generated model's Jackson any-setter as a required
      // MCP input field. Convert the MCP object immediately to the generated
      // User transport model; the Server remains the contract validator.
      return mapper.convertValue(event, CalendarEventWriteRequest.class);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid Calendar event content");
    }
  }

  CalendarUserEvent update(String calendarId, String eventId, String ifMatch,
      CalendarEventWriteRequest event) {
    requireCalendarId(calendarId);
    requireEventId(eventId);
    requireVersion(ifMatch);
    if (event == null) {
      throw new IllegalArgumentException("Calendar update requires complete event content");
    }
    try {
      return boundedEvent(calendarId, calendars.updateCalendarEvent(
          calendarId, eventId, ifMatch, event, authorization()));
    } catch (ApiException failure) {
      throw apiFailure(failure);
    }
  }

  void delete(String calendarId, String eventId, String ifMatch) {
    requireCalendarId(calendarId);
    requireEventId(eventId);
    requireVersion(ifMatch);
    try {
      calendars.deleteCalendarEvent(calendarId, eventId, ifMatch, authorization());
    } catch (ApiException failure) {
      throw apiFailure(failure);
    }
  }

  private CalendarUserEvent boundedEvent(String calendarId, CalendarUserEvent event) {
    if (event == null || !calendarId.equals(event.getCalendarId()) || event.getId() == null
        || event.getVersion() == null || event.getContent() == null) {
      throw new IllegalStateException("The Calendar User API returned an invalid event");
    }
    try {
      if (mapper.writeValueAsBytes(event).length > MAX_RESULT_BYTES) {
        throw new IllegalStateException("The Calendar event exceeds the MCP result bound");
      }
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("The Calendar event is unavailable");
    }
    return event;
  }

  private Map<String, String> authorization() {
    return Map.of("Authorization", "Bearer " + credentials.exchangedBearer());
  }

  private static void requireCalendarId(String id) {
    if (id == null || !id.matches("calendar:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("A stable Weave Calendar reference is required");
    }
  }

  private static void requireEventId(String id) {
    if (id == null || !id.matches("event:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("A stable Weave Event reference is required");
    }
  }

  private static void requireVersion(String version) {
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("A strong Calendar event version is required");
    }
  }

  private static IllegalStateException apiFailure(ApiException failure) {
    return new IllegalStateException("Calendar User API rejected request: HTTP " + failure.getCode());
  }

  private static boolean visible(CalendarUserCalendar item, String expected) {
    return item != null && expected.equals(item.getId())
        && item.getAllowedActions() != null && item.getAllowedActions().contains("read");
  }
}
