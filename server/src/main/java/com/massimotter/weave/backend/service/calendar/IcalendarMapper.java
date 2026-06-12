package com.massimotter.weave.backend.service.calendar;

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

    public EventDraft draftFrom(CreateCalendarEventRequest request) {
        return new EventDraft(
                UUID.randomUUID() + "@weave.test",
                request.title(),
                blankToNull(request.description()),
                request.startsAt(),
                request.endsAt(),
                request.timezone(),
                blankToNull(request.location()),
                request.allDay());
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
                request.allDay() == null ? existing.allDay() : request.allDay());
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
        appendDateTime(builder, "DTSTART", event.startsAt(), event.timezone(), event.allDay());
        appendDateTime(builder, "DTEND", event.endsAt(), event.timezone(), event.allDay());
        appendText(builder, "SUMMARY", event.title());
        appendText(builder, "DESCRIPTION", event.description());
        appendText(builder, "LOCATION", event.location());
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
                attendees(eventProperties),
                CalendarProviderRefResponse.caldavEvent(id, cleanEtag(etag), updatedAt),
                updatedAt);
    }

    public EventDraft parse(String calendarData) {
        List<Property> eventProperties = eventProperties(calendarData);
        Map<String, Property> properties = new LinkedHashMap<>();
        for (Property property : eventProperties) {
            properties.putIfAbsent(property.name(), property);
        }

        if (properties.containsKey("RRULE") || properties.containsKey("RDATE") || properties.containsKey("EXDATE")) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.INVALID_RESPONSE,
                    "Recurring CalDAV events are not yet represented by the Weave calendar facade.",
                    Map.of(
                            "module", "calendar",
                            "operation", "map-event",
                            "unsupportedFields", List.of("RRULE", "RDATE", "EXDATE"),
                            "supportSafeReason", "recurrence-not-yet-supported"));
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
                allDay);
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

    private List<CalendarAttendeeResponse> attendees(List<Property> properties) {
        return properties.stream()
                .filter(property -> "ATTENDEE".equals(property.name()))
                .map(property -> new CalendarAttendeeResponse(
                        unquote(unescape(property.params().get("CN"))),
                        mailToEmail(unescape(property.value())),
                        property.params().get("ROLE"),
                        property.params().get("PARTSTAT")))
                .toList();
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
        return ZoneId.of(timezone);
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
            boolean allDay) {
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
