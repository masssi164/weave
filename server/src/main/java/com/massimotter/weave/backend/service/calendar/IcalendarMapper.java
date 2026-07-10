package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.model.calendar.CalendarAttendeeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarProviderRefResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class IcalendarMapper {

    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, String> TIMEZONE_ALIASES = Map.of(
            "CEST", "Europe/Berlin",
            "CET", "Europe/Berlin",
            "MESZ", "Europe/Berlin",
            "MEZ", "Europe/Berlin",
            "GMT", "UTC",
            "UTC", "UTC",
            "Z", "UTC");

    public EventDraft draftFrom(CreateCalendarEventRequest request) {
        return new EventDraft(
                UUID.randomUUID() + "@weave.test",
                request.title(),
                blankToNull(request.description()),
                request.startsAt(),
                request.endsAt(),
                request.timezone(),
                blankToNull(request.location()),
                request.allDay(),
                List.of(),
                null,
                null);
    }

    public EventDraft merge(EventDraft existing, UpdateCalendarEventRequest request) {
        return new EventDraft(
                existing.uid(),
                request.title() == null ? existing.title() : request.title(),
                request.description() == null ? existing.description() : blankToNull(request.description()),
                request.startsAt() == null ? existing.startsAt() : request.startsAt(),
                request.endsAt() == null ? existing.endsAt() : request.endsAt(),
                request.timezone() == null || request.timezone().isBlank() ? existing.timezone() : request.timezone(),
                request.location() == null ? existing.location() : blankToNull(request.location()),
                request.allDay() == null ? existing.allDay() : request.allDay(),
                existing.attendees(),
                existing.recurrence(),
                existing.updatedAt());
    }

    public String toIcalendar(EventDraft event) {
        StringBuilder builder = new StringBuilder();
        builder.append("BEGIN:VCALENDAR\r\n");
        builder.append("VERSION:2.0\r\n");
        builder.append("PRODID:-//Weave//Calendar Facade//EN\r\n");
        builder.append("CALSCALE:GREGORIAN\r\n");
        builder.append("BEGIN:VEVENT\r\n");
        builder.append("UID:").append(escape(event.uid())).append("\r\n");
        builder.append("DTSTAMP:").append(UTC_FORMAT.format(OffsetDateTime.now(ZoneOffset.UTC))).append("\r\n");
        if (event.updatedAt() != null) {
            builder.append("LAST-MODIFIED:").append(UTC_FORMAT.format(event.updatedAt())).append("\r\n");
        }
        appendDateTime(builder, "DTSTART", event.startsAt(), event.timezone(), event.allDay());
        appendDateTime(builder, "DTEND", event.endsAt(), event.timezone(), event.allDay());
        appendText(builder, "SUMMARY", event.title());
        appendText(builder, "DESCRIPTION", event.description());
        appendText(builder, "LOCATION", event.location());
        appendRecurrence(builder, event.recurrence(), event.timezone(), event.allDay());
        event.attendees().forEach(attendee -> appendAttendee(builder, attendee));
        builder.append("END:VEVENT\r\n");
        builder.append("END:VCALENDAR\r\n");
        return builder.toString();
    }

    public CalendarEventResponse toResponse(String id, String etag, String calendarData) {
        List<Property> eventProperties = eventProperties(calendarData);
        EventDraft draft = parse(calendarData);
        OffsetDateTime updatedAt = updatedAt(eventProperties);
        return new CalendarEventResponse(
                id,
                draft.title(),
                draft.description(),
                draft.startsAt(),
                draft.endsAt(),
                draft.timezone(),
                draft.location(),
                draft.allDay(),
                cleanEtag(etag),
                CalendarScopeResponse.workspace(),
                null,
                draft.attendees().stream()
                        .map(attendee -> new CalendarAttendeeResponse(
                                attendee.displayName(),
                                attendee.address(),
                                attendee.role(),
                                attendee.response()))
                        .toList(),
                CalendarProviderRefResponse.caldavEvent(id, cleanEtag(etag), updatedAt),
                updatedAt);
    }

    public EventDraft parse(String calendarData) {
        List<Property> eventProperties = eventProperties(calendarData);
        Map<String, Property> properties = new LinkedHashMap<>();
        for (Property property : eventProperties) {
            properties.putIfAbsent(property.name(), property);
        }

        Property uid = properties.get("UID");
        Property start = properties.get("DTSTART");
        Property end = properties.get("DTEND");
        if (uid == null || start == null || end == null) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.INVALID_RESPONSE,
                    "CalDAV event did not contain required UID, DTSTART, and DTEND fields.");
        }

        String timezone = timezone(start);
        boolean allDay = "DATE".equalsIgnoreCase(start.params().get("VALUE"));
        return new EventDraft(
                unescape(uid.value()),
                valueOrDefault(unescape(value(properties, "SUMMARY")), "Untitled event"),
                blankToNull(unescape(value(properties, "DESCRIPTION"))),
                parseDateTime(start, timezone),
                parseDateTime(end, timezone),
                timezone,
                blankToNull(unescape(value(properties, "LOCATION"))),
                allDay,
                attendees(eventProperties),
                recurrence(eventProperties, timezone),
                updatedAt(eventProperties));
    }

    public CalendarEvent parse(
            CalendarId calendarId,
            CalendarScope scope,
            EventVersion version,
            String calendarData) {
        EventDraft draft = parse(calendarData);
        ZoneId timezone = zoneId(draft.timezone());
        return new CalendarEvent(
                calendarId,
                new EventId(draft.uid()),
                scope,
                draft.title(),
                draft.description(),
                draft.startsAt().atZoneSameInstant(timezone).toLocalDateTime(),
                draft.endsAt().atZoneSameInstant(timezone).toLocalDateTime(),
                timezone,
                draft.allDay(),
                draft.location(),
                draft.attendees(),
                draft.recurrence(),
                version,
                draft.updatedAt() == null ? null : draft.updatedAt().toInstant());
    }

    public String toIcalendar(CalendarEvent event) {
        return toIcalendar(new EventDraft(
                event.id().value(),
                event.title(),
                event.description(),
                event.startsAt().toOffsetDateTime(),
                event.endsAt().toOffsetDateTime(),
                event.timezone().getId(),
                event.location(),
                event.allDay(),
                event.attendees(),
                event.recurrence(),
                event.updatedAt() == null ? null : OffsetDateTime.ofInstant(event.updatedAt(), ZoneOffset.UTC)));
    }

    private List<Property> eventProperties(String calendarData) {
        List<String> lines = unfold(calendarData);
        boolean inEvent = false;
        List<Property> properties = new ArrayList<>();
        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line)) {
                inEvent = true;
                continue;
            }
            if ("END:VEVENT".equalsIgnoreCase(line)) {
                break;
            }
            if (!inEvent || !line.contains(":")) {
                continue;
            }
            properties.add(Property.parse(line));
        }
        return properties;
    }

    private List<Attendee> attendees(List<Property> properties) {
        return properties.stream()
                .filter(property -> "ATTENDEE".equals(property.name()))
                .map(property -> new Attendee(
                        null,
                        unquote(unescape(property.params().get("CN"))),
                        mailToEmail(unescape(property.value())),
                        property.params().get("ROLE"),
                        property.params().get("PARTSTAT")))
                .toList();
    }

    private RecurrenceSet recurrence(List<Property> properties, String eventTimezone) {
        List<Property> rules = properties.stream()
                .filter(property -> "RRULE".equals(property.name()))
                .toList();
        boolean hasRecurrenceDates = properties.stream()
                .anyMatch(property -> "RDATE".equals(property.name()) || "EXDATE".equals(property.name()));
        if (rules.isEmpty()) {
            if (hasRecurrenceDates) {
                throw recurrenceUnsupported("RDATE and EXDATE require a bounded RRULE.");
            }
            return null;
        }
        if (rules.size() != 1) {
            throw recurrenceUnsupported("Exactly one RRULE is supported per event.");
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String part : rules.get(0).value().split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                throw recurrenceUnsupported("RRULE contains an invalid component.");
            }
            values.put(part.substring(0, separator).toUpperCase(Locale.ROOT), part.substring(separator + 1));
        }
        List<String> unsupported = values.keySet().stream()
                .filter(key -> !List.of("FREQ", "INTERVAL", "COUNT", "UNTIL").contains(key))
                .sorted()
                .toList();
        if (!unsupported.isEmpty()) {
            throw recurrenceUnsupported("RRULE components are not supported: " + String.join(", ", unsupported));
        }

        RecurrenceFrequency frequency;
        try {
            frequency = RecurrenceFrequency.valueOf(values.getOrDefault("FREQ", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw recurrenceUnsupported("Only DAILY and WEEKLY RRULE frequencies are supported.");
        }
        int interval = parsePositiveInteger(values.getOrDefault("INTERVAL", "1"), "INTERVAL");
        Integer count = values.containsKey("COUNT") ? parsePositiveInteger(values.get("COUNT"), "COUNT") : null;
        ZonedDateTime until = values.containsKey("UNTIL")
                ? parseRecurrenceDate(values.get("UNTIL"), eventTimezone, false)
                : null;
        if ((count == null) == (until == null)) {
            throw recurrenceUnsupported("RRULE requires exactly one COUNT or UNTIL bound.");
        }
        try {
            return new RecurrenceSet(
                    frequency,
                    interval,
                    count,
                    until,
                    recurrenceDates(properties, "RDATE", eventTimezone),
                    recurrenceDates(properties, "EXDATE", eventTimezone));
        } catch (IllegalArgumentException exception) {
            throw recurrenceUnsupported(exception.getMessage());
        }
    }

    private List<ZonedDateTime> recurrenceDates(
            List<Property> properties,
            String propertyName,
            String eventTimezone) {
        List<ZonedDateTime> dates = new ArrayList<>();
        properties.stream()
                .filter(property -> propertyName.equals(property.name()))
                .forEach(property -> {
                    String timezone = valueOrDefault(property.params().get("TZID"), eventTimezone);
                    boolean dateOnly = "DATE".equalsIgnoreCase(property.params().get("VALUE"));
                    for (String value : property.value().split(",")) {
                        dates.add(parseRecurrenceDate(value, timezone, dateOnly));
                    }
                });
        return List.copyOf(dates);
    }

    private ZonedDateTime parseRecurrenceDate(String value, String timezone, boolean dateOnly) {
        try {
            if (dateOnly || value.length() == 8) {
                return LocalDate.parse(value, DATE_FORMAT).atStartOfDay(zoneId(timezone));
            }
            if (value.endsWith("Z")) {
                return LocalDateTime.parse(value.substring(0, value.length() - 1), LOCAL_DATE_TIME_FORMAT)
                        .atOffset(ZoneOffset.UTC)
                        .toZonedDateTime();
            }
            return LocalDateTime.parse(value, LOCAL_DATE_TIME_FORMAT).atZone(zoneId(timezone));
        } catch (RuntimeException exception) {
            if (exception instanceof CalendarAdapterException adapterException) {
                throw adapterException;
            }
            throw recurrenceUnsupported("Recurrence date-time is invalid.");
        }
    }

    private int parsePositiveInteger(String value, String field) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw recurrenceUnsupported(field + " must be a positive integer.");
        }
    }

    private CalendarAdapterException recurrenceUnsupported(String reason) {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.INVALID_REQUEST,
                "Calendar recurrence is outside the supported bounded profile.",
                Map.of(
                        "module", "calendar",
                        "operation", "map-recurrence",
                        "errorCode", "caldav-recurrence-unsupported",
                        "reason", reason,
                        "supportSafe", true));
    }

    private void appendRecurrence(
            StringBuilder builder,
            RecurrenceSet recurrence,
            String timezone,
            boolean allDay) {
        if (recurrence == null) {
            return;
        }
        builder.append("RRULE:FREQ=").append(recurrence.frequency().name());
        if (recurrence.interval() != 1) {
            builder.append(";INTERVAL=").append(recurrence.interval());
        }
        if (recurrence.count() != null) {
            builder.append(";COUNT=").append(recurrence.count());
        } else {
            builder.append(";UNTIL=").append(UTC_FORMAT.format(recurrence.until()));
        }
        builder.append("\r\n");
        appendRecurrenceDates(builder, "RDATE", recurrence.additionalDates(), timezone, allDay);
        appendRecurrenceDates(builder, "EXDATE", recurrence.excludedDates(), timezone, allDay);
    }

    private void appendRecurrenceDates(
            StringBuilder builder,
            String name,
            List<ZonedDateTime> values,
            String timezone,
            boolean allDay) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(name);
        if (allDay) {
            builder.append(";VALUE=DATE:");
            builder.append(values.stream()
                    .map(value -> DATE_FORMAT.format(value.withZoneSameInstant(zoneId(timezone)).toLocalDate()))
                    .collect(java.util.stream.Collectors.joining(",")));
        } else {
            builder.append(";TZID=").append(timezone).append(":");
            builder.append(values.stream()
                    .map(value -> LOCAL_DATE_TIME_FORMAT.format(value.withZoneSameInstant(zoneId(timezone)).toLocalDateTime()))
                    .collect(java.util.stream.Collectors.joining(",")));
        }
        builder.append("\r\n");
    }

    private void appendAttendee(StringBuilder builder, Attendee attendee) {
        String address = attendee.address() == null
                ? "urn:weave:member:" + attendee.memberRef()
                : "mailto:" + attendee.address();
        builder.append("ATTENDEE");
        if (attendee.displayName() != null) {
            builder.append(";CN=\"").append(attendee.displayName().replace("\"", "'")).append("\"");
        }
        if (attendee.response() != null) {
            builder.append(";PARTSTAT=").append(attendee.response().toUpperCase(Locale.ROOT));
        }
        if (attendee.role() != null) {
            builder.append(";ROLE=").append(attendee.role().toUpperCase(Locale.ROOT));
        }
        builder.append(":").append(escape(address)).append("\r\n");
    }

    private OffsetDateTime updatedAt(List<Property> properties) {
        Property property = properties.stream()
                .filter(candidate -> "LAST-MODIFIED".equals(candidate.name()))
                .findFirst()
                .orElseGet(() -> properties.stream()
                        .filter(candidate -> "DTSTAMP".equals(candidate.name()))
                        .findFirst()
                        .orElse(null));
        if (property == null || property.value() == null || property.value().isBlank()) {
            return null;
        }
        return parseTimestamp(property.value());
    }

    private OffsetDateTime parseTimestamp(String value) {
        if (value.endsWith("Z")) {
            return LocalDateTime.parse(value.substring(0, value.length() - 1), LOCAL_DATE_TIME_FORMAT)
                    .atOffset(ZoneOffset.UTC);
        }
        return LocalDateTime.parse(value, LOCAL_DATE_TIME_FORMAT).atOffset(ZoneOffset.UTC);
    }

    private void appendDateTime(StringBuilder builder, String name, OffsetDateTime value, String timezone, boolean allDay) {
        if (allDay) {
            builder.append(name).append(";VALUE=DATE:").append(DATE_FORMAT.format(value.toLocalDate())).append("\r\n");
            return;
        }
        builder.append(name);
        if (timezone != null && !timezone.isBlank()) {
            builder.append(";TZID=").append(timezone);
        }
        ZonedDateTime zoned = value.atZoneSameInstant(zoneId(timezone));
        builder.append(":").append(LOCAL_DATE_TIME_FORMAT.format(zoned.toLocalDateTime())).append("\r\n");
    }

    private void appendText(StringBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(name).append(":").append(escape(value)).append("\r\n");
        }
    }

    private OffsetDateTime parseDateTime(Property property, String timezone) {
        if ("DATE".equalsIgnoreCase(property.params().get("VALUE"))) {
            LocalDate date = LocalDate.parse(property.value(), DATE_FORMAT);
            return date.atStartOfDay(zoneId(timezone)).toOffsetDateTime();
        }
        if (property.value().endsWith("Z")) {
            return LocalDateTime.parse(property.value().substring(0, property.value().length() - 1), LOCAL_DATE_TIME_FORMAT)
                    .atOffset(ZoneOffset.UTC);
        }
        return LocalDateTime.parse(property.value(), LOCAL_DATE_TIME_FORMAT)
                .atZone(zoneId(timezone))
                .toOffsetDateTime();
    }

    private ZoneId zoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        String normalized = timezone.trim();
        String alias = TIMEZONE_ALIASES.get(normalized.toUpperCase(Locale.ROOT));
        try {
            return ZoneId.of(alias == null ? normalized : alias);
        } catch (RuntimeException exception) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.INVALID_REQUEST,
                    "Calendar event timezone is not supported.",
                    Map.of(
                            "field", "timezone",
                            "value", normalized,
                            "supportSafeReason", "invalid-timezone"),
                    exception);
        }
    }

    private String timezone(Property property) {
        String tzid = property.params().get("TZID");
        return tzid == null || tzid.isBlank() ? "UTC" : tzid;
    }

    private List<String> unfold(String calendarData) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : calendarData.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if ((rawLine.startsWith(" ") || rawLine.startsWith("\t")) && !lines.isEmpty()) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + rawLine.substring(1));
            } else if (!rawLine.isBlank()) {
                lines.add(rawLine);
            }
        }
        return lines;
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace(";", "\\;")
                .replace(",", "\\,");
    }

    private static String unescape(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (char current : value.toCharArray()) {
            if (escaped) {
                if (current == 'n' || current == 'N') {
                    builder.append('\n');
                } else {
                    builder.append(current);
                }
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else {
                builder.append(current);
            }
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static String mailToEmail(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, "mailto:", 0, "mailto:".length())) {
            return trimmed.substring("mailto:".length());
        }
        return trimmed;
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String value(Map<String, Property> properties, String name) {
        Property property = properties.get(name);
        return property == null ? null : property.value();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String cleanEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            return null;
        }
        return etag.trim();
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
            RecurrenceSet recurrence,
            OffsetDateTime updatedAt) {

        public EventDraft(
                String uid,
                String title,
                String description,
                OffsetDateTime startsAt,
                OffsetDateTime endsAt,
                String timezone,
                String location,
                boolean allDay) {
            this(uid, title, description, startsAt, endsAt, timezone, location, allDay, List.of(), null, null);
        }

        public EventDraft {
            attendees = attendees == null ? List.of() : List.copyOf(attendees);
        }
    }

    private record Property(String name, Map<String, String> params, String value) {
        static Property parse(String line) {
            int separator = line.indexOf(':');
            String metadata = line.substring(0, separator);
            String value = line.substring(separator + 1);
            String[] parts = metadata.split(";");
            String name = parts[0].toUpperCase(Locale.ROOT);
            Map<String, String> params = new LinkedHashMap<>();
            for (int index = 1; index < parts.length; index++) {
                int equals = parts[index].indexOf('=');
                if (equals > 0) {
                    params.put(
                            parts[index].substring(0, equals).toUpperCase(Locale.ROOT),
                            parts[index].substring(equals + 1));
                }
            }
            return new Property(name, params, value);
        }
    }
}
