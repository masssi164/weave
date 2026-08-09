package com.massimotter.weave.backend.service.calendar;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.validate.ValidationException;

/** iCal4j-backed RFC 5545 syntax adapter. */
public final class Ical4jIcalendarCodec implements IcalendarCodec {

    static final int MAX_ICALENDAR_CHARS = 2_000_000;

    @Override
    public String normalize(String calendarData) {
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
            var writer = new StringWriter(calendarData.length() + 256);
            new CalendarOutputter(true).output(calendar, writer);
            return writer.toString();
        } catch (ParserException | IOException | ValidationException | IllegalArgumentException exception) {
            if (exception instanceof CalendarAdapterException adapterException) {
                throw adapterException;
            }
            throw invalid("iCalendar payload is invalid.", exception);
        }
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
