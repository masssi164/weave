package com.massimotter.weave.backend.calendar.adapter;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalKind;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.service.calendar.RecurrenceEngine;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-normalized persistence and window-bounded iCal4j recurrence projection. */
@Component
public class NativeCalendarRelationalStore {

    private static final int MAX_OCCURRENCES = 10_000;

    private final JdbcTemplate jdbc;
    private final RecurrenceEngine recurrenceEngine;

    @Autowired
    NativeCalendarRelationalStore(JdbcTemplate jdbc) {
        this(jdbc, new com.massimotter.weave.backend.service.calendar.Ical4jRecurrenceEngine());
    }

    NativeCalendarRelationalStore(JdbcTemplate jdbc, RecurrenceEngine recurrenceEngine) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.recurrenceEngine = Objects.requireNonNull(recurrenceEngine, "recurrenceEngine");
    }

    @Transactional
    public void save(CalendarEvent event, String scopeKey) {
        upsertTemporal(event, scopeKey);
        replaceAttendees(event, scopeKey);
        replaceRecurrence(event, scopeKey);
        replaceOverrides(event, scopeKey);
    }

    @Transactional
    public void delete(String calendarId, String scopeKey, String eventId) {
        jdbc.update("delete from weave_calendar_event_overrides where calendar_id=? and scope_key=? and event_id=?", calendarId, scopeKey, eventId);
        jdbc.update("delete from weave_calendar_recurrence_dates where calendar_id=? and scope_key=? and event_id=?", calendarId, scopeKey, eventId);
        jdbc.update("delete from weave_calendar_recurrence_rules where calendar_id=? and scope_key=? and event_id=?", calendarId, scopeKey, eventId);
        jdbc.update("delete from weave_calendar_attendees where calendar_id=? and scope_key=? and event_id=?", calendarId, scopeKey, eventId);
        jdbc.update("delete from weave_calendar_event_temporals where calendar_id=? and scope_key=? and event_id=?", calendarId, scopeKey, eventId);
    }

    @Transactional(readOnly = true)
    public CalendarEvent enrich(CalendarEvent fallback, String scopeKey) {
        String calendar = fallback.calendarId().value();
        String eventId = fallback.id().value();
        List<TemporalPair> temporal = jdbc.query(
                """
                select temporal_kind,start_date,end_date,start_local,end_local,start_instant,end_instant,timezone_id
                from weave_calendar_event_temporals where calendar_id=? and scope_key=? and event_id=?
                """,
                (rs, ignored) -> new TemporalPair(
                        temporal(rs.getString("temporal_kind"), rs.getObject("start_date", LocalDate.class),
                                rs.getObject("start_local", LocalDateTime.class), rs.getObject("start_instant", Instant.class), rs.getString("timezone_id")),
                        temporal(rs.getString("temporal_kind"), rs.getObject("end_date", LocalDate.class),
                                rs.getObject("end_local", LocalDateTime.class), rs.getObject("end_instant", Instant.class), rs.getString("timezone_id"))),
                calendar, scopeKey, eventId);
        if (temporal.isEmpty()) return fallback;
        List<Attendee> attendees = jdbc.query(
                """
                select member_ref,display_name,address,attendee_role,response_state
                from weave_calendar_attendees
                where calendar_id=? and scope_key=? and event_id=? order by ordinal
                """,
                (rs, ignored) -> new Attendee(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                calendar, scopeKey, eventId);
        RecurrenceSet recurrence = loadRecurrence(calendar, scopeKey, eventId, fallback.recurrence());
        List<RecurrenceOverride> overrides = loadOverrides(calendar, scopeKey, eventId);
        TemporalPair pair = temporal.getFirst();
        return new CalendarEvent(
                fallback.calendarId(), fallback.id(), fallback.scope(), fallback.title(), fallback.description(),
                pair.start(), pair.end(), fallback.location(), attendees, recurrence, overrides,
                fallback.version(), fallback.updatedAt());
    }

    @Transactional(readOnly = true)
    public List<String> candidateEventIds(
            String calendarId,
            String scopeKey,
            Instant from,
            Instant to,
            ZoneId evaluationZone) {
        LocalDate fromDate = from.atZone(evaluationZone).toLocalDate();
        LocalDate toDate = to.atZone(evaluationZone).toLocalDate();
        LocalDateTime fromLocal = from.atZone(evaluationZone).toLocalDateTime();
        LocalDateTime toLocal = to.atZone(evaluationZone).toLocalDateTime();
        return jdbc.query(
                """
                select distinct e.event_id
                from weave_calendar_events e
                join weave_calendar_event_temporals t
                  on t.calendar_id=e.calendar_id and t.scope_key=e.scope_key and t.event_id=e.event_id
                left join weave_calendar_recurrence_rules r
                  on r.calendar_id=e.calendar_id and r.scope_key=e.scope_key and r.event_id=e.event_id
                left join weave_calendar_event_overrides o
                  on o.calendar_id=e.calendar_id and o.scope_key=e.scope_key and o.event_id=e.event_id and o.cancelled=false
                where e.calendar_id=? and e.scope_key=? and e.deleted=false
                  and (
                    r.event_id is not null
                    or (t.temporal_kind='UTC' and t.start_instant < ? and t.end_instant > ?)
                    or (t.temporal_kind='DATE' and t.start_date < ? and t.end_date > ?)
                    or (t.temporal_kind in ('ZONED','FLOATING') and t.start_local < ? and t.end_local > ?)
                    or (o.start_instant is not null and o.start_instant < ? and o.end_instant > ?)
                    or (o.start_date is not null and o.start_date < ? and o.end_date > ?)
                    or (o.start_local is not null and o.start_local < ? and o.end_local > ?)
                  )
                order by e.event_id
                """,
                (rs, ignored) -> rs.getString(1),
                calendarId, scopeKey,
                to, from,
                toDate, fromDate,
                toLocal, fromLocal,
                to, from,
                toDate, fromDate,
                toLocal, fromLocal);
    }

    /** Compatibility overload for existing callers; UTC is explicit rather than implicit in storage semantics. */
    @Transactional(readOnly = true)
    public List<String> candidateEventIds(String calendarId, String scopeKey, Instant from, Instant to) {
        return candidateEventIds(calendarId, scopeKey, from, to, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public List<Occurrence> occurrences(
            CalendarEvent event,
            Instant from,
            Instant to,
            ZoneId evaluationZone) {
        if (evaluationZone == null) throw new IllegalArgumentException("calendar evaluation zone is required");
        Map<String, Occurrence> result = new LinkedHashMap<>();
        addMasterAndRecurrence(result, event, from, to, evaluationZone);
        applyOverrides(result, event, evaluationZone);
        return result.values().stream()
                .filter(value -> value.start().isBefore(to) && value.end().isAfter(from))
                .sorted(Comparator.comparing(Occurrence::start))
                .limit(MAX_OCCURRENCES)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Occurrence> occurrences(CalendarEvent event, Instant from, Instant to) {
        ZoneId zone = event.startValue().kind() == TemporalKind.ZONED
                ? event.startValue().zoneId()
                : ZoneOffset.UTC;
        return occurrences(event, from, to, zone);
    }

    private void addMasterAndRecurrence(
            Map<String, Occurrence> result,
            CalendarEvent event,
            Instant from,
            Instant to,
            ZoneId evaluationZone) {
        Instant masterStart = event.startValue().toInstant(evaluationZone);
        Instant masterEnd = event.endValue().toInstant(evaluationZone);
        Duration exactDuration = Duration.between(masterStart, masterEnd);
        add(result, masterStart, masterEnd);
        RecurrenceSet recurrence = event.recurrence();
        if (recurrence == null) return;
        String rrule = recurrence.rrule();
        switch (event.startValue().kind()) {
            case DATE -> recurrenceEngine.dates(
                            rrule,
                            event.startValue().date(),
                            from.atZone(evaluationZone).toLocalDate(),
                            to.atZone(evaluationZone).toLocalDate().plusDays(1),
                            MAX_OCCURRENCES)
                    .forEach(date -> {
                        Instant start = date.atStartOfDay(evaluationZone).toInstant();
                        long days = java.time.temporal.ChronoUnit.DAYS.between(event.startValue().date(), event.endValue().date());
                        Instant end = date.plusDays(days).atStartOfDay(evaluationZone).toInstant();
                        add(result, start, end);
                    });
            case FLOATING -> recurrenceEngine.floating(
                            rrule,
                            event.startValue().localDateTime(),
                            from.atZone(evaluationZone).toLocalDateTime(),
                            to.atZone(evaluationZone).toLocalDateTime(),
                            MAX_OCCURRENCES)
                    .forEach(local -> add(result,
                            local.atZone(evaluationZone).toInstant(),
                            local.plus(java.time.Duration.between(event.startValue().localDateTime(), event.endValue().localDateTime()))
                                    .atZone(evaluationZone).toInstant()));
            case UTC -> recurrenceEngine.utc(rrule, event.startValue().instant(), from, to, MAX_OCCURRENCES)
                    .forEach(start -> add(result, start, start.plus(exactDuration)));
            case ZONED -> {
                ZoneId zone = event.startValue().zoneId();
                ZonedDateTime seed = event.startValue().localDateTime().atZone(zone);
                java.time.Duration localDuration = java.time.Duration.between(event.startValue().localDateTime(), event.endValue().localDateTime());
                recurrenceEngine.zoned(rrule, seed, from.atZone(zone), to.atZone(zone), MAX_OCCURRENCES)
                        .forEach(start -> add(result, start.toInstant(), start.toLocalDateTime().plus(localDuration).atZone(zone).toInstant()));
            }
        }
        recurrence.additionalDates().forEach(value -> {
            Instant start = value.toInstant(evaluationZone);
            add(result, start, occurrenceEnd(event, value, start, exactDuration, evaluationZone));
        });
        recurrence.excludedDates().forEach(value -> result.remove(key(value.toInstant(evaluationZone))));
    }

    private Instant occurrenceEnd(
            CalendarEvent event,
            TemporalValue occurrenceStart,
            Instant projectedStart,
            Duration exactDuration,
            ZoneId evaluationZone) {
        return switch (event.startValue().kind()) {
            case DATE -> occurrenceStart.date()
                    .plusDays(java.time.temporal.ChronoUnit.DAYS.between(event.startValue().date(), event.endValue().date()))
                    .atStartOfDay(evaluationZone).toInstant();
            case FLOATING -> occurrenceStart.localDateTime()
                    .plus(Duration.between(event.startValue().localDateTime(), event.endValue().localDateTime()))
                    .atZone(evaluationZone).toInstant();
            case UTC -> projectedStart.plus(exactDuration);
            case ZONED -> occurrenceStart.localDateTime()
                    .plus(Duration.between(event.startValue().localDateTime(), event.endValue().localDateTime()))
                    .atZone(event.startValue().zoneId()).toInstant();
        };
    }

    private void applyOverrides(Map<String, Occurrence> result, CalendarEvent event, ZoneId evaluationZone) {
        for (RecurrenceOverride override : event.overrides()) {
            result.remove(key(override.recurrenceId().toInstant(evaluationZone)));
            if (!override.cancelled()) {
                add(result, override.start().toInstant(evaluationZone), override.end().toInstant(evaluationZone));
            }
        }
    }

    private void add(Map<String, Occurrence> target, Instant start, Instant end) {
        target.put(key(start), new Occurrence(start, end));
    }

    private String key(Instant value) {
        return value.toString();
    }

    private void upsertTemporal(CalendarEvent event, String scopeKey) {
        TemporalValue start = event.startValue();
        TemporalValue end = event.endValue();
        jdbc.update(
                """
                insert into weave_calendar_event_temporals(
                    calendar_id,scope_key,event_id,temporal_kind,start_date,end_date,start_local,end_local,
                    start_instant,end_instant,timezone_id)
                values (?,?,?,?,?,?,?,?,?,?,?)
                on conflict (calendar_id,scope_key,event_id) do update set
                    temporal_kind=excluded.temporal_kind,start_date=excluded.start_date,end_date=excluded.end_date,
                    start_local=excluded.start_local,end_local=excluded.end_local,start_instant=excluded.start_instant,
                    end_instant=excluded.end_instant,timezone_id=excluded.timezone_id
                """,
                event.calendarId().value(), scopeKey, event.id().value(), start.kind().name(),
                start.date(), end.date(), start.localDateTime(), end.localDateTime(), start.instant(), end.instant(), zone(start));
    }

    private void replaceAttendees(CalendarEvent event, String scopeKey) {
        String calendar = event.calendarId().value();
        String id = event.id().value();
        jdbc.update("delete from weave_calendar_attendees where calendar_id=? and scope_key=? and event_id=?", calendar, scopeKey, id);
        int ordinal = 0;
        for (Attendee attendee : event.attendees()) {
            jdbc.update(
                    """
                    insert into weave_calendar_attendees(
                        calendar_id,scope_key,event_id,ordinal,member_ref,display_name,address,attendee_role,response_state)
                    values (?,?,?,?,?,?,?,?,?)
                    """,
                    calendar, scopeKey, id, ordinal++, attendee.memberRef(), attendee.displayName(), attendee.address(), attendee.role(), attendee.response());
        }
    }

    private void replaceRecurrence(CalendarEvent event, String scopeKey) {
        String calendar = event.calendarId().value();
        String id = event.id().value();
        jdbc.update("delete from weave_calendar_recurrence_dates where calendar_id=? and scope_key=? and event_id=?", calendar, scopeKey, id);
        jdbc.update("delete from weave_calendar_recurrence_rules where calendar_id=? and scope_key=? and event_id=?", calendar, scopeKey, id);
        RecurrenceSet recurrence = event.recurrence();
        if (recurrence == null) return;
        jdbc.update(
                """
                insert into weave_calendar_recurrence_rules(
                    calendar_id,scope_key,event_id,frequency,interval_value,count_value,until_local,until_instant,
                    until_timezone_id,by_day,by_month_day,by_month,by_set_pos,week_start)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                calendar, scopeKey, id, recurrence.frequency().name(), recurrence.interval(), recurrence.count(),
                recurrence.until() == null ? null : recurrence.until().toLocalDateTime(),
                recurrence.until() == null ? null : recurrence.until().toInstant(),
                recurrence.until() == null ? null : recurrence.until().getZone().getId(),
                csv(recurrence.byDay()), csv(recurrence.byMonthDay()), csv(recurrence.byMonth()), csv(recurrence.bySetPos()), recurrence.weekStart());
        int ordinal = 0;
        for (TemporalValue value : recurrence.additionalDates()) saveRecurrenceDate(calendar, scopeKey, id, "RDATE", ordinal++, value);
        ordinal = 0;
        for (TemporalValue value : recurrence.excludedDates()) saveRecurrenceDate(calendar, scopeKey, id, "EXDATE", ordinal++, value);
    }

    private void saveRecurrenceDate(
            String calendar,
            String scopeKey,
            String id,
            String type,
            int ordinal,
            TemporalValue value) {
        jdbc.update(
                """
                insert into weave_calendar_recurrence_dates(
                    calendar_id,scope_key,event_id,recurrence_type,ordinal,temporal_kind,date_value,local_value,instant_value,timezone_id)
                values (?,?,?,?,?,?,?,?,?,?)
                """,
                calendar, scopeKey, id, type, ordinal, value.kind().name(), value.date(), value.localDateTime(), value.instant(), zone(value));
    }

    private void replaceOverrides(CalendarEvent event, String scopeKey) {
        String calendar = event.calendarId().value();
        String id = event.id().value();
        jdbc.update("delete from weave_calendar_event_overrides where calendar_id=? and scope_key=? and event_id=?", calendar, scopeKey, id);
        for (RecurrenceOverride override : event.overrides()) {
            TemporalValue recurrenceId = override.recurrenceId();
            TemporalValue start = override.start();
            TemporalValue end = override.end();
            jdbc.update(
                    """
                    insert into weave_calendar_event_overrides(
                        calendar_id,scope_key,event_id,recurrence_id_key,temporal_kind,
                        recurrence_date,recurrence_local,recurrence_instant,recurrence_timezone_id,cancelled,
                        start_date,end_date,start_local,end_local,start_instant,end_instant,timezone_id,title,description,location)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    calendar, scopeKey, id, recurrenceKey(recurrenceId), recurrenceId.kind().name(),
                    recurrenceId.date(), recurrenceId.localDateTime(), recurrenceId.instant(), zone(recurrenceId),
                    override.cancelled(),
                    start == null ? null : start.date(), end == null ? null : end.date(),
                    start == null ? null : start.localDateTime(), end == null ? null : end.localDateTime(),
                    start == null ? null : start.instant(), end == null ? null : end.instant(),
                    start == null ? null : zone(start), override.title(), override.description(), override.location());
        }
    }

    private RecurrenceSet loadRecurrence(String calendar, String scopeKey, String eventId, RecurrenceSet fallback) {
        List<RuleRow> rows = jdbc.query(
                """
                select frequency,interval_value,count_value,until_local,until_instant,until_timezone_id,
                       by_day,by_month_day,by_month,by_set_pos,week_start
                from weave_calendar_recurrence_rules
                where calendar_id=? and scope_key=? and event_id=?
                """,
                (rs, ignored) -> new RuleRow(
                        rs.getString(1), rs.getInt(2), (Integer) rs.getObject(3),
                        rs.getObject(4, LocalDateTime.class), rs.getObject(5, Instant.class), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)),
                calendar, scopeKey, eventId);
        if (rows.isEmpty()) return fallback;
        RuleRow row = rows.getFirst();
        List<TemporalValue> rdates = loadRecurrenceDates(calendar, scopeKey, eventId, "RDATE");
        List<TemporalValue> exdates = loadRecurrenceDates(calendar, scopeKey, eventId, "EXDATE");
        ZonedDateTime until = row.untilInstant != null
                ? row.untilInstant.atZone(row.untilZone == null ? ZoneOffset.UTC : ZoneId.of(row.untilZone))
                : row.untilLocal == null ? null : row.untilLocal.atZone(row.untilZone == null ? ZoneOffset.UTC : ZoneId.of(row.untilZone));
        return new RecurrenceSet(
                RecurrenceFrequency.valueOf(row.frequency), row.interval, row.count, until,
                rdates, exdates,
                strings(row.byDay), integers(row.byMonthDay), integers(row.byMonth), integers(row.bySetPos), row.weekStart);
    }

    private List<TemporalValue> loadRecurrenceDates(String calendar, String scopeKey, String eventId, String type) {
        return jdbc.query(
                """
                select temporal_kind,date_value,local_value,instant_value,timezone_id
                from weave_calendar_recurrence_dates
                where calendar_id=? and scope_key=? and event_id=? and recurrence_type=?
                order by ordinal
                """,
                (rs, ignored) -> temporal(
                        rs.getString(1), rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDateTime.class),
                        rs.getObject(4, Instant.class), rs.getString(5)),
                calendar, scopeKey, eventId, type);
    }

    private List<RecurrenceOverride> loadOverrides(String calendar, String scopeKey, String eventId) {
        return jdbc.query(
                """
                select temporal_kind,recurrence_date,recurrence_local,recurrence_instant,recurrence_timezone_id,cancelled,
                       start_date,end_date,start_local,end_local,start_instant,end_instant,timezone_id,title,description,location
                from weave_calendar_event_overrides
                where calendar_id=? and scope_key=? and event_id=? order by recurrence_id_key
                """,
                (rs, ignored) -> {
                    TemporalKind kind = TemporalKind.valueOf(rs.getString(1));
                    TemporalValue recurrenceId = temporal(kind.name(), rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDateTime.class), rs.getObject(4, Instant.class), rs.getString(5));
                    boolean cancelled = rs.getBoolean(6);
                    TemporalValue start = cancelled ? null : temporal(kind.name(), rs.getObject(7, LocalDate.class), rs.getObject(9, LocalDateTime.class), rs.getObject(11, Instant.class), rs.getString(13));
                    TemporalValue end = cancelled ? null : temporal(kind.name(), rs.getObject(8, LocalDate.class), rs.getObject(10, LocalDateTime.class), rs.getObject(12, Instant.class), rs.getString(13));
                    return new RecurrenceOverride(recurrenceId, start, end, cancelled, rs.getString(14), rs.getString(15), rs.getString(16));
                },
                calendar, scopeKey, eventId);
    }

    private TemporalValue temporal(String kindName, LocalDate date, LocalDateTime local, Instant instant, String zone) {
        return switch (TemporalKind.valueOf(kindName)) {
            case DATE -> TemporalValue.date(date);
            case FLOATING -> TemporalValue.floating(local);
            case UTC -> TemporalValue.utc(instant);
            case ZONED -> TemporalValue.zoned(local, ZoneId.of(zone));
        };
    }

    private String recurrenceKey(TemporalValue value) {
        return value.kind().name() + ':' + switch (value.kind()) {
            case DATE -> value.date().toString();
            case FLOATING -> value.localDateTime().toString();
            case UTC -> value.instant().toString();
            case ZONED -> value.localDateTime() + "[" + value.zoneId().getId() + "]";
        };
    }

    private String zone(TemporalValue value) {
        return value == null || value.zoneId() == null ? null : value.zoneId().getId();
    }

    private String csv(List<?> values) {
        return values == null || values.isEmpty() ? null : values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<String> strings(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }

    private List<Integer> integers(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String item : value.split(",")) result.add(Integer.parseInt(item));
        return List.copyOf(result);
    }

    public record Occurrence(Instant start, Instant end) {}
    private record TemporalPair(TemporalValue start, TemporalValue end) {}
    private record RuleRow(
            String frequency, int interval, Integer count, LocalDateTime untilLocal, Instant untilInstant,
            String untilZone, String byDay, String byMonthDay, String byMonth, String bySetPos, String weekStart) {}
}
