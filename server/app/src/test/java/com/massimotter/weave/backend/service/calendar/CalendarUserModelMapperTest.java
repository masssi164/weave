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

    @ParameterizedTest
    @EnumSource(TemporalKind.class)
    void singleInstanceMoveAndCancellationRoundTripWithoutLosingTemporalIntent(TemporalKind kind) {
        var recurrence = new Recurrence(RecurrenceFrequency.DAILY, 1, 4, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        var move = new com.massimotter.weave.backend.model.calendar.CalendarUserModels.Override(
                time(kind, "2026-03-29", "09:00:00"), time(kind, "2026-04-01", "11:00:00"),
                time(kind, kind == TemporalKind.DATE ? "2026-04-02" : "2026-04-01", "12:00:00"), false, "Moved", null, null);
        var cancel = new com.massimotter.weave.backend.model.calendar.CalendarUserModels.Override(
                time(kind, "2026-03-30", "09:00:00"), null, null, true, null, null, null);
        var content = new WriteRequest("Planning", null, time(kind, "2026-03-28", "09:00:00"),
                time(kind, kind == TemporalKind.DATE ? "2026-03-29" : "2026-03-28", "10:00:00"),
                null, List.of(), recurrence, List.of(move, cancel));
        var event = CalendarUserModelMapper.event(new CalendarId("calendar"), new EventId("event"), CalendarScope.workspace(), content);
        assertThat(CalendarUserModelMapper.content(event)).isEqualTo(content);
        CalendarUserModelMapper.requireLossless(event);
        var occurrences = new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()).occurrences(event,
                Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-04-03T00:00:00Z"), ZoneId.of("Europe/Berlin"));
        assertThat(occurrences).hasSize(3);
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

    @ParameterizedTest
    @EnumSource(TemporalKind.class)
    void agendaIncludesRecurringIntervalsThatStartBeforeTheRequestedWindow(TemporalKind kind) {
        var rule = new Recurrence(RecurrenceFrequency.WEEKLY, 1, 5, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        var content = new WriteRequest("Multi-day", null, time(kind, "2026-03-01", "09:00:00"),
                time(kind, "2026-03-04", "10:00:00"), null, List.of(), rule, List.of());
        var event = CalendarUserModelMapper.event(new CalendarId("calendar"), new EventId("event"), CalendarScope.workspace(), content);
        var occurrences = new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()).occurrences(event,
                Instant.parse("2026-03-17T12:00:00Z"), Instant.parse("2026-03-17T13:00:00Z"), ZoneId.of("Europe/Berlin"));
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().start().toLocalDate()).isEqualTo(LocalDate.parse("2026-03-15"));
    }

    @ParameterizedTest
    @EnumSource(RecurrenceFrequency.class)
    void utcRecurrenceRetainsInstantSemanticsForEverySupportedFrequency(RecurrenceFrequency frequency) {
        var values = new Ical4jRecurrenceEngine().utc("FREQ=" + frequency + ";COUNT=3",
                Instant.parse("2026-03-01T09:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2030-04-01T00:00:00Z"), 10);
        assertThat(values).hasSize(3).allSatisfy(value -> assertThat(value.atZone(ZoneOffset.UTC).getHour()).isEqualTo(9));
    }

    @Test
    void recurrenceLimitsRejectTruncationBeforeExclusionsCanHideIt() {
        assertThatThrownBy(() -> new Ical4jRecurrenceEngine().utc("FREQ=DAILY;COUNT=20",
                Instant.parse("2026-03-01T09:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), 10))
                .isInstanceOfSatisfying(CalendarAdapterException.class,
                        failure -> assertThat(failure.details()).containsEntry("errorCode", "calendar-window-too-large"));
    }

    @Test
    void invalidDuplicateAndRulelessOverridesAreRejected() {
        var start = time(TemporalKind.UTC, "2026-03-28", "09:00:00");
        var end = time(TemporalKind.UTC, "2026-03-28", "10:00:00");
        var rule = new Recurrence(RecurrenceFrequency.DAILY, 1, 3, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        var cancelled = new com.massimotter.weave.backend.model.calendar.CalendarUserModels.Override(start, null, null, true, null, null, null);
        var inverted = new com.massimotter.weave.backend.model.calendar.CalendarUserModels.Override(start, end, start, false, null, null, null);
        for (var value : List.of(
                new WriteRequest("Invalid", null, start, end, null, List.of(), null, List.of(cancelled)),
                new WriteRequest("Invalid", null, start, end, null, List.of(), rule, List.of(cancelled, cancelled)),
                new WriteRequest("Invalid", null, start, end, null, List.of(), rule, List.of(inverted)))) {
            assertThatThrownBy(() -> CalendarUserModelMapper.event(new CalendarId("calendar"), new EventId("event"), CalendarScope.workspace(), value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
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

    @Test
    void unsupportedEnvelopeAndOverridePayloadIsRejected() {
        String master = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\nBEGIN:VEVENT\r\nUID:event\r\n"
                + "DTSTAMP:20260328T000000Z\r\nDTSTART:20260328T090000Z\r\nDTEND:20260328T100000Z\r\nSUMMARY:Planning\r\n"
                + "RRULE:FREQ=DAILY;COUNT=3\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        String override = "BEGIN:VEVENT\r\nUID:event\r\nDTSTAMP:20260328T000000Z\r\nRECURRENCE-ID:20260329T090000Z\r\n"
                + "DTSTART:20260329T110000Z\r\nDTEND:20260329T120000Z\r\nATTENDEE:mailto:private@example.test\r\nEND:VEVENT\r\n";
        for (String body : List.of(master.replace("PRODID:", "X-PRIVATE:keep-me\r\nPRODID:"),
                master.replace("END:VCALENDAR", override + "END:VCALENDAR"))) {
            assertThatThrownBy(() -> new Ical4jIcalendarCodec().decode(new CalendarId("calendar"), CalendarScope.workspace(), EventVersion.unknown(), body))
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
