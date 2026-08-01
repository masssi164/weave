package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IcalendarProjectionAssertionsTest {

  @Test
  void acceptsCanonicalNormalizationAndRequiredWorkspaceMetadata() {
    assertThatCode(
            () ->
                IcalendarProjectionAssertions.requireWorkspaceProjection(
                    submitted("Planning"), projected("Planning"), "shared calendar event"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsSemanticDriftAndMissingCanonicalMetadata() {
    assertThatThrownBy(
            () ->
                IcalendarProjectionAssertions.requireWorkspaceProjection(
                    submitted("Planning"), projected("Changed"), "shared calendar event"))
        .isInstanceOf(ProductFlowException.class)
        .hasMessageContaining("canonical SUMMARY semantics changed");

    assertThatThrownBy(
            () ->
                IcalendarProjectionAssertions.requireWorkspaceProjection(
                    submitted("Planning"),
                    projected("Planning").replace("X-WEAVE-CONTEXT-ID:workspace-default\r\n", ""),
                    "shared calendar event"))
        .isInstanceOf(ProductFlowException.class)
        .hasMessageContaining("X-WEAVE-CONTEXT-ID");

    assertThatThrownBy(
            () ->
                IcalendarProjectionAssertions.requireWorkspaceProjection(
                    submitted("Planning").replace(
                        "SUMMARY:Planning\r\n",
                        "SUMMARY:Planning\r\nATTENDEE;ROLE=REQ-PARTICIPANT:mailto:member@example.invalid\r\n"),
                    projected("Planning"),
                    "shared calendar event"))
        .isInstanceOf(ProductFlowException.class)
        .hasMessageContaining("attendee semantics changed");
  }

  private static String submitted(String summary) {
    return "BEGIN:VCALENDAR\r\n"
        + "VERSION:2.0\r\n"
        + "BEGIN:VEVENT\r\n"
        + "UID:event-1\r\n"
        + "DTSTART:20300102T100000Z\r\n"
        + "DTEND:20300102T110000Z\r\n"
        + "SUMMARY:"
        + summary
        + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
  }

  private static String projected(String summary) {
    return "BEGIN:VCALENDAR\r\n"
        + "VERSION:2.0\r\n"
        + "PRODID:-//Weave//Calendar Facade//EN\r\n"
        + "CALSCALE:GREGORIAN\r\n"
        + "BEGIN:VEVENT\r\n"
        + "UID:event-1\r\n"
        + "X-WEAVE-CONTEXT-ID:workspace-default\r\n"
        + "X-WEAVE-MEETING-THREAD-ID:meeting:workspace-default:0123456789ab\r\n"
        + "DTSTAMP:20300101T000000Z\r\n"
        + "DTSTART;TZID=UTC:20300102T100000\r\n"
        + "DTEND;TZID=UTC:20300102T110000\r\n"
        + "SUMMARY:"
        + summary
        + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
  }
}
