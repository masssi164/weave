package com.massimotter.weave.backend.calendar.adapter;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Embeddable
class CalendarCollectionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "calendar_id", nullable = false, length = 96)
    private String calendarId;

    @Column(name = "scope_key", nullable = false, length = 768)
    private String scopeKey;

    protected CalendarCollectionId() {
    }

    CalendarCollectionId(String calendarId, String scopeKey) {
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId");
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey");
    }

    String calendarId() {
        return calendarId;
    }

    String scopeKey() {
        return scopeKey;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof CalendarCollectionId other
                && Objects.equals(calendarId, other.calendarId)
                && Objects.equals(scopeKey, other.scopeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(calendarId, scopeKey);
    }
}

@Entity
@Table(name = "weave_calendar_collections")
class CalendarCollectionJpaEntity {

    @EmbeddedId
    private CalendarCollectionId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private ScopeType scopeType;

    @Column(name = "team_id", length = 255)
    private String teamId;

    @Column(name = "channel_id", length = 255)
    private String channelId;

    @Column(name = "latest_change_sequence", nullable = false)
    private long latestChangeSequence;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected CalendarCollectionJpaEntity() {
    }

    static CalendarCollectionJpaEntity create(
            CalendarCollectionId id,
            CalendarScope scope,
            Instant timestamp) {
        CalendarCollectionJpaEntity entity = new CalendarCollectionJpaEntity();
        entity.id = id;
        entity.scopeType = scope.type();
        entity.teamId = scope.teamId();
        entity.channelId = scope.channelId();
        entity.updatedAt = CalendarPersistenceTime.utc(timestamp);
        return entity;
    }

    long nextSequence(Instant timestamp) {
        latestChangeSequence++;
        updatedAt = CalendarPersistenceTime.utc(timestamp);
        return latestChangeSequence;
    }

    long latestChangeSequence() {
        return latestChangeSequence;
    }
}

interface CalendarCollectionJpaRepository
        extends JpaRepository<CalendarCollectionJpaEntity, CalendarCollectionId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select collection from CalendarCollectionJpaEntity collection where collection.id = :id")
    Optional<CalendarCollectionJpaEntity> lockById(@Param("id") CalendarCollectionId id);
}

@Embeddable
class CalendarEventId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "calendar_id", nullable = false, length = 96)
    private String calendarId;

    @Column(name = "scope_key", nullable = false, length = 768)
    private String scopeKey;

    @Column(name = "event_id", nullable = false, length = 512)
    private String eventId;

    protected CalendarEventId() {
    }

    CalendarEventId(String calendarId, String scopeKey, String eventId) {
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId");
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
    }

    String calendarId() {
        return calendarId;
    }

    String scopeKey() {
        return scopeKey;
    }

    String eventId() {
        return eventId;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof CalendarEventId other
                && Objects.equals(calendarId, other.calendarId)
                && Objects.equals(scopeKey, other.scopeKey)
                && Objects.equals(eventId, other.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(calendarId, scopeKey, eventId);
    }
}

@Entity
@Table(name = "weave_calendar_events")
class CalendarEventJpaEntity {

    @EmbeddedId
    private CalendarEventId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private ScopeType scopeType;

    @Column(name = "team_id", length = 255)
    private String teamId;

    @Column(name = "channel_id", length = 255)
    private String channelId;

    @Column(name = "title", nullable = false, length = 1024)
    private String title;

    @Column(name = "description", length = 8192)
    private String description;

    @Column(name = "local_start", nullable = false)
    private LocalDateTime localStart;

    @Column(name = "local_end", nullable = false)
    private LocalDateTime localEnd;

