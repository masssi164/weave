package com.massimotter.weave.backend.calendar.adapter;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Snapshot-bounded reader for the native Calendar change journal. */
interface CalendarSnapshotChangeRepository
        extends Repository<CalendarChangeJpaEntity, CalendarChangeId> {

    @Query("""
            select change from CalendarChangeJpaEntity change
            where change.id.calendarId = :calendarId
              and change.id.scopeKey = :scopeKey
              and change.id.changeSequence > :afterRevision
              and change.id.changeSequence <= :snapshotHighWater
            order by change.id.changeSequence
            """)
    List<CalendarChangeJpaEntity> findSnapshot(
            @Param("calendarId") String calendarId,
            @Param("scopeKey") String scopeKey,
            @Param("afterRevision") long afterRevision,
            @Param("snapshotHighWater") long snapshotHighWater);
}
