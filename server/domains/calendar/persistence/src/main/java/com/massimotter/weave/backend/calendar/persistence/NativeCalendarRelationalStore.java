package com.massimotter.weave.backend.calendar.persistence;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Domain-owned normalized Calendar payload persistence and database-bounded candidate selection. */
public class NativeCalendarRelationalStore {

    static final int READ_BATCH_SIZE = 200;

    private final EntityManager entities;

    @Autowired
    NativeCalendarRelationalStore(EntityManager entities) {
        this.entities = Objects.requireNonNull(entities, "entities");
    }

    @Transactional
    public void save(CalendarEvent event, String scopeKey) {
        CalendarEventId id = new CalendarEventId(event.calendarId().value(), scopeKey, event.id().value());
        entities.merge(new CalendarTemporalJpaEntity(id, event.startValue(), event.endValue()));
        removePayloadChildren(id);
        // Delete existing keys before inserting replacements, including repeated writes in one transaction.
        entities.flush();
        int ordinal = 0;
        for (Attendee attendee : event.attendees()) {
            entities.persist(new CalendarAttendeeJpaEntity(new CalendarAttendeeId(id, ordinal++), attendee));
        }
        RecurrenceSet recurrence = event.recurrence();
        if (recurrence != null) {
            entities.persist(new CalendarRecurrenceRuleJpaEntity(id, recurrence));
            saveRecurrenceDates(id, "RDATE", recurrence.additionalDates());
            saveRecurrenceDates(id, "EXDATE", recurrence.excludedDates());
        }
        for (RecurrenceOverride override : event.overrides()) {
            entities.persist(new CalendarOverrideJpaEntity(
                    new CalendarOverrideId(id, recurrenceKey(override.recurrenceId())), override));
        }
    }

    @Transactional
    public void delete(String calendarId, String scopeKey, String eventId) {
        CalendarEventId id = new CalendarEventId(calendarId, scopeKey, eventId);
        removePayloadChildren(id);
        CalendarTemporalJpaEntity temporal = entities.find(CalendarTemporalJpaEntity.class, id);
        if (temporal != null) entities.remove(temporal);
    }

    private void removePayloadChildren(CalendarEventId id) {
        eventRows(CalendarOverrideJpaEntity.class, id, "id.event", "id.recurrenceKey").forEach(entities::remove);
        eventRows(CalendarRecurrenceDateJpaEntity.class, id, "id.event", "id.type, payload.id.ordinal").forEach(entities::remove);
        CalendarRecurrenceRuleJpaEntity rule = entities.find(CalendarRecurrenceRuleJpaEntity.class, id);
        if (rule != null) entities.remove(rule);
        eventRows(CalendarAttendeeJpaEntity.class, id, "id.event", "id.ordinal").forEach(entities::remove);
    }

    private <T> List<T> eventRows(Class<T> type, CalendarEventId id, String idPath, String order) {
        return entities.createQuery("select payload from " + type.getSimpleName()
                        + " payload where payload." + idPath + " = :event order by payload." + order, type)
                .setParameter("event", id).getResultList();
    }

    private void saveRecurrenceDates(CalendarEventId id, String type, List<TemporalValue> values) {
        for (int ordinal = 0; ordinal < values.size(); ordinal++) {
            entities.persist(new CalendarRecurrenceDateJpaEntity(
                    new CalendarRecurrenceDateId(id, type, ordinal), values.get(ordinal)));
        }
    }

