package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.model.calendar.CalendarAttendeeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarProviderRefResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarThreadRefResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Application/API mapper only. RFC 5545 syntax, recurrence grammar, escaping,
 * folding and temporal interpretation are delegated exclusively to
 * {@link IcalendarCodec} (iCal4j-backed).
 */
public class IcalendarMapper {

    private static final java.util.Map<String, String> TIMEZONE_ALIASES = java.util.Map.of(
            "CEST", "Europe/Berlin",
            "CET", "Europe/Berlin",
            "MESZ", "Europe/Berlin",
            "MEZ", "Europe/Berlin",
            "GMT", "UTC",
            "UTC", "UTC",
            "Z", "UTC");

    private final IcalendarCodec codec;

    public IcalendarMapper() {
        this(new Ical4jIcalendarCodec());
    }

    IcalendarMapper(IcalendarCodec codec) {
        this.codec = java.util.Objects.requireNonNull(codec, "codec");
    }

    public EventDraft draftFrom(CreateCalendarEventRequest request) {
        ZoneId zone = zoneId(request.timezone());
        return new EventDraft(
                UUID.randomUUID() + "@weave.test",
                request.title(),
                blankToNull(request.description()),
                request.startsAt(),
                request.endsAt(),
                zone.getId(),
                blankToNull(request.location()),
                request.allDay(),
                List.of(),
                null,
                null);
    }

    public EventDraft merge(EventDraft existing, UpdateCalendarEventRequest request) {
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? existing.timezone()
                : zoneId(request.timezone()).getId();
        return new EventDraft(
                existing.uid(),
                request.title() == null ? existing.title() : request.title(),
                request.description() == null ? existing.description() : blankToNull(request.description()),
                request.startsAt() == null ? existing.startsAt() : request.startsAt(),
                request.endsAt() == null ? existing.endsAt() : request.endsAt(),
                timezone,
                request.location() == null ? existing.location() : blankToNull(request.location()),
                request.allDay() == null ? existing.allDay() : request.allDay(),
                existing.attendees(),
                existing.recurrence(),
                existing.updatedAt());
    }

    public String toIcalendar(EventDraft draft) {
        return codec.encode(toCanonical(draft));
    }

    public EventDraft parse(String calendarData) {
        CalendarEvent event = codec.decode(
                new CalendarId("caldav-draft"),
                CalendarScope.workspace(),
                EventVersion.unknown(),
                calendarData);
        return toDraft(event);
    }

    public CalendarEvent parse(
            CalendarId calendarId,
            CalendarScope scope,
            EventVersion version,
            String calendarData) {
        return codec.decode(calendarId, scope, version, calendarData);
    }

    public String toIcalendar(CalendarEvent event) {
        return codec.encode(event);
    }

    public String toNorthboundIcalendar(CalendarEvent event, CalendarScopeResponse scope) {
        String encoded = codec.encode(event);
        CalendarThreadRefResponse thread = CalendarThreadRefResponse.forEvent(scope, event.id().value());
        StringBuilder extensions = new StringBuilder();
        appendExtension(extensions, "X-WEAVE-CONTEXT-ID", thread.contextId());
        appendExtension(extensions, "X-WEAVE-CHANNEL-ID", thread.channelId());
        appendExtension(extensions, "X-WEAVE-MEETING-THREAD-ID", thread.meetingThreadId());
        if (extensions.isEmpty()) return encoded;
        int insertion = encoded.indexOf("END:VEVENT");
        if (insertion < 0) return encoded;
        return encoded.substring(0, insertion) + extensions + encoded.substring(insertion);
    }

