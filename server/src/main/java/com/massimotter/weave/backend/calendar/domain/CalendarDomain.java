package com.massimotter.weave.backend.calendar.domain;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public enum WriteIntent {
        CREATE,
        UPDATE
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

    public record Attendee(String memberRef, String displayName, String address, String role, String response) {
        public Attendee {
            if (optionalText(memberRef) == null && optionalText(address) == null) {
                throw new IllegalArgumentException("attendee requires a member reference or address");
            }
            memberRef = optionalText(memberRef);
            displayName = optionalText(displayName);
            address = optionalText(address);
            role = optionalText(role);
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
            if (count != null && count > 10_000) {
                throw new IllegalArgumentException("recurrence count exceeds the expansion safety limit");
            }
            additionalDates = additionalDates == null ? List.of() : List.copyOf(additionalDates);
            excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
        }

        private int maximumOccurrences() {
            return count == null ? 10_000 : count;
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

        public List<CalendarOccurrence> occurrences(Instant from, Instant to) {
            if (from == null || to == null || !to.isAfter(from)) {
                throw new IllegalArgumentException("occurrence query end must be after start");
            }
            Duration localDuration = Duration.between(localStart, localEnd);
            Map<LocalDateTime, CalendarOccurrence> occurrences = new LinkedHashMap<>();
            if (recurrence == null) {
                addOccurrence(occurrences, localStart, localDuration);
            } else {
                int generated = 0;
                LocalDateTime candidate = localStart;
                while (generated < recurrence.maximumOccurrences()) {
                    ZonedDateTime zonedCandidate = candidate.atZone(timezone);
                    if (recurrence.until() != null && zonedCandidate.isAfter(recurrence.until())) {
                        break;
                    }
                    addOccurrence(occurrences, candidate, localDuration);
                    generated++;
                    candidate = switch (recurrence.frequency()) {
                        case DAILY -> candidate.plusDays(recurrence.interval());
                        case WEEKLY -> candidate.plusWeeks(recurrence.interval());
                    };
                }
                if (recurrence.until() != null
                        && !candidate.atZone(timezone).isAfter(recurrence.until())) {
                    throw new IllegalArgumentException("recurrence expansion exceeds the safety limit");
                }
                recurrence.additionalDates().stream()
                        .map(value -> value.withZoneSameInstant(timezone).toLocalDateTime())
                        .forEach(value -> addOccurrence(occurrences, value, localDuration));
                recurrence.excludedDates().stream()
                        .map(value -> value.withZoneSameInstant(timezone).toLocalDateTime())
                        .forEach(occurrences::remove);
            }
            return occurrences.values().stream()
                    .filter(occurrence -> occurrence.start().toInstant().isBefore(to)
                            && occurrence.end().toInstant().isAfter(from))
                    .sorted(Comparator.comparing(CalendarOccurrence::start))
                    .toList();
        }

        private void addOccurrence(
                Map<LocalDateTime, CalendarOccurrence> occurrences,
                LocalDateTime occurrenceStart,
                Duration localDuration) {
            ZonedDateTime start = occurrenceStart.atZone(timezone);
            ZonedDateTime end = occurrenceStart.plus(localDuration).atZone(timezone);
            occurrences.put(occurrenceStart, new CalendarOccurrence(id, start, end));
        }
    }

    public record CalendarWrite(CalendarEvent event, WriteIntent intent, EventVersion expectedVersion) {
        public CalendarWrite {
            if (event == null || intent == null) {
                throw new IllegalArgumentException("calendar event and write intent are required");
            }
            expectedVersion = expectedVersion == null ? EventVersion.unknown() : expectedVersion;
            if (intent == WriteIntent.CREATE && expectedVersion.value() != null) {
                throw new IllegalArgumentException("create writes cannot carry an expected version");
            }
        }
    }

    public record CalendarOccurrence(EventId eventId, ZonedDateTime start, ZonedDateTime end) {
        public CalendarOccurrence {
            if (eventId == null || start == null || end == null || !end.isAfter(start)) {
                throw new IllegalArgumentException("calendar occurrence requires an event and valid time range");
            }
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