    @Transactional(readOnly = true)
    public CalendarEvent enrich(CalendarEvent fallback, String scopeKey) {
        CalendarEventId id = new CalendarEventId(fallback.calendarId().value(), scopeKey, fallback.id().value());
        CalendarTemporalJpaEntity temporal = entities.find(CalendarTemporalJpaEntity.class, id);
        if (temporal == null) return fallback;
        List<Attendee> attendees = eventRows(CalendarAttendeeJpaEntity.class, id, "id.event", "id.ordinal")
                .stream().map(CalendarAttendeeJpaEntity::toDomain).toList();
        CalendarRecurrenceRuleJpaEntity rule = entities.find(CalendarRecurrenceRuleJpaEntity.class, id);
        RecurrenceSet recurrence = rule == null ? fallback.recurrence()
                : rule.toDomain(recurrenceDates(id, "RDATE"), recurrenceDates(id, "EXDATE"));
        List<RecurrenceOverride> overrides = eventRows(CalendarOverrideJpaEntity.class, id, "id.event", "id.recurrenceKey")
                .stream().map(CalendarOverrideJpaEntity::toDomain).toList();
        return new CalendarEvent(
                fallback.calendarId(), fallback.id(), fallback.scope(), fallback.title(), fallback.description(),
                temporal.start(), temporal.end(), fallback.location(), attendees, recurrence, overrides,
                fallback.version(), fallback.updatedAt());
    }

    /** Batch only already selected identities; no whole-calendar payload scan or result cap. */
    @Transactional(readOnly = true)
    public List<CalendarEvent> enrichEvents(List<CalendarEvent> selected, String scopeKey) {
        if (selected.isEmpty()) return List.of();
        String calendar = selected.getFirst().calendarId().value();
        List<CalendarEvent> enriched = new ArrayList<>(selected.size());
        for (int offset = 0; offset < selected.size(); offset += READ_BATCH_SIZE) {
            List<CalendarEvent> batch = selected.subList(offset, Math.min(offset + READ_BATCH_SIZE, selected.size()));
            List<String> ids = batch.stream().map(event -> event.id().value()).toList();
            var temporals = payloadRows(CalendarTemporalJpaEntity.class, "id", calendar, scopeKey, ids, "id.eventId")
                    .stream().collect(Collectors.toMap(row -> row.id().eventId(), Function.identity()));
            var attendees = payloadRows(CalendarAttendeeJpaEntity.class, "id.event", calendar, scopeKey, ids, "id.ordinal")
                    .stream().collect(Collectors.groupingBy(row -> row.eventId().eventId()));
            var rules = payloadRows(CalendarRecurrenceRuleJpaEntity.class, "id", calendar, scopeKey, ids, "id.eventId")
                    .stream().collect(Collectors.toMap(row -> row.id().eventId(), Function.identity()));
            var dates = payloadRows(CalendarRecurrenceDateJpaEntity.class, "id.event", calendar, scopeKey, ids, "id.ordinal")
                    .stream().collect(Collectors.groupingBy(row -> row.eventId().eventId()));
            var overrides = payloadRows(CalendarOverrideJpaEntity.class, "id.event", calendar, scopeKey, ids, "id.recurrenceKey")
                    .stream().collect(Collectors.groupingBy(row -> row.eventId().eventId()));
            for (CalendarEvent fallback : batch) {
                String id = fallback.id().value();
                var temporal = temporals.get(id);
                if (temporal == null) {
                    enriched.add(fallback);
                    continue;
                }
                var recurrence = rules.get(id);
                List<CalendarRecurrenceDateJpaEntity> eventDates = dates.getOrDefault(id, List.of());
                RecurrenceSet rule = recurrence == null ? fallback.recurrence() : recurrence.toDomain(
                        eventDates.stream().filter(row -> row.type().equals("RDATE")).map(CalendarRecurrenceDateJpaEntity::toDomain).toList(),
                        eventDates.stream().filter(row -> row.type().equals("EXDATE")).map(CalendarRecurrenceDateJpaEntity::toDomain).toList());
                enriched.add(new CalendarEvent(fallback.calendarId(), fallback.id(), fallback.scope(), fallback.title(), fallback.description(),
                        temporal.start(), temporal.end(), fallback.location(),
                        attendees.getOrDefault(id, List.of()).stream().map(CalendarAttendeeJpaEntity::toDomain).toList(), rule,
                        overrides.getOrDefault(id, List.of()).stream().map(CalendarOverrideJpaEntity::toDomain).toList(),
                        fallback.version(), fallback.updatedAt()));
            }
        }
        return List.copyOf(enriched);
    }

