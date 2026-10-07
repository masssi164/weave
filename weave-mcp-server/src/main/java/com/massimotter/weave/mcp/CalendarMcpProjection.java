package com.massimotter.weave.mcp;

import com.massimotter.weave.userapi.model.CalendarUserAgenda;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** One curated Calendar read tool over the generated User API. */
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
}
