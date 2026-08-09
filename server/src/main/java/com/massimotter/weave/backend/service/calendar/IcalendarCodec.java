package com.massimotter.weave.backend.service.calendar;

/**
 * Standards boundary for RFC 5545 syntax.
 *
 * <p>Canonical Calendar code consumes plain Weave/java.time values. Library
 * model objects are intentionally confined to the adapter implementation.
 */
public interface IcalendarCodec {

    /** Parse, validate and serialize an iCalendar document using the standards library. */
    String normalize(String calendarData);
}
