package com.massimotter.weave.backend.calendar.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarJpaEntityContractTest {
    @Test
    void collectionAndNormalizedPayloadMappingsShareTheCalendarLibrary() {
        var tables = Map.of(
                CalendarCollectionJpaEntity.class, "weave_calendar_collections",
                CalendarTemporalJpaEntity.class, "weave_calendar_event_temporals",
                CalendarRecurrenceRuleJpaEntity.class, "weave_calendar_recurrence_rules",
                CalendarRecurrenceDateJpaEntity.class, "weave_calendar_recurrence_dates",
                CalendarAttendeeJpaEntity.class, "weave_calendar_attendees",
                CalendarOverrideJpaEntity.class, "weave_calendar_event_overrides");
        var owner = CalendarCollectionJpaEntity.class.getProtectionDomain().getCodeSource().getLocation();
        tables.forEach((type, table) -> {
            assertThat(type.getAnnotation(Entity.class)).as(type.getName()).isNotNull();
            assertThat(type.getAnnotation(Table.class).name()).isEqualTo(table);
            assertThat(type.getProtectionDomain().getCodeSource().getLocation()).isEqualTo(owner);
        });
    }
}
