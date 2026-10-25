package com.massimotter.weave.backend.service.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class IcalendarMapperTest {

    private static final String CALDAV_RECURRENCE_DST_FACADE = "CALDAV_RECURRENCE_DST_FACADE";
    private static final String CALDAV_CANONICAL_THREAD_PROJECTION = "CALDAV_CANONICAL_THREAD_PROJECTION";

    private final IcalendarMapper mapper = new IcalendarMapper();
    private final RecurrenceEngine recurrenceEngine = new Ical4jRecurrenceEngine();

    @Test
    void mapsCreateRequestToIcalendarAndBackThroughIcal4j() {
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
    void addsCanonicalThreadMetadataOnlyToNorthboundIcalendar() {
        CalendarScope scope = new CalendarScope(ScopeType.CHANNEL, "engineering", "engineering-general");
        var event = mapper.parse(
                new CalendarId("calendar:user-1"),
                scope,
                new EventVersion("\"etag-1\""),
                """
                        BEGIN:VCALENDAR
                        VERSION:2.0
                        PRODID:-//Weave Test//EN
                        BEGIN:VEVENT
                        UID:planning
                        DTSTAMP:20260425T080000Z
                        DTSTART:20260426T090000Z
                        DTEND:20260426T100000Z
                        SUMMARY:Planning
                        END:VEVENT
                        END:VCALENDAR
                        """);
        CalendarScopeResponse projectionScope = CalendarScopeResponse.channel(
                "engineering", "engineering-general", "Engineering / general channel calendar");

        String northbound = mapper.toNorthboundIcalendar(event, projectionScope);

        assertThat(CALDAV_CANONICAL_THREAD_PROJECTION).isNotBlank();
        assertThat(northbound)
                .contains("X-WEAVE-CONTEXT-ID:channel-engineering-general")
                .contains("X-WEAVE-CHANNEL-ID:engineering-general")
                .containsPattern("X-WEAVE-MEETING-THREAD-ID:meeting:channel-engineering-general:[0-9a-f]{12}");
        assertThat(mapper.toIcalendar(event)).doesNotContain("X-WEAVE-");
    }

    @Test
    void mergesUpdateRequestWithoutLeakingCalDavFieldsToApiDto() {
        IcalendarMapper.EventDraft existing = mapper.parse("""
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Weave Test//EN
                BEGIN:VEVENT
                UID:test-uid
                DTSTAMP:20260425T080000Z
                DTSTART;TZID=Europe/Berlin:20260426T100000
                DTEND;TZID=Europe/Berlin:20260426T110000
                SUMMARY:Planning
                DESCRIPTION:Original
                END:VEVENT
                END:VCALENDAR
                """);
        UpdateCalendarEventRequest update = new UpdateCalendarEventRequest(
                "Updated", null, null,
                OffsetDateTime.parse("2026-04-26T12:00:00+02:00"),
                null, "Remote", null, "etag");

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
                VERSION:2.0
                PRODID:-//Weave Test//EN
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
        assertThat(response.providerRef().rawProviderPathExposed()).isFalse();
    }

    @Test
    void preservesAllDayDateSemantics() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Release day", null,
                OffsetDateTime.parse("2026-04-26T00:00:00+02:00"),
                OffsetDateTime.parse("2026-04-27T00:00:00+02:00"),
                "Europe/Berlin", null, true);

        String icalendar = mapper.toIcalendar(mapper.draftFrom(request));
        IcalendarMapper.EventDraft parsed = mapper.parse(icalendar);

        assertThat(icalendar).contains("DTSTART;VALUE=DATE:20260426");
        assertThat(icalendar).contains("DTEND;VALUE=DATE:20260427");
        assertThat(parsed.allDay()).isTrue();
        assertThat(parsed.startsAt().toLocalDate()).isEqualTo(request.startsAt().toLocalDate());
        assertThat(parsed.endsAt().toLocalDate()).isEqualTo(request.endsAt().toLocalDate());
    }

    @Test
    void normalizesCommonTimezoneAliasesAtTheApplicationBoundary() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Dogfood check", null,
                OffsetDateTime.parse("2026-06-27T10:00:00+02:00"),
                OffsetDateTime.parse("2026-06-27T10:30:00+02:00"),
                "CEST", null, false);

        IcalendarMapper.EventDraft draft = mapper.draftFrom(request);
        String icalendar = mapper.toIcalendar(draft);

        assertThat(draft.timezone()).isEqualTo("Europe/Berlin");
        assertThat(icalendar).contains("DTSTART;TZID=Europe/Berlin:20260627T100000");
    }

    @Test
    void reportsUnsupportedTimezonesAsInvalidRequests() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Dogfood check", null,
                OffsetDateTime.parse("2026-06-27T10:00:00+02:00"),
                OffsetDateTime.parse("2026-06-27T10:30:00+02:00"),
                "Mars/Phobos", null, false);

        assertThatThrownBy(() -> mapper.draftFrom(request))
                .isInstanceOfSatisfying(CalendarAdapterException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(CalendarAdapterException.Type.INVALID_REQUEST);
                    assertThat(exception.details()).containsEntry("field", "timezone");
                    assertThat(exception.details()).containsEntry("supportSafeReason", "invalid-timezone");
                });
    }

    @Test
    void recurrenceIsParsedByIcal4jAndExpandedOnlyThroughRecurrenceEngine() {
        var event = mapper.parse(
                new CalendarId("calendar:user-1"),
                CalendarScope.workspace(),
                new EventVersion("\"etag-1\""),
                """
                        BEGIN:VCALENDAR
                        VERSION:2.0
                        PRODID:-//Weave Test//EN
                        BEGIN:VEVENT
                        UID:weekly-planning
                        DTSTAMP:20260320T080000Z
                        DTSTART;TZID=Europe/Berlin:20260322T090000
                        DTEND;TZID=Europe/Berlin:20260322T100000
                        SUMMARY:Planning
                        RRULE:FREQ=WEEKLY;COUNT=3
                        RDATE;TZID=Europe/Berlin:20260412T090000
                        EXDATE;TZID=Europe/Berlin:20260329T090000
                        END:VEVENT
                        END:VCALENDAR
                        """);

        var starts = recurrenceEngine.zoned(
                event.recurrence().rrule(),
                event.startValue().localDateTime().atZone(event.startValue().zoneId()),
                Instant.parse("2026-03-20T00:00:00Z").atZone(ZoneId.of("Europe/Berlin")),
                Instant.parse("2026-04-20T00:00:00Z").atZone(ZoneId.of("Europe/Berlin")),
                100);

        assertThat(CALDAV_RECURRENCE_DST_FACADE).isNotBlank();
        assertThat(event.startValue().localDateTime().getHour()).isEqualTo(9);
        assertThat(event.recurrence().frequency().name()).isEqualTo("WEEKLY");
        assertThat(event.recurrence().count()).isEqualTo(3);
        assertThat(starts).extracting(value -> value.toLocalDate().toString())
                .contains("2026-03-22", "2026-03-29", "2026-04-05");
        assertThat(starts).extracting(value -> value.getHour()).containsOnly(9);
        assertThat(event.recurrence().additionalDates()).singleElement().satisfies(value ->
                assertThat(value.zoneId().getId()).isEqualTo("Europe/Berlin"));
        assertThat(event.recurrence().excludedDates()).singleElement();
    }

    @Test
    void acceptsMonthlyYearlyAndByRulePartsWithoutRequiringSeriesBound() {
        var event = mapper.parse(
                new CalendarId("calendar:user-1"),
                CalendarScope.workspace(),
                EventVersion.unknown(),
                """
                        BEGIN:VCALENDAR
                        VERSION:2.0
                        PRODID:-//Weave Test//EN
                        BEGIN:VEVENT
                        UID:monthly
                        DTSTAMP:20260425T080000Z
                        DTSTART;TZID=Europe/Berlin:20260426T100000
                        DTEND;TZID=Europe/Berlin:20260426T110000
                        SUMMARY:Planning
                        RRULE:FREQ=MONTHLY;BYDAY=MO,TU;BYSETPOS=1;WKST=MO
                        END:VEVENT
                        END:VCALENDAR
                        """);

        assertThat(event.recurrence().frequency().name()).isEqualTo("MONTHLY");
        assertThat(event.recurrence().byDay()).containsExactly("MO", "TU");
        assertThat(event.recurrence().bySetPos()).containsExactly(1);
        assertThat(event.recurrence().weekStart()).isEqualTo("MO");
    }
}