    private <T> List<T> payloadRows(Class<T> type, String idPath, String calendar, String scope,
            List<String> ids, String order) {
        return entities.createQuery("select payload from " + type.getSimpleName()
                        + " payload where payload." + idPath + ".calendarId = :calendar and payload."
                        + idPath + ".scopeKey = :scope and payload." + idPath + ".eventId in :ids order by payload." + order, type)
                .setParameter("calendar", calendar).setParameter("scope", scope).setParameter("ids", ids).getResultList();
    }

    private List<TemporalValue> recurrenceDates(CalendarEventId id, String type) {
        return entities.createQuery("""
                        select payload from CalendarRecurrenceDateJpaEntity payload
                        where payload.id.event = :event and payload.id.type = :type order by payload.id.ordinal
                        """, CalendarRecurrenceDateJpaEntity.class)
                .setParameter("event", id).setParameter("type", type).getResultList().stream()
                .map(CalendarRecurrenceDateJpaEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<String> candidateEventIds(
            String calendarId, String scopeKey, Instant from, Instant to, ZoneId evaluationZone) {
        LocalDate fromDate = from.atZone(evaluationZone).toLocalDate();
        var localEnd = to.atZone(evaluationZone);
        LocalDate toDate = localEnd.toLocalDate();
        if (!localEnd.toLocalTime().equals(LocalTime.MIDNIGHT)) toDate = toDate.plusDays(1);
        // Every legal offset is within MIN/MAX. This is a bounded candidate superset, not
        // a timezone conversion: exact FLOATING/ZONED overlap (including DST) stays in
        // CalendarOccurrenceEngine. Evaluation-zone wall-clock bounds can reverse at DST.
        LocalDateTime fromLocal = from.atOffset(ZoneOffset.MIN).toLocalDateTime();
        LocalDateTime toLocal = to.atOffset(ZoneOffset.MAX).toLocalDateTime();
        return entities.createQuery("""
                select event.id.eventId from CalendarEventJpaEntity event
                join CalendarTemporalJpaEntity temporal on temporal.id = event.id
                where event.id.calendarId = :calendar and event.id.scopeKey = :scope and event.deleted = false
                  and (
                    exists (select rule.id from CalendarRecurrenceRuleJpaEntity rule where rule.id = event.id)
                    or (temporal.kind = 'UTC' and temporal.startInstant < :toInstant and temporal.endInstant > :fromInstant)
                    or (temporal.kind = 'DATE' and temporal.startDate < :toDate and temporal.endDate > :fromDate)
                    or (temporal.kind in ('ZONED', 'FLOATING') and temporal.startLocal < :toLocal and temporal.endLocal > :fromLocal)
                    or exists (select override.id from CalendarOverrideJpaEntity override
                        where override.id.event = event.id and override.cancelled = false and (
                            (override.startInstant is not null and override.startInstant < :toInstant and override.endInstant > :fromInstant)
                            or (override.startDate is not null and override.startDate < :toDate and override.endDate > :fromDate)
                            or (override.startLocal is not null and override.startLocal < :toLocal and override.endLocal > :fromLocal)))
                  )
                order by event.localStart, event.id.eventId
                """, String.class)
                .setParameter("calendar", calendarId).setParameter("scope", scopeKey)
                .setParameter("fromInstant", CalendarNormalizedValues.offset(from))
                .setParameter("toInstant", CalendarNormalizedValues.offset(to))
                .setParameter("fromDate", fromDate).setParameter("toDate", toDate)
                .setParameter("fromLocal", fromLocal).setParameter("toLocal", toLocal)
                .getResultList();
    }

    private String recurrenceKey(TemporalValue value) {
        return value.kind().name() + ':' + switch (value.kind()) {
            case DATE -> value.date().toString();
            case FLOATING -> value.localDateTime().toString();
            case UTC -> value.instant().toString();
            case ZONED -> value.localDateTime() + "[" + value.zoneId().getId() + "]";
        };
    }

}
