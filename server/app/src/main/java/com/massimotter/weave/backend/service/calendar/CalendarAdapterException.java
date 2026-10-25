package com.massimotter.weave.backend.service.calendar;

import java.util.Map;

public class CalendarAdapterException extends RuntimeException {

    private final Type type;
    private final Map<String, Object> details;

    public CalendarAdapterException(Type type, String message) {
        this(type, message, Map.of(), null);
    }

    public CalendarAdapterException(Type type, String message, Map<String, Object> details) {
        this(type, message, details, null);
    }

    public CalendarAdapterException(Type type, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Type type() {
        return type;
    }

    public Map<String, Object> details() {
        return details;
    }

    public enum Type {
        NOT_CONFIGURED,
        INVALID_REQUEST,
        AUTH_FAILED,
        NOT_FOUND,
        CONFLICT,
        DOWNSTREAM_UNAVAILABLE,
        INVALID_RESPONSE
    }
}
