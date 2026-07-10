package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcalendarMapperTest {

    private final IcalendarMapper mapper = new IcalendarMapper();

    @Test
    void mapsCreateRequestToIcalendarAndBack() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Planning, roadmap",
                "Line one\nLine two",
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:30:00+02:00"),
                "Europe/Berlin",
                "Office; Room 1",
                false);

        IcalendarMapper.EventDraft draft = mapper.draftFrom(request);
        String icalendar = mapper.toIcalendar(draft);
        IcalendarMapper.EventDraft parsed = mapper.parse(icalendar);

        assertThat(icalendar).contains("BEGIN:VEVENT");
        assertThat(parsed.title()).isEqualTo("Planning, roadmap");
        assertThat(parsed.description()).isEqualTo("Line one\nLine two");
        assertThat(parsed.startsAt()).isEqualTo(request.startsAt());
        assertThat(parsed.endsAt()).isEqualTo(request.endsAt());
        assertThat(parsed.location()).isEqualTo("Office; Room 1");
        assertThat(parsed.timezone()).isEqualTo("Europe/Berlin");
    }

    @Test
    void mergesUpdateRequestWithoutLeakingCalDavFieldsToApiDto() {
        IcalendarMapper.EventDraft existing = mapper.parse("""
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:test-uid
                DTSTART;TZID=Europe/Berlin:20260426T100000
                DTEND;TZID=Europe/Berlin:20260426T110000
                SUMMARY:Planning
                DESCRIPTION:Original
                END:VEVENT
                END:VCALENDAR
                """);
        UpdateCalendarEventRequest update = new UpdateCalendarEventRequest(
                "Updated",
                null,
                null,
                OffsetDateTime.parse("2026-04-26T12:00:00+02:00"),
                null,
                "Remote",
                null,
                "etag");

        IcalendarMapper.EventDraft merged = mapper.merge(existing, update);

        assertThat(merged.uid()).isEqualTo("test-uid");
        assertThat(merged.title()).isEqualTo("Updated");
        assertThat(merged.description()).isEqualTo("Original");
        assertThat(merged.startsAt()).isEqualTo(existing.startsAt());
        assertThat(merged.endsAt()).isEqualTo(update.endsAt());
        assertThat(merged.location()).isEqualTo("Remote");
    }

    @Test
    void exposesSafeAttendeeProviderAndUpdatedMetadataFromIcalendar() {
        var response = mapper.toResponse("opaque-event-id", "\"etag-1\"", """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:test-uid
                DTSTAMP:20260425T080000Z
                LAST-MODIFIED:20260425T090000Z
                DTSTART;TZID=Europe/Berlin:20260426T100000
                DTEND;TZID=Europe/Berlin:20260426T110000
                SUMMARY:Planning
                ATTENDEE;CN=Ada Lovelace;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED:mailto:ada@example.com
                END:VEVENT
                END:VCALENDAR
                """);

        assertThat(response.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-04-25T09:00:00Z"));
        assertThat(response.attendees()).singleElement().satisfies(attendee -> {
            assertThat(attendee.name()).isEqualTo("Ada Lovelace");
            assertThat(attendee.email()).isEqualTo("ada@example.com");
            assertThat(attendee.role()).isEqualTo("req-participant");
            assertThat(attendee.responseStatus()).isEqualTo("accepted");
        });
        assertThat(response.providerRef().provider()).isEqualTo("weave-calendar-facade");
        assertThat(response.providerRef().objectKind()).isEqualTo("calendar-event");
        assertThat(response.providerRef().opaqueId()).isEqualTo("opaque-event-id");
        assertThat(response.providerRef().etag()).isEqualTo("\"etag-1\"");
        assertThat(response.providerRef().rawProviderPathExposed()).isFalse();
    }

    @Test
    void preservesAllDayDateSemantics() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Release day",
                null,
                OffsetDateTime.parse("2026-04-26T00:00:00+02:00"),
                OffsetDateTime.parse("2026-04-27T00:00:00+02:00"),
                "Europe/Berlin",
                null,
                true);

        String icalendar = mapper.toIcalendar(mapper.draftFrom(request));
        IcalendarMapper.EventDraft parsed = mapper.parse(icalendar);

        assertThat(icalendar).contains("DTSTART;VALUE=DATE:20260426");
        assertThat(icalendar).contains("DTEND;VALUE=DATE:20260427");
        assertThat(parsed.allDay()).isTrue();
        assertThat(parsed.startsAt().toLocalDate()).isEqualTo(request.startsAt().toLocalDate());
        assertThat(parsed.endsAt().toLocalDate()).isEqualTo(request.endsAt().toLocalDate());
    }

    @Test
    void acceptsCommonGermanTimezoneAliasesWhenWritingIcalendar() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Dogfood check",
                null,
                OffsetDateTime.parse("2026-06-27T10:00:00+02:00"),
                OffsetDateTime.parse("2026-06-27T10:30:00+02:00"),
                "CEST",
                null,
                false);

        String icalendar = mapper.toIcalendar(mapper.draftFrom(request));

        assertThat(icalendar).contains("DTSTART;TZID=CEST:20260627T100000");
        assertThat(mapper.parse(icalendar).startsAt())
                .isEqualTo(OffsetDateTime.parse("2026-06-27T10:00:00+02:00"));
    }

    @Test
    void reportsUnsupportedTimezonesAsInvalidRequests() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Dogfood check",
                null,
                OffsetDateTime.parse("2026-06-27T10:00:00+02:00"),
                OffsetDateTime.parse("2026-06-27T10:30:00+02:00"),
                "Mars/Phobos",
                null,
                false);

        assertThatThrownBy(() -> mapper.toIcalendar(mapper.draftFrom(request)))
                .isInstanceOfSatisfying(CalendarAdapterException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(CalendarAdapterException.Type.INVALID_REQUEST);
                    assertThat(exception.details()).containsEntry("field", "timezone");
                    assertThat(exception.details()).containsEntry("supportSafeReason", "invalid-timezone");
                });
    }

    @Test
    void mapsBoundedRecurrenceAndPreservesLocalWallClockAcrossDst() {
        // CALDAV_RECURRENCE_DST_FACADE
        var event = mapper.parse(
                new CalendarId("calendar:user-1"),
                CalendarScope.workspace(),
                new EventVersion("\"etag-1\""),
                """
                        BEGIN:VCALENDAR
                        BEGIN:VEVENT
                        UID:weekly-planning
                        DTSTART;TZID=Europe/Berlin:20260322T090000
                        DTEND;TZID=Europe/Berlin:20260322T100000
                        SUMMARY:Planning
                        RRULE:FREQ=WEEKLY;COUNT=3
                        RDATE;TZID=Europe/Berlin:20260412T090000
                        EXDATE;TZID=Europe/Berlin:20260329T090000
                        END:VEVENT
                        END:VCALENDAR
                        """);

        var occurrences = event.occurrences(
                Instant.parse("2026-03-20T00:00:00Z"),
                Instant.parse("2026-04-20T00:00:00Z"));

        assertThat(event.recurrence().frequency().name()).isEqualTo("WEEKLY");
        assertThat(event.recurrence().count()).isEqualTo(3);
        assertThat(occurrences).extracting(occurrence -> occurrence.start().toLocalDate().toString())
                .containsExactly("2026-03-22", "2026-04-05", "2026-04-12");
        assertThat(occurrences).extracting(occurrence -> occurrence.start().getHour())
                .containsOnly(9);
        assertThat(occurrences.get(0).start().getOffset().toString()).isEqualTo("+01:00");
        assertThat(occurrences.get(1).start().getOffset().toString()).isEqualTo("+02:00");

        String rendered = mapper.toIcalendar(event);
        assertThat(rendered)
                .contains("RRULE:FREQ=WEEKLY;COUNT=3")
                .contains("RDATE;TZID=Europe/Berlin:20260412T090000")
                .contains("EXDATE;TZID=Europe/Berlin:20260329T090000");
    }

    @Test
    void rejectsUnboundedOrUnsupportedRecurrenceWithStableReason() {
        assertThatThrownBy(() -> mapper.parse("""
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:test-uid
                DTSTART;TZID=Europe/Berlin:20260426T100000
                DTEND;TZID=Europe/Berlin:20260426T110000
                SUMMARY:Planning
                RRULE:FREQ=MONTHLY
                END:VEVENT
                END:VCALENDAR
                """))
                .isInstanceOfSatisfying(CalendarAdapterException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(CalendarAdapterException.Type.INVALID_REQUEST);
                    assertThat(exception.details()).containsEntry("errorCode", "caldav-recurrence-unsupported");
                    assertThat(exception.details()).containsEntry("supportSafe", true);
                });
    }
}
