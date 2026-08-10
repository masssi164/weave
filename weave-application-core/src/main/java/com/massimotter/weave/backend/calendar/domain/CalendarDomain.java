package com.massimotter.weave.backend.calendar.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/** Provider-neutral canonical Calendar domain. */
public final class CalendarDomain {

    private CalendarDomain() {
    }

    public enum ScopeType {
        WORKSPACE,
        TEAM,
        CHANNEL
    }

    public enum TemporalKind {
        DATE,
        FLOATING,
        UTC,
        ZONED
    }

    public enum RecurrenceFrequency {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY
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

    /**
     * Exact RFC 5545 temporal semantics. Exactly one representation is populated.
     * DATE has no clock/zone; FLOATING has a local clock and no zone; UTC is an
     * instant; ZONED has a local clock plus an IANA TZID.
     */
    public record TemporalValue(
            TemporalKind kind,
            LocalDate date,
            LocalDateTime localDateTime,
            Instant instant,
            ZoneId zoneId) {

        public TemporalValue {
            if (kind == null) {
                throw new IllegalArgumentException("calendar temporal kind is required");
            }
            int values = (date == null ? 0 : 1)
                    + (localDateTime == null ? 0 : 1)
                    + (instant == null ? 0 : 1);
            if (values != 1) {
                throw new IllegalArgumentException("calendar temporal value requires exactly one value representation");
            }
            switch (kind) {
                case DATE -> {
                    if (date == null || zoneId != null) {
                        throw new IllegalArgumentException("DATE must contain only a LocalDate");
                    }
                }
                case FLOATING -> {
                    if (localDateTime == null || zoneId != null) {
                        throw new IllegalArgumentException("FLOATING must contain only a LocalDateTime");
                    }
                }
                case UTC -> {
                    if (instant == null || zoneId != null) {
                        throw new IllegalArgumentException("UTC must contain only an Instant");
                    }
                }
                case ZONED -> {
                    if (localDateTime == null || zoneId == null || "Z".equals(zoneId.getId())) {
                        throw new IllegalArgumentException("ZONED requires LocalDateTime and IANA TZID");
                    }
                }
            }
        }

        public static TemporalValue date(LocalDate value) {
            return new TemporalValue(TemporalKind.DATE, value, null, null, null);
        }

        public static TemporalValue floating(LocalDateTime value) {
            return new TemporalValue(TemporalKind.FLOATING, null, value, null, null);
        }

        public static TemporalValue utc(Instant value) {
            return new TemporalValue(TemporalKind.UTC, null, null, value, null);
        }

        public static TemporalValue zoned(LocalDateTime value, ZoneId zone) {
            return new TemporalValue(TemporalKind.ZONED, null, value, null, zone);
        }

        public boolean dateOnly() {
            return kind == TemporalKind.DATE;
        }

        public LocalDateTime localProjection() {
            return switch (kind) {
                case DATE -> date.atStartOfDay();
                case FLOATING, ZONED -> localDateTime;
                case UTC -> LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            };
        }

        public ZoneId compatibilityZone() {
            return switch (kind) {
                case ZONED -> zoneId;
                case UTC, DATE -> ZoneOffset.UTC;
                case FLOATING -> null;
            };
        }

        public Instant toInstant(ZoneId evaluationZone) {
            return switch (kind) {
                case DATE -> {
                    if (evaluationZone == null) {
                        throw new IllegalArgumentException("DATE values require an explicit evaluation zone for Instant projection");
                    }
                    yield date.atStartOfDay(evaluationZone).toInstant();
                }
                case UTC -> instant;
                case ZONED -> localDateTime.atZone(zoneId).toInstant();
                case FLOATING -> {
                    if (evaluationZone == null) {
                        throw new IllegalArgumentException("FLOATING values require an explicit evaluation zone");
                    }
                    yield localDateTime.atZone(evaluationZone).toInstant();
                }
            };
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

    /** Product-profile recurrence. RRULE/RDATE/EXDATE parsing belongs to the standards adapter. */
    public record RecurrenceSet(
            RecurrenceFrequency frequency,
            int interval,
            Integer count,
            ZonedDateTime until,
            List<TemporalValue> additionalDates,
            List<TemporalValue> excludedDates,
            List<String> byDay,
            List<Integer> byMonthDay,
            List<Integer> byMonth,
            List<Integer> bySetPos,
            String weekStart) {

        public RecurrenceSet(
                RecurrenceFrequency frequency,
                int interval,
                Integer count,
                ZonedDateTime until,
                List<TemporalValue> additionalDates,
                List<TemporalValue> excludedDates) {
            this(frequency, interval, count, until, additionalDates, excludedDates,
                    List.of(), List.of(), List.of(), List.of(), null);
        }

        public RecurrenceSet {
            if (frequency == null) {
                throw new IllegalArgumentException("recurrence frequency is required");
            }
            if (interval < 1) {
                throw new IllegalArgumentException("recurrence interval must be positive");
            }
            if (count != null && until != null) {
                throw new IllegalArgumentException("recurrence cannot carry COUNT and UNTIL together");
            }
            if (count != null && (count < 1 || count > 100_000)) {
                throw new IllegalArgumentException("recurrence count is outside the supported range");
            }
            additionalDates = additionalDates == null ? List.of() : List.copyOf(additionalDates);
            excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
            byDay = byDay == null ? List.of() : byDay.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
            byMonthDay = byMonthDay == null ? List.of() : List.copyOf(byMonthDay);
            byMonth = byMonth == null ? List.of() : List.copyOf(byMonth);
            bySetPos = bySetPos == null ? List.of() : List.copyOf(bySetPos);
            weekStart = optionalText(weekStart);
            if (byMonth.stream().anyMatch(value -> value < 1 || value > 12)) {
                throw new IllegalArgumentException("BYMONTH value is invalid");
            }
            if (byMonthDay.stream().anyMatch(value -> value == 0 || value < -31 || value > 31)) {
                throw new IllegalArgumentException("BYMONTHDAY value is invalid");
            }
            if (bySetPos.stream().anyMatch(value -> value == 0 || value < -366 || value > 366)) {
                throw new IllegalArgumentException("BYSETPOS value is invalid");
            }
        }

        public String rrule() {
            StringBuilder value = new StringBuilder("FREQ=").append(frequency.name());
            if (interval != 1) value.append(";INTERVAL=").append(interval);
            if (count != null) value.append(";COUNT=").append(count);
            if (until != null) value.append(";UNTIL=").append(until.withZoneSameInstant(ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")));
            if (!byDay.isEmpty()) value.append(";BYDAY=").append(String.join(",", byDay));
            if (!byMonthDay.isEmpty()) value.append(";BYMONTHDAY=").append(joinIntegers(byMonthDay));
            if (!byMonth.isEmpty()) value.append(";BYMONTH=").append(joinIntegers(byMonth));
            if (!bySetPos.isEmpty()) value.append(";BYSETPOS=").append(joinIntegers(bySetPos));
            if (weekStart != null) value.append(";WKST=").append(weekStart);
            return value.toString();
        }
    }

    public record RecurrenceOverride(
            TemporalValue recurrenceId,
            TemporalValue start,
            TemporalValue end,
            boolean cancelled,
            String title,
            String description,
            String location) {
        public RecurrenceOverride {
            if (recurrenceId == null) {
                throw new IllegalArgumentException("recurrence override id is required");
            }
            if (!cancelled && (start == null || end == null)) {
                throw new IllegalArgumentException("moved recurrence override requires start and end");
            }
            title = optionalText(title);
            description = optionalText(description);
            location = optionalText(location);
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
            Instant updatedAt,
            TemporalValue startValue,
            TemporalValue endValue,
            List<RecurrenceOverride> overrides) {

        /** Backward-compatible constructor for legacy callers; no recurrence semantics are implemented here. */
        public CalendarEvent(
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
            this(calendarId, id, scope, title, description, localStart, localEnd, timezone, allDay,
                    location, attendees, recurrence, version, updatedAt,
                    allDay ? TemporalValue.date(localStart.toLocalDate()) : TemporalValue.zoned(localStart, timezone),
                    allDay ? TemporalValue.date(localEnd.toLocalDate()) : TemporalValue.zoned(localEnd, timezone),
                    List.of());
        }

        public CalendarEvent(
                CalendarId calendarId,
                EventId id,
                CalendarScope scope,
                String title,
                String description,
                TemporalValue start,
                TemporalValue end,
                String location,
                List<Attendee> attendees,
                RecurrenceSet recurrence,
                List<RecurrenceOverride> overrides,
                EventVersion version,
                Instant updatedAt) {
            this(calendarId, id, scope, title, description,
                    start == null ? null : start.localProjection(),
                    end == null ? null : end.localProjection(),
                    compatibilityZone(start),
                    start != null && start.kind() == TemporalKind.DATE,
                    location, attendees, recurrence, version, updatedAt, start, end, overrides);
        }

        public CalendarEvent {
            if (calendarId == null || id == null || startValue == null || endValue == null) {
                throw new IllegalArgumentException("calendar, event, start, and end are required");
            }
            if (startValue.kind() != endValue.kind()
                    || startValue.kind() == TemporalKind.ZONED && !startValue.zoneId().equals(endValue.zoneId())) {
                throw new IllegalArgumentException("calendar start and end temporal semantics must match");
            }
            scope = scope == null ? CalendarScope.workspace() : scope;
            title = requireText(title, "event title");
            description = optionalText(description);
            location = optionalText(location);
            attendees = attendees == null ? List.of() : List.copyOf(attendees);
            overrides = overrides == null ? List.of() : List.copyOf(overrides);
            version = version == null ? EventVersion.unknown() : version;
            localStart = startValue.localProjection();
            localEnd = endValue.localProjection();
            timezone = compatibilityZone(startValue);
            allDay = startValue.kind() == TemporalKind.DATE;
            requireEndAfterStart(startValue, endValue);
            for (RecurrenceOverride override : overrides) {
                if (override.recurrenceId().kind() != startValue.kind()) {
                    throw new IllegalArgumentException("RECURRENCE-ID temporal semantics must match DTSTART");
                }
            }
        }

        public ZonedDateTime startsAt() {
            return switch (startValue.kind()) {
                case DATE -> startValue.date().atStartOfDay(ZoneOffset.UTC);
                case UTC -> startValue.instant().atZone(ZoneOffset.UTC);
                case ZONED -> startValue.localDateTime().atZone(startValue.zoneId());
                case FLOATING -> throw new IllegalArgumentException("FLOATING values have no implicit zone");
            };
        }

        public ZonedDateTime endsAt() {
            return switch (endValue.kind()) {
                case DATE -> endValue.date().atStartOfDay(ZoneOffset.UTC);
                case UTC -> endValue.instant().atZone(ZoneOffset.UTC);
                case ZONED -> endValue.localDateTime().atZone(endValue.zoneId());
                case FLOATING -> throw new IllegalArgumentException("FLOATING values have no implicit zone");
            };
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

    public record CalendarChangeSet(String syncToken, List<CalendarChange> changes) {
        public CalendarChangeSet {
            syncToken = requireText(syncToken, "calendar sync token");
            changes = changes == null ? List.of() : List.copyOf(changes);
            String expectedSyncToken = syncToken;
            if (changes.stream().anyMatch(change -> !expectedSyncToken.equals(change.syncToken()))) {
                throw new IllegalArgumentException("calendar changes must carry the change-set sync token");
            }
        }
    }

    private static void requireEndAfterStart(TemporalValue start, TemporalValue end) {
        switch (start.kind()) {
            case DATE -> {
                if (!end.date().isAfter(start.date())) {
                    throw new IllegalArgumentException("DATE DTEND must be exclusive and after DTSTART");
                }
            }
            case FLOATING, ZONED -> {
                if (!end.localDateTime().isAfter(start.localDateTime())) {
                    throw new IllegalArgumentException("event end must be after event start");
                }
            }
            case UTC -> {
                if (!end.instant().isAfter(start.instant())) {
                    throw new IllegalArgumentException("event end must be after event start");
                }
            }
        }
    }

    private static ZoneId compatibilityZone(TemporalValue value) {
        return value == null ? null : value.compatibilityZone();
    }

    private static String joinIntegers(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
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
