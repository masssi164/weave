package com.massimotter.weave.backend.service.calendar;

import static org.assertj.core.api.Assertions.*;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.*;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

class CalendarUserModelMapperTest {
    @ParameterizedTest
    @EnumSource(TemporalKind.class)
    void roundTripsEveryTemporalKindAndCompatibleUntilWithoutOffsetsOrFieldLoss(TemporalKind kind) {
        TimeValue start = time(kind, "2026-03-28", "09:00:00");
        TimeValue end = time(kind, kind == TemporalKind.DATE ? "2026-03-29" : "2026-03-28", "10:00:00");
        TimeValue until = time(kind == TemporalKind.ZONED ? TemporalKind.UTC : kind, "2026-04-01", "09:00:00");
        Recurrence recurrence = new Recurrence(RecurrenceFrequency.DAILY, 1, null, until,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        WriteRequest request = new WriteRequest("Planning", null, start, end, null, List.of(), recurrence, List.of());
        var event = CalendarUserModelMapper.event(new CalendarId("calendar"), new EventId("event"), CalendarScope.workspace(), request);
        assertThat(CalendarUserModelMapper.content(event)).isEqualTo(request);
        var expanded = new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()).occurrences(event,
                Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-04-03T00:00:00Z"), ZoneId.of("Europe/Berlin"));
        assertThat(expanded).hasSize(5);
        if (kind == TemporalKind.ZONED) {
            assertThat(expanded.get(0).start().getHour()).isEqualTo(9);
            assertThat(expanded.get(1).start().getHour()).isEqualTo(9);
            assertThat(expanded.get(0).start().getOffset()).isNotEqualTo(expanded.get(1).start().getOffset());
        }
    }

    @Test
    void inputDoesNotAcceptUnknownFieldsOrAnOffsetDisguisedAsFloatingTime() {
        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("""
                {"title":"Planning","start":{"kind":"FLOATING","localDateTime":"2026-03-28T09:00:00"},
                 "end":{"kind":"FLOATING","localDateTime":"2026-03-28T10:00:00"},"attendees":[],"overrides":[],"providerUid":"injected"}
                """, WriteRequest.class)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> CalendarUserModelMapper.temporal(new TimeValue(TemporalKind.FLOATING,
                null, "2026-03-28T09:00:00Z", null, null))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupportedProviderFieldsAndParametersAreRejectedBeforeAnEditCanLoseThem() {
        Ical4jIcalendarCodec codec = new Ical4jIcalendarCodec();
        for (String extra : List.of("X-PRIVATE-PROPERTY:keep-me", "ORGANIZER:mailto:owner@example.test",
                "DESCRIPTION;LANGUAGE=de:keep-me", "BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT5M\r\nDESCRIPTION:Reminder\r\nEND:VALARM")) {
            String body = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\nBEGIN:VEVENT\r\nUID:event\r\n"
                    + "DTSTAMP:20260328T000000Z\r\nDTSTART:20260328T090000Z\r\nDTEND:20260328T100000Z\r\nSUMMARY:Planning\r\n"
                    + extra + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
            assertThatThrownBy(() -> codec.decode(new CalendarId("calendar"), CalendarScope.workspace(), EventVersion.unknown(), body))
                    .isInstanceOf(CalendarAdapterException.class);
        }
    }

    static TimeValue time(TemporalKind kind, String date, String clock) {
        return switch (kind) {
            case DATE -> new TimeValue(kind, date, null, null, null);
            case FLOATING -> new TimeValue(kind, null, date + "T" + clock, null, null);
            case UTC -> new TimeValue(kind, null, null, date + "T" + clock + "Z", null);
            case ZONED -> new TimeValue(kind, null, date + "T" + clock, null, "Europe/Berlin");
        };
    }
}
