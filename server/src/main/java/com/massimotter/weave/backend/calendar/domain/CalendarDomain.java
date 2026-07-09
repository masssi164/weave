package com.massimotter.weave.backend.calendar.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public final class CalendarDomain {

    private CalendarDomain() {
    }

    public enum ScopeType {
        WORKSPACE,
        TEAM,
        CHANNEL
    }

    public enum RecurrenceFrequency {
        DAILY,
        WEEKLY
    }

    public record CalendarId(String value) {
        public CalendarId {
            value = requireText(value, "calendar id");
        }
    }

    public record EventId(String value) {
        public EventId {
            value = requireText(value, "event id");
        }
    }

    public record EventVersion(String value) {
        public EventVersion {
            value = value == null || value.isBlank() ? null : value.trim();
        }

        public static EventVersion unknown() {
            return new EventVersion(null);
        }
    }

    public record CalendarScope(ScopeType type, String teamId, String channelId) {
        public CalendarScope {
            if (type == null) {
                throw new IllegalArgumentException("calendar scope type is required");
            }
            teamId = optionalText(teamId);
            channelId = optionalText(channelId);
            if (type == ScopeType.WORKSPACE && (teamId != null || channelId != null)) {
                throw new IllegalArgumentException("workspace calendar scope has no team or channel id");
            }
            if (type == ScopeType.TEAM && (teamId == null || channelId != null)) {
                throw new IllegalArgumentException("team calendar scope requires only a team id");
            }
            if (type == ScopeType.CHANNEL && (teamId == null || channelId == null)) {
                throw new IllegalArgumentException("channel calendar scope requires team and channel ids");
            }
        }

        public static CalendarScope workspace() {
            return new CalendarScope(ScopeType.WORKSPACE, null, null);
        }
    }

    public record Attendee(String memberRef, String displayName, String address, String response) {
        public Attendee {
            if (optionalText(memberRef) == null && optionalText(address) == null) {
                throw new IllegalArgumentException("attendee requires a member reference or address");
            }
            memberRef = optionalText(memberRef);
            displayName = optionalText(displayName);
            address = optionalText(address);
            response = optionalText(response);
        }
    }

    public record RecurrenceSet(
            RecurrenceFrequency frequency,
            int interval,
            Integer count,
            ZonedDateTime until,
            List<ZonedDateTime> additionalDates,
            List<ZonedDateTime> excludedDates) {
        public RecurrenceSet {
            if (frequency == null) {
                throw new IllegalArgumentException("recurrence frequency is required");
            }
            if (interval < 1) {
                throw new IllegalArgumentException("recurrence interval must be positive");
            }
            if ((count == null) == (until == null)) {
                throw new IllegalArgumentException("recurrence requires exactly one COUNT or UNTIL bound");
            }
            if (count != null && count < 1) {
                throw new IllegalArgumentException("recurrence count must be positive");
            }
            additionalDates = additionalDates == null ? List.of() : List.copyOf(additionalDates);
            excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
        }
    }

    public record CalendarEvent(
            CalendarId calendarId,
            EventId id,
            CalendarScope scope,
            String title,
            String description,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            ZoneId timezone,
            boolean allDay,
            String location,
            List<Attendee> attendees,
            RecurrenceSet recurrence,
            EventVersion version,
            Instant updatedAt) {
        public CalendarEvent {
            if (calendarId == null || id == null || localStart == null || localEnd == null || timezone == null) {
                throw new IllegalArgumentException("calendar, event, start, end, and timezone are required");
            }
            scope = scope == null ? CalendarScope.workspace() : scope;
            title = requireText(title, "event title");
            description = optionalText(description);
            location = optionalText(location);
            attendees = attendees == null ? List.of() : List.copyOf(attendees);
            version = version == null ? EventVersion.unknown() : version;
            if (!localEnd.isAfter(localStart)) {
                throw new IllegalArgumentException("event end must be after event start");
            }
        }

        public ZonedDateTime startsAt() {
            return localStart.atZone(timezone);
        }

        public ZonedDateTime endsAt() {
            return localEnd.atZone(timezone);
        }
    }

    public record FreeBusyWindow(Instant start, Instant end) {
        public FreeBusyWindow {
            if (start == null || end == null || !end.isAfter(start)) {
                throw new IllegalArgumentException("free-busy end must be after start");
            }
        }
    }

    public record CalendarChange(String syncToken, EventId eventId, boolean deleted, EventVersion version) {
        public CalendarChange {
            syncToken = requireText(syncToken, "sync token");
            if (eventId == null) {
                throw new IllegalArgumentException("changed event id is required");
            }
            version = version == null ? EventVersion.unknown() : version;
        }
    }

    private static String requireText(String value, String field) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
