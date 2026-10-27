package com.massimotter.weave.backend.calendar.persistence;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalKind;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Domain-owned normalized Calendar payload mappings; serving never mutates schema. */
@Entity
@Table(name = "weave_calendar_event_temporals", check = @CheckConstraint(constraint = "(temporal_kind='DATE' and start_date is not null and end_date is not null and end_date > start_date and start_local is null and end_local is null and start_instant is null and end_instant is null and timezone_id is null) or (temporal_kind='FLOATING' and start_local is not null and end_local is not null and end_local > start_local and start_date is null and end_date is null and start_instant is null and end_instant is null and timezone_id is null) or (temporal_kind='UTC' and start_instant is not null and end_instant is not null and end_instant > start_instant and start_date is null and end_date is null and start_local is null and end_local is null and timezone_id is null) or (temporal_kind='ZONED' and start_local is not null and end_local is not null and end_local > start_local and timezone_id is not null and start_date is null and end_date is null and start_instant is null and end_instant is null)"))
class CalendarTemporalJpaEntity {
    @EmbeddedId private CalendarEventId id;
    @Column(name = "temporal_kind", nullable = false, length = 16) private String kind;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "start_local") private LocalDateTime startLocal;
    @Column(name = "end_local") private LocalDateTime endLocal;
    @Column(name = "start_instant") private OffsetDateTime startInstant;
    @Column(name = "end_instant") private OffsetDateTime endInstant;
    @Column(name = "timezone_id", length = 255) private String zone;
    protected CalendarTemporalJpaEntity() {}

    CalendarTemporalJpaEntity(CalendarEventId id, TemporalValue start, TemporalValue end) {
        this.id = id;
        kind = start.kind().name();
        startDate = start.date(); endDate = end.date();
        startLocal = start.localDateTime(); endLocal = end.localDateTime();
        startInstant = CalendarNormalizedValues.offset(start.instant());
        endInstant = CalendarNormalizedValues.offset(end.instant());
        zone = CalendarNormalizedValues.zone(start);
    }

    CalendarEventId id() { return id; }

    TemporalValue start() { return CalendarNormalizedValues.temporal(kind, startDate, startLocal, startInstant, zone); }
    TemporalValue end() { return CalendarNormalizedValues.temporal(kind, endDate, endLocal, endInstant, zone); }
}

@Embeddable
record CalendarAttendeeId(@Embedded CalendarEventId event, @Column(name = "ordinal", nullable = false) int ordinal) implements Serializable {}

@Entity
@Table(name = "weave_calendar_attendees", check = @CheckConstraint(constraint = "ordinal >= 0 and (member_ref is not null or address is not null)"))
class CalendarAttendeeJpaEntity {
    @EmbeddedId private CalendarAttendeeId id;
    @Column(name = "member_ref", length = 512) private String memberRef;
    @Column(name = "display_name", length = 1024) private String displayName;
    @Column(name = "address", length = 2048) private String address;
    @Column(name = "attendee_role", length = 128) private String role;
    @Column(name = "response_state", length = 128) private String response;
    protected CalendarAttendeeJpaEntity() {}

    CalendarAttendeeJpaEntity(CalendarAttendeeId id, Attendee attendee) {
        this.id = id;
        memberRef = attendee.memberRef(); displayName = attendee.displayName(); address = attendee.address();
        role = attendee.role(); response = attendee.response();
    }

    CalendarEventId eventId() { return id.event(); }

    Attendee toDomain() { return new Attendee(memberRef, displayName, address, role, response); }
}

@Entity
@Table(name = "weave_calendar_recurrence_rules", check = @CheckConstraint(constraint = "frequency in ('DAILY','WEEKLY','MONTHLY','YEARLY') and interval_value > 0 and (count_value is null or count_value > 0) and not (count_value is not null and (until_local is not null or until_instant is not null))"))
class CalendarRecurrenceRuleJpaEntity {
    @EmbeddedId private CalendarEventId id;
    @Column(name = "frequency", nullable = false, length = 16) private String frequency;
    @Column(name = "interval_value", nullable = false) private int interval;
    @Column(name = "count_value") private Integer count;
    @Column(name = "until_local") private LocalDateTime untilLocal;
    @Column(name = "until_instant") private OffsetDateTime untilInstant;
    @Column(name = "until_timezone_id", length = 255) private String untilZone;
    @Column(name = "by_day", columnDefinition = "text") private String byDay;
    @Column(name = "by_month_day", columnDefinition = "text") private String byMonthDay;
    @Column(name = "by_month", columnDefinition = "text") private String byMonth;
    @Column(name = "by_set_pos", columnDefinition = "text") private String bySetPos;
    @Column(name = "week_start", length = 2) private String weekStart;
    protected CalendarRecurrenceRuleJpaEntity() {}

