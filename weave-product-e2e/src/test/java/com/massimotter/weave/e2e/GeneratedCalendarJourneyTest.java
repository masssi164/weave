package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.model.CalendarEventWriteRequest;
import com.massimotter.weave.userapi.model.CalendarTimeValue;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GeneratedCalendarJourneyTest {
  @Test
  void readbackIgnoresOnlyOmittedVersusExplicitOptionalNulls() throws Exception {
    CalendarEventWriteRequest sent = new CalendarEventWriteRequest()
        .title("Planning")
        .description("Shared calendar")
        .start(new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE)
            .date(LocalDate.parse("2026-10-25")))
        .end(new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE)
            .date(LocalDate.parse("2026-10-26")))
        .attendees(List.of()).overrides(List.of());
    String response = """
        {"title":"Planning","description":"Shared calendar","start":{
          "kind":"DATE","date":"2026-10-25","localDateTime":null,"instant":null,"timeZone":null},
         "end":{"kind":"DATE","date":"2026-10-26","localDateTime":null,
           "instant":null,"timeZone":null},"location":null,"attendees":[],
         "recurrence":null,"overrides":[]}
        """;
    CalendarEventWriteRequest read = ApiClient.createDefaultObjectMapper()
        .readValue(response, CalendarEventWriteRequest.class);

    assertThat(sent).isNotEqualTo(read);
    assertThat(GeneratedCalendarJourney.sameContent(sent, read)).isTrue();
    read.getStart().setDate(LocalDate.parse("2026-10-24"));
    assertThat(GeneratedCalendarJourney.sameContent(sent, read)).isFalse();
    read.getStart().setDate(LocalDate.parse("2026-10-25"));
    read.setAttendees(null);
    assertThat(GeneratedCalendarJourney.sameContent(sent, read)).isFalse();
  }
}
