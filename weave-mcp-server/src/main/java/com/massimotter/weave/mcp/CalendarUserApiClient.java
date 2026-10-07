package com.massimotter.weave.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.userapi.api.CalendarUserApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.CalendarUserAgenda;
import com.massimotter.weave.userapi.model.CalendarUserCalendar;
import com.massimotter.weave.userapi.model.CalendarUserCalendars;
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
    if (calendarId == null || !calendarId.matches("calendar:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("A stable Weave Calendar reference is required");
    }
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

  private static boolean visible(CalendarUserCalendar item, String expected) {
    return item != null && expected.equals(item.getId())
        && item.getAllowedActions() != null && item.getAllowedActions().contains("read");
  }
}
