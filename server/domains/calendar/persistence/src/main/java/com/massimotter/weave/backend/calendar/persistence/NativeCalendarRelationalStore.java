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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Domain-owned normalized Calendar payload persistence and database-bounded candidate selection. */
public class NativeCalendarRelationalStore {

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
        LocalDate toDate = to.atZone(evaluationZone).toLocalDate();
        LocalDateTime fromLocal = from.atZone(evaluationZone).toLocalDateTime();
        LocalDateTime toLocal = to.atZone(evaluationZone).toLocalDateTime();
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
                order by event.id.eventId
                """, String.class)
                .setParameter("calendar", calendarId).setParameter("scope", scopeKey)
                .setParameter("fromInstant", CalendarNormalizedValues.offset(from))
                .setParameter("toInstant", CalendarNormalizedValues.offset(to))
                .setParameter("fromDate", fromDate).setParameter("toDate", toDate)
                .setParameter("fromLocal", fromLocal).setParameter("toLocal", toLocal)
                .getResultList();
    }

    /** Compatibility overload for existing callers; UTC is explicit rather than implicit in storage semantics. */
    @Transactional(readOnly = true)
    public List<String> candidateEventIds(String calendarId, String scopeKey, Instant from, Instant to) {
        return candidateEventIds(calendarId, scopeKey, from, to, ZoneOffset.UTC);
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