    CalendarRecurrenceRuleJpaEntity(CalendarEventId id, RecurrenceSet recurrence) {
        this.id = id;
        frequency = recurrence.frequency().name(); interval = recurrence.interval(); count = recurrence.count();
        untilLocal = recurrence.until() == null ? null : recurrence.until().toLocalDateTime();
        untilInstant = recurrence.until() == null ? null : CalendarNormalizedValues.offset(recurrence.until().toInstant());
        untilZone = recurrence.until() == null ? null : recurrence.until().getZone().getId();
        byDay = CalendarNormalizedValues.csv(recurrence.byDay());
        byMonthDay = CalendarNormalizedValues.csv(recurrence.byMonthDay());
        byMonth = CalendarNormalizedValues.csv(recurrence.byMonth());
        bySetPos = CalendarNormalizedValues.csv(recurrence.bySetPos());
        weekStart = recurrence.weekStart();
    }

    CalendarEventId id() { return id; }

    RecurrenceSet toDomain(List<TemporalValue> additional, List<TemporalValue> excluded) {
        ZoneId zone = untilZone == null ? ZoneOffset.UTC : ZoneId.of(untilZone);
        ZonedDateTime until = untilInstant != null ? untilInstant.atZoneSameInstant(zone)
                : untilLocal == null ? null : untilLocal.atZone(zone);
        return new RecurrenceSet(RecurrenceFrequency.valueOf(frequency), interval, count, until, additional, excluded,
                CalendarNormalizedValues.strings(byDay), CalendarNormalizedValues.integers(byMonthDay),
                CalendarNormalizedValues.integers(byMonth), CalendarNormalizedValues.integers(bySetPos), weekStart);
    }
}

@Embeddable
record CalendarRecurrenceDateId(@Embedded CalendarEventId event,
        @Column(name = "recurrence_type", nullable = false, length = 8) String type,
        @Column(name = "ordinal", nullable = false) int ordinal) implements Serializable {}

@Entity
@Table(name = "weave_calendar_recurrence_dates", check = @CheckConstraint(constraint = "ordinal >= 0 and recurrence_type in ('RDATE','EXDATE') and ((temporal_kind='DATE' and date_value is not null and local_value is null and instant_value is null and timezone_id is null) or (temporal_kind='FLOATING' and date_value is null and local_value is not null and instant_value is null and timezone_id is null) or (temporal_kind='UTC' and date_value is null and local_value is null and instant_value is not null and timezone_id is null) or (temporal_kind='ZONED' and date_value is null and local_value is not null and instant_value is null and timezone_id is not null))"))
class CalendarRecurrenceDateJpaEntity {
    @EmbeddedId private CalendarRecurrenceDateId id;
    @Column(name = "temporal_kind", nullable = false, length = 16) private String kind;
    @Column(name = "date_value") private LocalDate date;
    @Column(name = "local_value") private LocalDateTime local;
    @Column(name = "instant_value") private OffsetDateTime instant;
    @Column(name = "timezone_id", length = 255) private String zone;
    protected CalendarRecurrenceDateJpaEntity() {}

    CalendarRecurrenceDateJpaEntity(CalendarRecurrenceDateId id, TemporalValue value) {
        this.id = id; kind = value.kind().name(); date = value.date(); local = value.localDateTime();
        instant = CalendarNormalizedValues.offset(value.instant()); zone = CalendarNormalizedValues.zone(value);
    }

    CalendarEventId eventId() { return id.event(); }
    String type() { return id.type(); }

    TemporalValue toDomain() { return CalendarNormalizedValues.temporal(kind, date, local, instant, zone); }
}