    @Column(name = "timezone_id", nullable = false, length = 255)
    private String timezoneId;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "location", length = 2048)
    private String location;

    @Column(name = "attendee_state", nullable = false, length = 65535)
    private String attendeeState;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", length = 32)
    private RecurrenceFrequency recurrenceFrequency;

    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval;

    @Column(name = "recurrence_count")
    private Integer recurrenceCount;

    @Column(name = "recurrence_until_local")
    private LocalDateTime recurrenceUntilLocal;

    @Column(name = "recurrence_until_timezone", length = 255)
    private String recurrenceUntilTimezone;

    @Column(name = "recurrence_date_state", nullable = false, length = 65535)
    private String recurrenceDateState;

    @Column(name = "additional_date_count", nullable = false)
    private int additionalDateCount;

    @Column(name = "event_version", nullable = false, length = 128)
    private String eventVersion;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "change_sequence", nullable = false)
    private long changeSequence;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected CalendarEventJpaEntity() {
    }

    static CalendarEventJpaEntity create(CalendarEventId id) {
        CalendarEventJpaEntity entity = new CalendarEventJpaEntity();
        entity.id = id;
        return entity;
    }

    void apply(CalendarEvent event, long sequence, String version, Instant timestamp) {
        scopeType = event.scope().type();
        teamId = event.scope().teamId();
        channelId = event.scope().channelId();
        title = event.title();
        description = event.description();
        localStart = event.localStart();
        localEnd = event.localEnd();
        timezoneId = event.timezone().getId();
        allDay = event.allDay();
        location = event.location();
        attendeeState = CalendarPersistenceCodec.attendees(event.attendees());
        applyRecurrence(event.recurrence());
        eventVersion = version;
        updatedAt = CalendarPersistenceTime.utc(timestamp);
        deleted = false;
        changeSequence = sequence;
    }

    private void applyRecurrence(RecurrenceSet recurrence) {
        recurrenceDateState = "";
        additionalDateCount = 0;
        if (recurrence == null) {
            recurrenceFrequency = null;
            recurrenceInterval = null;
            recurrenceCount = null;
            recurrenceUntilLocal = null;
            recurrenceUntilTimezone = null;
            return;
        }
        recurrenceFrequency = recurrence.frequency();
        recurrenceInterval = recurrence.interval();
        recurrenceCount = recurrence.count();
        recurrenceUntilLocal = recurrence.until() == null ? null : recurrence.until().toLocalDateTime();
        recurrenceUntilTimezone = recurrence.until() == null ? null : recurrence.until().getZone().getId();
        List<ZonedDateTime> recurrenceDates = new java.util.ArrayList<>(recurrence.additionalDates());
        additionalDateCount = recurrenceDates.size();
        recurrenceDates.addAll(recurrence.excludedDates());
        recurrenceDateState = CalendarPersistenceCodec.recurrenceDates(recurrenceDates);
    }

    void markDeleted(long sequence, String version, Instant timestamp) {
        eventVersion = version;
        updatedAt = CalendarPersistenceTime.utc(timestamp);
        deleted = true;
        changeSequence = sequence;
    }

    boolean deleted() {
        return deleted;
    }

    String eventVersion() {
        return eventVersion;
    }

    CalendarEvent toDomain() {
        List<ZonedDateTime> storedDates = CalendarPersistenceCodec.recurrenceDates(recurrenceDateState);
        RecurrenceSet recurrence = recurrenceFrequency == null
                ? null
                : new RecurrenceSet(
                        recurrenceFrequency,
                        recurrenceInterval,
                        recurrenceCount,
                        recurrenceUntilLocal == null
                                ? null
                                : recurrenceUntilLocal.atZone(ZoneId.of(recurrenceUntilTimezone)),
                        storedDates.subList(0, additionalDateCount),
                        storedDates.subList(additionalDateCount, storedDates.size()));
        return new CalendarEvent(
                new CalendarId(id.calendarId()),
                new EventId(id.eventId()),
                new CalendarScope(scopeType, teamId, channelId),
                title,
                description,
                localStart,
                localEnd,
                ZoneId.of(timezoneId),
                allDay,
                location,
                CalendarPersistenceCodec.attendees(attendeeState),
                recurrence,
                new EventVersion(eventVersion),
                CalendarPersistenceTime.instant(updatedAt));
    }
}

interface CalendarEventJpaRepository extends JpaRepository<CalendarEventJpaEntity, CalendarEventId> {

    @Query("""
            select event from CalendarEventJpaEntity event
            where event.id.calendarId = :calendarId
              and event.id.scopeKey = :scopeKey
              and event.deleted = false
            order by event.localStart, event.id.eventId
            """)
    List<CalendarEventJpaEntity> findActive(
            @Param("calendarId") String calendarId,
            @Param("scopeKey") String scopeKey);
}

