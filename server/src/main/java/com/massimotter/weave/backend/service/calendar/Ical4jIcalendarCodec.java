package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalKind;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.validate.ValidationException;

/**
 * iCal4j-backed RFC 5545 adapter. iCal4j owns parsing, unfolding/folding,
 * escaping, property/parameter grammar and validation. This class only maps
 * validated standards values to provider-neutral Weave domain values.
 */
public final class Ical4jIcalendarCodec implements IcalendarCodec {

    static final int MAX_ICALENDAR_CHARS = 2_000_000;
    private static final int MAX_COMPONENTS = 1_000;
    private static final int MAX_PROPERTIES_PER_EVENT = 2_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    @Override
    public CalendarEvent decode(
            CalendarId calendarId,
            CalendarScope scope,
            EventVersion version,
            String calendarData) {
        var calendar = parseCalendar(calendarData);
        List<VEvent> events = calendar.getComponents(Component.VEVENT);
        if (events.isEmpty()) {
            throw invalid("iCalendar payload does not contain a VEVENT.", null);
        }
        if (events.size() > MAX_COMPONENTS) {
            throw invalid("iCalendar payload contains too many VEVENT components.", null);
        }

        VEvent master = events.stream()
                .filter(event -> property(event, Property.RECURRENCE_ID) == null)
                .findFirst()
                .orElseThrow(() -> invalid("iCalendar payload does not contain a master VEVENT.", null));
        requirePropertyBound(master);

        String uid = requiredValue(master, Property.UID, "UID");
        TemporalValue start = temporal(requiredProperty(master, Property.DTSTART, "DTSTART"));
        TemporalValue end = temporal(requiredProperty(master, Property.DTEND, "DTEND"));
        String title = value(master, Property.SUMMARY, "Untitled event");
        String description = nullableValue(master, Property.DESCRIPTION);
        String location = nullableValue(master, Property.LOCATION);
        Instant updatedAt = instantProperty(master, Property.LAST_MODIFIED);
        if (updatedAt == null) {
            updatedAt = instantProperty(master, Property.DTSTAMP);
        }

        List<Attendee> attendees = new ArrayList<>();
        for (Property attendee : properties(master, Property.ATTENDEE)) {
            attendees.add(new Attendee(
                    null,
                    parameter(attendee, "CN"),
                    calendarAddress(attendee.getValue()),
                    parameter(attendee, "ROLE"),
                    parameter(attendee, "PARTSTAT")));
        }

        RecurrenceSet recurrence = recurrence(master, start);
        List<RecurrenceOverride> overrides = events.stream()
                .filter(event -> property(event, Property.RECURRENCE_ID) != null)
                .map(event -> recurrenceOverride(event, start.kind()))
                .toList();

        return new CalendarEvent(
                calendarId,
                new EventId(uid),
                scope,
                title,
                description,
                start,
                end,
                location,
                attendees,
                recurrence,
                overrides,
                version,
                updatedAt);
    }