@Embeddable
record CalendarOverrideId(@Embedded CalendarEventId event,
        @Column(name = "recurrence_id_key", nullable = false, length = 768) String recurrenceKey) implements Serializable {}

@Entity
@Table(name = "weave_calendar_event_overrides", check = @CheckConstraint(constraint = "temporal_kind in ('DATE','FLOATING','UTC','ZONED')"))
class CalendarOverrideJpaEntity {
    @EmbeddedId private CalendarOverrideId id;
    @Column(name = "temporal_kind", nullable = false, length = 16) private String kind;
    @Column(name = "recurrence_date") private LocalDate recurrenceDate;
    @Column(name = "recurrence_local") private LocalDateTime recurrenceLocal;
    @Column(name = "recurrence_instant") private OffsetDateTime recurrenceInstant;
    @Column(name = "recurrence_timezone_id", length = 255) private String recurrenceZone;
    @Column(name = "cancelled", nullable = false) private boolean cancelled;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "start_local") private LocalDateTime startLocal;
    @Column(name = "end_local") private LocalDateTime endLocal;
    @Column(name = "start_instant") private OffsetDateTime startInstant;
    @Column(name = "end_instant") private OffsetDateTime endInstant;
    @Column(name = "timezone_id", length = 255) private String zone;
    @Column(name = "title", length = 1024) private String title;
    @Column(name = "description", length = 8192) private String description;
    @Column(name = "location", length = 2048) private String location;
    protected CalendarOverrideJpaEntity() {}

    CalendarOverrideJpaEntity(CalendarOverrideId id, RecurrenceOverride override) {
        this.id = id;
        TemporalValue recurrence = override.recurrenceId();
        kind = recurrence.kind().name(); recurrenceDate = recurrence.date(); recurrenceLocal = recurrence.localDateTime();
        recurrenceInstant = CalendarNormalizedValues.offset(recurrence.instant());
        recurrenceZone = CalendarNormalizedValues.zone(recurrence); cancelled = override.cancelled();
        TemporalValue start = override.start(); TemporalValue end = override.end();
        startDate = start == null ? null : start.date(); endDate = end == null ? null : end.date();
        startLocal = start == null ? null : start.localDateTime(); endLocal = end == null ? null : end.localDateTime();
        startInstant = start == null ? null : CalendarNormalizedValues.offset(start.instant());
        endInstant = end == null ? null : CalendarNormalizedValues.offset(end.instant());
        zone = CalendarNormalizedValues.zone(start);
        title = override.title(); description = override.description(); location = override.location();
    }

    CalendarEventId eventId() { return id.event(); }

    RecurrenceOverride toDomain() {
        return new RecurrenceOverride(
                CalendarNormalizedValues.temporal(kind, recurrenceDate, recurrenceLocal, recurrenceInstant, recurrenceZone),
                cancelled ? null : CalendarNormalizedValues.temporal(kind, startDate, startLocal, startInstant, zone),
                cancelled ? null : CalendarNormalizedValues.temporal(kind, endDate, endLocal, endInstant, zone),
                cancelled, title, description, location);
    }
}

final class CalendarNormalizedValues {
    private CalendarNormalizedValues() {}

    static TemporalValue temporal(String kind, LocalDate date, LocalDateTime local, OffsetDateTime instant, String zone) {
        return switch (TemporalKind.valueOf(kind)) {
            case DATE -> TemporalValue.date(date);
            case FLOATING -> TemporalValue.floating(local);
            case UTC -> TemporalValue.utc(instant.toInstant());
            case ZONED -> TemporalValue.zoned(local, ZoneId.of(zone));
        };
    }

    static OffsetDateTime offset(Instant instant) { return instant == null ? null : instant.atOffset(ZoneOffset.UTC); }
    static String zone(TemporalValue value) { return value == null || value.zoneId() == null ? null : value.zoneId().getId(); }
    static String csv(List<?> values) {
        return values == null || values.isEmpty() ? null
                : values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    static List<String> strings(String value) { return value == null || value.isBlank() ? List.of() : List.of(value.split(",")); }
    static List<Integer> integers(String value) { return strings(value).stream().map(Integer::valueOf).toList(); }
}