@Embeddable
class CalendarChangeId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "calendar_id", nullable = false, length = 96)
    private String calendarId;

    @Column(name = "scope_key", nullable = false, length = 768)
    private String scopeKey;

    @Column(name = "change_sequence", nullable = false)
    private long changeSequence;

    protected CalendarChangeId() {
    }

    CalendarChangeId(String calendarId, String scopeKey, long changeSequence) {
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId");
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey");
        this.changeSequence = changeSequence;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof CalendarChangeId other
                && changeSequence == other.changeSequence
                && Objects.equals(calendarId, other.calendarId)
                && Objects.equals(scopeKey, other.scopeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(calendarId, scopeKey, changeSequence);
    }
}

@Entity
@Table(name = "weave_calendar_changes")
class CalendarChangeJpaEntity {

    @EmbeddedId
    private CalendarChangeId id;

    @Column(name = "event_id", nullable = false, length = 512)
    private String eventId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "event_version", nullable = false, length = 128)
    private String eventVersion;

    @Column(name = "changed_at_utc", nullable = false)
    private OffsetDateTime changedAt;

    protected CalendarChangeJpaEntity() {
    }

    static CalendarChangeJpaEntity create(
            CalendarChangeId id,
            String eventId,
            boolean deleted,
            String eventVersion,
            Instant changedAt) {
        CalendarChangeJpaEntity entity = new CalendarChangeJpaEntity();
        entity.id = id;
        entity.eventId = eventId;
        entity.deleted = deleted;
        entity.eventVersion = eventVersion;
        entity.changedAt = CalendarPersistenceTime.utc(changedAt);
        return entity;
    }

    String eventId() {
        return eventId;
    }

    boolean deleted() {
        return deleted;
    }

    String eventVersion() {
        return eventVersion;
    }
}

interface CalendarChangeJpaRepository extends JpaRepository<CalendarChangeJpaEntity, CalendarChangeId> {

    @Query("""
            select change from CalendarChangeJpaEntity change
            where change.id.calendarId = :calendarId
              and change.id.scopeKey = :scopeKey
              and change.id.changeSequence > :sinceSequence
            order by change.id.changeSequence
            """)
    List<CalendarChangeJpaEntity> findSince(
            @Param("calendarId") String calendarId,
            @Param("scopeKey") String scopeKey,
            @Param("sinceSequence") long sinceSequence);
}

final class CalendarPersistenceTime {

    private CalendarPersistenceTime() {
    }

    static OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    static Instant instant(OffsetDateTime value) {
        return value.toInstant();
    }
}

final class CalendarPersistenceCodec {

    private static final String ITEM_SEPARATOR = ",";
    private static final String FIELD_SEPARATOR = "\\.";

    private CalendarPersistenceCodec() {
    }

    static String attendees(List<Attendee> attendees) {
        return attendees.stream()
                .map(attendee -> String.join(".",
                        encode(attendee.memberRef()),
                        encode(attendee.displayName()),
                        encode(attendee.address()),
                        encode(attendee.role()),
                        encode(attendee.response())))
                .collect(java.util.stream.Collectors.joining(ITEM_SEPARATOR));
    }

    static List<Attendee> attendees(String state) {
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(state.split(ITEM_SEPARATOR, -1))
                .map(item -> item.split(FIELD_SEPARATOR, -1))
                .map(fields -> {
                    if (fields.length != 5) {
                        throw new IllegalStateException("stored calendar attendee state is invalid");
                    }
                    return new Attendee(
                            decode(fields[0]),
                            decode(fields[1]),
                            decode(fields[2]),
                            decode(fields[3]),
                            decode(fields[4]));
                })
                .toList();
    }

    static String recurrenceDates(List<ZonedDateTime> values) {
        return values.stream()
                .map(value -> encode(value.toString()))
                .collect(java.util.stream.Collectors.joining(ITEM_SEPARATOR));
    }

    static List<ZonedDateTime> recurrenceDates(String state) {
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(state.split(ITEM_SEPARATOR, -1))
                .map(CalendarPersistenceCodec::decode)
                .map(ZonedDateTime::parse)
                .toList();
    }

    private static String encode(String value) {
        if (value == null) {
            return "-";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if ("-".equals(value)) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