    public CalendarEventResponse toResponse(String id, String etag, String calendarData) {
        CalendarEvent event = codec.decode(
                new CalendarId("caldav-response"),
                CalendarScope.workspace(),
                new EventVersion(cleanEtag(etag)),
                calendarData);
        OffsetDateTime updatedAt = event.updatedAt() == null
                ? null
                : OffsetDateTime.ofInstant(event.updatedAt(), ZoneOffset.UTC);
        return new CalendarEventResponse(
                id,
                event.title(),
                event.description(),
                offset(event.startValue()),
                offset(event.endValue()),
                displayTimezone(event.startValue()),
                event.location(),
                event.allDay(),
                cleanEtag(etag),
                CalendarScopeResponse.workspace(),
                null,
                event.attendees().stream()
                        .map(attendee -> new CalendarAttendeeResponse(
                                attendee.displayName(),
                                attendee.address(),
                                lower(attendee.role()),
                                lower(attendee.response())))
                        .toList(),
                CalendarProviderRefResponse.caldavEvent(id, cleanEtag(etag), updatedAt),
                updatedAt);
    }

    private CalendarEvent toCanonical(EventDraft draft) {
        ZoneId zone = zoneId(draft.timezone());
        TemporalValue start = draft.allDay()
                ? TemporalValue.date(draft.startsAt().toLocalDate())
                : TemporalValue.zoned(draft.startsAt().atZoneSameInstant(zone).toLocalDateTime(), zone);
        TemporalValue end = draft.allDay()
                ? TemporalValue.date(draft.endsAt().toLocalDate())
                : TemporalValue.zoned(draft.endsAt().atZoneSameInstant(zone).toLocalDateTime(), zone);
        return new CalendarEvent(
                new CalendarId("caldav-draft"),
                new EventId(draft.uid()),
                CalendarScope.workspace(),
                draft.title(),
                draft.description(),
                start,
                end,
                draft.location(),
                draft.attendees(),
                draft.recurrence(),
                List.of(),
                EventVersion.unknown(),
                draft.updatedAt() == null ? Instant.now() : draft.updatedAt().toInstant());
    }

    private EventDraft toDraft(CalendarEvent event) {
        return new EventDraft(
                event.id().value(),
                event.title(),
                event.description(),
                offset(event.startValue()),
                offset(event.endValue()),
                displayTimezone(event.startValue()),
                event.location(),
                event.allDay(),
                event.attendees(),
                event.recurrence(),
                event.updatedAt() == null ? null : OffsetDateTime.ofInstant(event.updatedAt(), ZoneOffset.UTC));
    }

    private OffsetDateTime offset(TemporalValue value) {
        return switch (value.kind()) {
            case DATE -> value.date().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            case FLOATING -> value.localDateTime().atOffset(ZoneOffset.UTC);
            case UTC -> value.instant().atOffset(ZoneOffset.UTC);
            case ZONED -> value.localDateTime().atZone(value.zoneId()).toOffsetDateTime();
        };
    }

    private String displayTimezone(TemporalValue value) {
        return switch (value.kind()) {
            case ZONED -> value.zoneId().getId();
            case UTC -> "UTC";
            case DATE -> "UTC";
            case FLOATING -> null;
        };
    }

    private ZoneId zoneId(String value) {
        String normalized = value == null || value.isBlank() ? "UTC" : value.trim();
        normalized = TIMEZONE_ALIASES.getOrDefault(normalized.toUpperCase(java.util.Locale.ROOT), normalized);
        try {
            return ZoneId.of(normalized);
        } catch (RuntimeException exception) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.INVALID_REQUEST,
                    "Calendar timezone is invalid.",
                    java.util.Map.of(
                            "module", "calendar",
                            "field", "timezone",
                            "supportSafeReason", "invalid-timezone"),
                    exception);
        }
    }

    private void appendExtension(StringBuilder out, String name, String value) {
        if (value == null || value.isBlank()) return;
        out.append(name).append(':').append(value.replace("\r", "").replace("\n", "")).append("\r\n");
    }

    private String cleanEtag(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    public record EventDraft(
            String uid,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String timezone,
            String location,
            boolean allDay,
            List<Attendee> attendees,
            com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet recurrence,
            OffsetDateTime updatedAt) {
        public EventDraft {
            attendees = attendees == null ? List.of() : List.copyOf(attendees);
        }
    }
}
