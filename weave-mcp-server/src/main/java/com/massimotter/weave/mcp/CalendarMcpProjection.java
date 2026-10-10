package com.massimotter.weave.mcp;

import com.massimotter.weave.userapi.model.CalendarUserAgenda;
import com.massimotter.weave.userapi.model.CalendarUserEvent;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Curated Calendar tools over the generated User API. */
@Component
public final class CalendarMcpProjection {
  private final CalendarUserApiClient calendars;

  CalendarMcpProjection(CalendarUserApiClient calendars) {
    this.calendars = calendars;
  }

  @McpTool(
      name = "calendar.agenda",
      title = "Read Weave Calendar agenda",
      description =
          "Read a bounded agenda for one currently authorized Weave Calendar without materializing previews.",
      // The generated User model has OpenAPI nullable/additional-properties metadata that
      // Spring AI's independent output-schema generator misreads. The User API and the
      // bounded Calendar client validate the result before MCP serialization.
      generateOutputSchema = false,
      annotations =
          @McpTool.McpAnnotations(
              title = "Read Calendar agenda",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  public CalendarUserAgenda agenda(
      @McpToolParam(description = "Stable Weave Calendar reference") String calendarId,
      @McpToolParam(description = "RFC 3339 interval start") String from,
      @McpToolParam(description = "RFC 3339 interval end, at most 366 days later") String to,
      @McpToolParam(description = "IANA evaluation time zone") String evaluationTimeZone) {
    return calendars.agenda(calendarId, from, to, evaluationTimeZone);
  }

  @McpTool(
      name = "calendar.create",
      title = "Create Weave Calendar event",
      description = "Create one supported event with an idempotency key in an authorized Calendar.",
      generateOutputSchema = false,
      annotations = @McpTool.McpAnnotations(
          title = "Create Calendar event", readOnlyHint = false,
          destructiveHint = false, idempotentHint = true, openWorldHint = false))
  public CalendarUserEvent create(
      @McpToolParam(description = "Stable Weave Calendar reference") String calendarId,
      @McpToolParam(description = "Stable retry key for this event creation") String idempotencyKey,
      @McpToolParam(description = "Complete supported event content from the User API")
          Map<String, Object> event) {
    return calendars.create(calendarId, idempotencyKey, calendars.eventRequest(event));
  }

  @McpTool(
      name = "calendar.update",
      title = "Update Weave Calendar event",
      description = "Replace supported event content using the current strong event version.",
      generateOutputSchema = false,
      annotations = @McpTool.McpAnnotations(
          title = "Update Calendar event", readOnlyHint = false,
          destructiveHint = false, idempotentHint = false, openWorldHint = false))
  public CalendarUserEvent update(
      @McpToolParam(description = "Stable Weave Calendar reference") String calendarId,
      @McpToolParam(description = "Stable materialized Weave Event reference") String eventId,
      @McpToolParam(description = "Current strong event version") String ifMatch,
      @McpToolParam(description = "Complete supported replacement content from the User API")
          Map<String, Object> event) {
    return calendars.update(calendarId, eventId, ifMatch, calendars.eventRequest(event));
  }

  @McpTool(
      name = "calendar.delete",
      title = "Delete Weave Calendar event",
      description = "Delete a materialized event using its current strong event version.",
      generateOutputSchema = false,
      annotations = @McpTool.McpAnnotations(
          title = "Delete Calendar event", readOnlyHint = false,
          destructiveHint = true, idempotentHint = false, openWorldHint = false))
  public Deletion delete(
      @McpToolParam(description = "Stable Weave Calendar reference") String calendarId,
      @McpToolParam(description = "Stable materialized Weave Event reference") String eventId,
      @McpToolParam(description = "Current strong event version") String ifMatch) {
    calendars.delete(calendarId, eventId, ifMatch);
    return new Deletion(eventId, true);
  }

  public record Deletion(String eventId, boolean deleted) {}
}