    @Override
    public String encode(CalendarEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("calendar event is required");
        }
        StringBuilder builder = new StringBuilder(1024);
        builder.append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//Weave//Calendar//EN\r\n")
                .append("CALSCALE:GREGORIAN\r\n");
        appendEvent(builder, event, null);
        for (RecurrenceOverride override : event.overrides()) {
            appendEvent(builder, event, override);
        }
        builder.append("END:VCALENDAR\r\n");
        // Round-trip through iCal4j so output folding/escaping/validation is
        // always owned by the standards library rather than this mapper.
        var calendar = parseCalendar(builder.toString());
        try {
            var writer = new StringWriter(builder.length() + 256);
            new CalendarOutputter(true).output(calendar, writer);
            return writer.toString();
        } catch (IOException | ValidationException exception) {
            throw invalid("iCalendar payload could not be serialized.", exception);
        }
    }

    private net.fortuna.ical4j.model.Calendar parseCalendar(String calendarData) {
        if (calendarData == null || calendarData.isBlank()) {
            throw invalid("iCalendar payload must not be blank.", null);
        }
        if (calendarData.length() > MAX_ICALENDAR_CHARS) {
            throw invalid("iCalendar payload exceeds the supported size limit.", null);
        }
        try {
            var calendar = new CalendarBuilder().build(new StringReader(calendarData));
            var validation = calendar.validate();
            if (validation.hasErrors()) {
                throw invalid("iCalendar payload failed RFC validation.", null);
            }
            return calendar;
        } catch (CalendarAdapterException exception) {
            throw exception;
        } catch (ParserException | IOException | IllegalArgumentException exception) {
            throw invalid("iCalendar payload is invalid.", exception);
        }
    }

    private RecurrenceSet recurrence(VEvent event, TemporalValue masterStart) {
        List<Property> rules = properties(event, Property.RRULE);
        List<TemporalValue> rdates = recurrenceDates(event, Property.RDATE, masterStart.kind());
        List<TemporalValue> exdates = recurrenceDates(event, Property.EXDATE, masterStart.kind());
        if (rules.isEmpty()) {
            if (!rdates.isEmpty() || !exdates.isEmpty()) {
                throw invalid("RDATE/EXDATE without a supported RRULE is outside the Weave Calendar profile.", null);
            }
            return null;
        }
        if (rules.size() != 1) {
            throw invalid("Exactly one RRULE is supported per VEVENT.", null);
        }
        String raw = rules.getFirst().getValue();
        Map<String, String> parts = new LinkedHashMap<>();
        for (String part : raw.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                throw invalid("RRULE contains an invalid component.", null);
            }
            parts.put(part.substring(0, separator).toUpperCase(Locale.ROOT), part.substring(separator + 1));
        }
        List<String> allowed = List.of("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "WKST");
        List<String> unsupported = parts.keySet().stream().filter(key -> !allowed.contains(key)).sorted().toList();
        if (!unsupported.isEmpty()) {
            throw invalid("RRULE contains unsupported components: " + String.join(", ", unsupported), null);
        }
        RecurrenceFrequency frequency;
        try {
            frequency = RecurrenceFrequency.valueOf(parts.getOrDefault("FREQ", ""));
        } catch (IllegalArgumentException exception) {
            throw invalid("RRULE frequency is outside the supported profile.", exception);
        }
        int interval = positiveInt(parts.getOrDefault("INTERVAL", "1"), "INTERVAL");
        Integer count = parts.containsKey("COUNT") ? positiveInt(parts.get("COUNT"), "COUNT") : null;
        ZonedDateTime until = parts.containsKey("UNTIL") ? recurrenceUntil(parts.get("UNTIL"), masterStart) : null;
        if (count != null && until != null) {
            throw invalid("RRULE cannot contain COUNT and UNTIL together.", null);
        }
        return new RecurrenceSet(
                frequency,
                interval,
                count,
                until,
                rdates,
                exdates,
                strings(parts.get("BYDAY")),
                integers(parts.get("BYMONTHDAY")),
                integers(parts.get("BYMONTH")),
                integers(parts.get("BYSETPOS")),
                parts.get("WKST"));
    }

    private List<TemporalValue> recurrenceDates(VEvent event, String propertyName, TemporalKind masterKind) {
        List<TemporalValue> result = new ArrayList<>();
        for (Property property : properties(event, propertyName)) {
            String tzid = parameter(property, Parameter.TZID);
            String valueType = parameter(property, Parameter.VALUE);
            for (String raw : property.getValue().split(",")) {
                TemporalValue value = temporal(raw, valueType, tzid);
                if (value.kind() != masterKind) {
                    throw invalid(propertyName + " temporal type must match DTSTART in the supported profile.", null);
                }
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private RecurrenceOverride recurrenceOverride(VEvent event, TemporalKind masterKind) {
        requirePropertyBound(event);
        TemporalValue recurrenceId = temporal(requiredProperty(event, Property.RECURRENCE_ID, "RECURRENCE-ID"));
        if (recurrenceId.kind() != masterKind) {
            throw invalid("RECURRENCE-ID temporal type must match DTSTART.", null);
        }
        String status = nullableValue(event, Property.STATUS);
        boolean cancelled = "CANCELLED".equalsIgnoreCase(status);
        TemporalValue start = cancelled ? null : temporal(requiredProperty(event, Property.DTSTART, "DTSTART"));
        TemporalValue end = cancelled ? null : temporal(requiredProperty(event, Property.DTEND, "DTEND"));
        return new RecurrenceOverride(
                recurrenceId,
                start,
                end,
                cancelled,
                nullableValue(event, Property.SUMMARY),
                nullableValue(event, Property.DESCRIPTION),
                nullableValue(event, Property.LOCATION));
    }

    private void appendEvent(StringBuilder out, CalendarEvent event, RecurrenceOverride override) {
        out.append("BEGIN:VEVENT\r\n");
        appendText(out, "UID", event.id().value());
        appendText(out, "DTSTAMP", formatUtc(event.updatedAt() == null ? Instant.now() : event.updatedAt()));
        if (override == null) {
            appendTemporal(out, "DTSTART", event.startValue());
            appendTemporal(out, "DTEND", event.endValue());
            appendText(out, "SUMMARY", event.title());
            appendOptionalText(out, "DESCRIPTION", event.description());
            appendOptionalText(out, "LOCATION", event.location());
            for (Attendee attendee : event.attendees()) {
                out.append("ATTENDEE");
                appendParameter(out, "CN", attendee.displayName());
                appendParameter(out, "ROLE", attendee.role());
                appendParameter(out, "PARTSTAT", attendee.response());
                out.append(':').append(calendarAddressValue(attendee.address(), attendee.memberRef())).append("\r\n");
            }
            if (event.recurrence() != null) {
                appendText(out, "RRULE", event.recurrence().rrule());
                appendRecurrenceValues(out, "RDATE", event.recurrence().additionalDates());
                appendRecurrenceValues(out, "EXDATE", event.recurrence().excludedDates());
            }
        } else {
            appendTemporal(out, "RECURRENCE-ID", override.recurrenceId());
            if (override.cancelled()) {
                appendText(out, "STATUS", "CANCELLED");
            } else {
                appendTemporal(out, "DTSTART", override.start());
                appendTemporal(out, "DTEND", override.end());
                appendOptionalText(out, "SUMMARY", override.title() == null ? event.title() : override.title());
                appendOptionalText(out, "DESCRIPTION", override.description());
                appendOptionalText(out, "LOCATION", override.location());
            }
        }
        out.append("END:VEVENT\r\n");
    }

    private void appendRecurrenceValues(StringBuilder out, String name, List<TemporalValue> values) {
        for (TemporalValue value : values) {
            appendTemporal(out, name, value);
        }
    }

    private void appendTemporal(StringBuilder out, String name, TemporalValue value) {
        switch (value.kind()) {
            case DATE -> out.append(name).append(";VALUE=DATE:").append(DATE.format(value.date())).append("\r\n");
            case FLOATING -> out.append(name).append(':').append(LOCAL.format(value.localDateTime())).append("\r\n");
            case UTC -> out.append(name).append(':').append(formatUtc(value.instant())).append("\r\n");
            case ZONED -> out.append(name).append(";TZID=").append(value.zoneId().getId()).append(':')
                    .append(LOCAL.format(value.localDateTime())).append("\r\n");
        }
    }

    private TemporalValue temporal(Property property) {
        return temporal(property.getValue(), parameter(property, Parameter.VALUE), parameter(property, Parameter.TZID));
    }

    private TemporalValue temporal(String raw, String valueType, String tzid) {
        try {
            if ("DATE".equalsIgnoreCase(valueType) || raw.length() == 8) {
                return TemporalValue.date(LocalDate.parse(raw, DATE));
            }
            if (raw.endsWith("Z")) {
                return TemporalValue.utc(LocalDateTime.parse(raw.substring(0, raw.length() - 1), LOCAL)
                        .toInstant(ZoneOffset.UTC));
            }
            LocalDateTime local = LocalDateTime.parse(raw, LOCAL);
            return tzid == null || tzid.isBlank()
                    ? TemporalValue.floating(local)
                    : TemporalValue.zoned(local, ZoneId.of(tzid));
        } catch (RuntimeException exception) {
            throw invalid("iCalendar temporal value is invalid.", exception);
        }
    }

    private ZonedDateTime recurrenceUntil(String raw, TemporalValue masterStart) {
        TemporalValue parsed = temporal(raw, raw.length() == 8 ? "DATE" : null,
                masterStart.kind() == TemporalKind.ZONED ? masterStart.zoneId().getId() : null);
        return switch (parsed.kind()) {
            case DATE -> parsed.date().atStartOfDay(masterStart.kind() == TemporalKind.ZONED ? masterStart.zoneId() : ZoneOffset.UTC);
            case FLOATING -> parsed.localDateTime().atZone(masterStart.kind() == TemporalKind.ZONED ? masterStart.zoneId() : ZoneOffset.UTC);
            case UTC -> parsed.instant().atZone(ZoneOffset.UTC);
            case ZONED -> parsed.localDateTime().atZone(parsed.zoneId());
        };
    }

    private Property requiredProperty(VEvent event, String name, String label) {
        Property property = property(event, name);
        if (property == null) {
            throw invalid("VEVENT is missing required " + label + ".", null);
        }
        return property;
    }

    private String requiredValue(VEvent event, String name, String label) {
        String value = nullableValue(event, name);
        if (value == null || value.isBlank()) {
            throw invalid("VEVENT is missing required " + label + ".", null);
        }
        return value;
    }

    private String value(VEvent event, String name, String fallback) {
        String value = nullableValue(event, name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nullableValue(VEvent event, String name) {
        Property property = property(event, name);
        return property == null ? null : property.getValue();
    }

    private Instant instantProperty(VEvent event, String name) {
        Property property = property(event, name);
        if (property == null) return null;
        TemporalValue value = temporal(property);
        return switch (value.kind()) {
            case UTC -> value.instant();
            case ZONED -> value.localDateTime().atZone(value.zoneId()).toInstant();
            case FLOATING -> value.localDateTime().toInstant(ZoneOffset.UTC);
            case DATE -> value.date().atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }

    @SuppressWarnings("unchecked")
    private List<Property> properties(VEvent event, String name) {
        return (List<Property>) (List<?>) event.getProperties(name);
    }

    private Property property(VEvent event, String name) {
        return event.getProperty(name).orElse(null);
    }

    private String parameter(Property property, String name) {
        var parameter = property.getParameter(name);
        return parameter.map(Parameter::getValue).orElse(null);
    }

    private void requirePropertyBound(VEvent event) {
        if (event.getProperties().size() > MAX_PROPERTIES_PER_EVENT) {
            throw invalid("VEVENT contains too many properties.", null);
        }
    }

    private int positiveInt(String raw, String name) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) throw new NumberFormatException(name);
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(name + " must be positive.", exception);
        }
    }

    private List<String> strings(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }

    private List<Integer> integers(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        try {
            return java.util.Arrays.stream(csv.split(",")).map(Integer::valueOf).toList();
        } catch (NumberFormatException exception) {
            throw invalid("RRULE integer list is invalid.", exception);
        }
    }

    private String calendarAddress(String value) {
        if (value == null) return null;
        return value.regionMatches(true, 0, "mailto:", 0, 7) ? value.substring(7) : value;
    }

    private String calendarAddressValue(String address, String memberRef) {
        String value = address != null && !address.isBlank() ? address : memberRef;
        if (value == null || value.isBlank()) {
            throw invalid("ATTENDEE requires an address or member reference.", null);
        }
        return value.contains(":") ? value : "mailto:" + value;
    }

    private void appendText(StringBuilder out, String name, String value) {
        out.append(name).append(':').append(escape(value)).append("\r\n");
    }

    private void appendOptionalText(StringBuilder out, String name, String value) {
        if (value != null && !value.isBlank()) appendText(out, name, value);
    }

    private void appendParameter(StringBuilder out, String name, String value) {
        if (value != null && !value.isBlank()) {
            out.append(';').append(name).append('=').append(quoteParameter(value));
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\r\n", "\\n").replace("\n", "\\n")
                .replace(",", "\\,").replace(";", "\\;");
    }

    private String quoteParameter(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return escaped.matches("[A-Za-z0-9._@+-]+") ? escaped : "\"" + escaped + "\"";
    }

    private String formatUtc(Instant value) {
        return LOCAL.format(LocalDateTime.ofInstant(value, ZoneOffset.UTC)) + "Z";
    }

    private static CalendarAdapterException invalid(String message, Throwable cause) {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.INVALID_REQUEST,
                message,
                Map.of(
                        "module", "calendar",
                        "operation", "icalendar-codec",
                        "errorCode", "icalendar-invalid",
                        "supportSafe", true),
                cause);
    }
}
