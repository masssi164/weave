package com.massimotter.weave.backend.calendar.adapter;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChangeSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(
        name = "weave.calendar.provider",
        havingValue = "weave-native",
        matchIfMissing = true)
public class NativeCalendarProviderAdapter implements CalendarProviderPort {

    static final String SYNC_TOKEN_PREFIX = "weave-native-calendar-sync-";

    private final CalendarCollectionJpaRepository collections;
    private final CalendarEventJpaRepository events;
    private final CalendarChangeJpaRepository changes;
    private final Clock clock;

    @Autowired
    NativeCalendarProviderAdapter(
            CalendarCollectionJpaRepository collections,
            CalendarEventJpaRepository events,
            CalendarChangeJpaRepository changes) {
        this(collections, events, changes, Clock.systemUTC());
    }

    NativeCalendarProviderAdapter(
            CalendarCollectionJpaRepository collections,
            CalendarEventJpaRepository events,
            CalendarChangeJpaRepository changes,
            Clock clock) {
        this.collections = Objects.requireNonNull(collections, "collections");
        this.events = Objects.requireNonNull(events, "events");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public ProviderReadiness readiness() {
        return ProviderReadiness.ready("weave-native-calendar-ready");
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "calendar",
                "weave-native",
                Set.of(
                        "query",
                        "read",
                        "create",
                        "update",
                        "delete",
                        "free-busy",
                        "bounded-recurrence",
                        "etag",
                        "sync-token"),
                Map.of(
                        "event", MappingClass.PORTABLE,
                        "timezone", MappingClass.PORTABLE,
                        "boundedRecurrence", MappingClass.PORTABLE,
                        "attendee", MappingClass.PORTABLE,
                        "reminder", MappingClass.UNSUPPORTED,
                        "meetingLink", MappingClass.UNSUPPORTED,
                        "syncToken", MappingClass.PORTABLE),
                true,
                true,
                true);
    }

    @Override
    public boolean ownsNorthboundSyncTokens() {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEvent> query(
            CalendarId calendarId,
            CalendarScope scope,
            Instant from,
            Instant to) {
        requireCalendarAndScope(calendarId, scope);
        List<CalendarEvent> active = events.findActive(calendarId.value(), scopeKey(scope)).stream()
                .map(CalendarEventJpaEntity::toDomain)
                .toList();
        if (from == null || to == null) {
            return active;
        }
        requireRange(from, to);
        return active.stream()
                .filter(event -> !event.occurrences(from, to).isEmpty())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
        requireCalendarAndScope(calendarId, scope);
        if (id == null) {
            throw invalid("read-event", "event id is required");
        }
        CalendarEventJpaEntity entity = events.findById(eventKey(calendarId, scope, id))
                .filter(candidate -> !candidate.deleted())
                .orElseThrow(() -> notFound("read-event"));
        return entity.toDomain();
    }

    @Override
    @Transactional
    public CalendarEvent write(CalendarWrite write) {
        if (write == null) {
            throw invalid("write-event", "calendar write is required");
        }
        CalendarEvent incoming = write.event();
        requireCalendarAndScope(incoming.calendarId(), incoming.scope());
        CalendarCollectionJpaEntity collection = lockedCollection(
                incoming.calendarId(), incoming.scope(), clock.instant());
        CalendarEventId key = eventKey(incoming.calendarId(), incoming.scope(), incoming.id());
        CalendarEventJpaEntity current = events.findById(key).orElse(null);

        if (write.intent() == WriteIntent.CREATE && current != null && !current.deleted()) {
            if (sameEvent(current.toDomain(), incoming)) {
                return current.toDomain();
            }
            throw conflict("create-event");
        }
        if (write.intent() == WriteIntent.UPDATE && (current == null || current.deleted())) {
            throw notFound("update-event");
        }
        if (current != null && !current.deleted() && sameEvent(current.toDomain(), incoming)) {
            return current.toDomain();
        }
        if (write.intent() == WriteIntent.UPDATE) {
            requireExpectedVersion(write.expectedVersion(), current.eventVersion(), "update-event");
        }

        Instant timestamp = clock.instant();
        long sequence = collection.nextSequence(timestamp);
        String version = eventVersion(incoming, sequence);
        CalendarEventJpaEntity entity = current == null ? CalendarEventJpaEntity.create(key) : current;
        entity.apply(incoming, sequence, version, timestamp);
        collections.save(collection);
        events.save(entity);
        changes.save(CalendarChangeJpaEntity.create(
                new CalendarChangeId(
                        incoming.calendarId().value(),
                        scopeKey(incoming.scope()),
                        sequence),
                incoming.id().value(),
                false,
                version,
                timestamp));
        return entity.toDomain();
    }

    @Override
    @Transactional
    public void delete(
            CalendarId calendarId,
            CalendarScope scope,
            EventId id,
            EventVersion expectedVersion) {
        requireCalendarAndScope(calendarId, scope);
        if (id == null) {
            throw invalid("delete-event", "event id is required");
        }
        CalendarEventJpaEntity entity = events.findById(eventKey(calendarId, scope, id))
                .orElseThrow(() -> notFound("delete-event"));
        if (entity.deleted()) {
            return;
        }
        requireExpectedVersion(expectedVersion, entity.eventVersion(), "delete-event");
        Instant timestamp = clock.instant();
        CalendarCollectionJpaEntity collection = lockedCollection(calendarId, scope, timestamp);
        long sequence = collection.nextSequence(timestamp);
        String version = tombstoneVersion(calendarId, scope, id, sequence);
        entity.markDeleted(sequence, version, timestamp);
        collections.save(collection);
        events.save(entity);
        changes.save(CalendarChangeJpaEntity.create(
                new CalendarChangeId(calendarId.value(), scopeKey(scope), sequence),
                id.value(),
                true,
                version,
                timestamp));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreeBusyWindow> freeBusy(
            CalendarId calendarId,
            CalendarScope scope,
            Instant from,
            Instant to) {
        requireRange(from, to);
        return query(calendarId, scope, from, to).stream()
                .flatMap(event -> event.occurrences(from, to).stream())
                .map(occurrence -> new FreeBusyWindow(
                        occurrence.start().toInstant(),
                        occurrence.end().toInstant()))
                .sorted(Comparator.comparing(FreeBusyWindow::start))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarChangeSet changes(
            CalendarId calendarId,
            CalendarScope scope,
            String sinceToken) {
        requireCalendarAndScope(calendarId, scope);
        CalendarCollectionId collectionId = collectionKey(calendarId, scope);
        long latestSequence = collections.findById(collectionId)
                .map(CalendarCollectionJpaEntity::latestChangeSequence)
                .orElse(0L);
        long sinceSequence = parseSyncToken(calendarId, scope, sinceToken, latestSequence);
        String nextToken = syncToken(calendarId, scope, latestSequence);
        List<CalendarChange> changed = changes.findSince(
                        calendarId.value(), scopeKey(scope), sinceSequence).stream()
                .map(change -> new CalendarChange(
                        nextToken,
                        new EventId(change.eventId()),
                        change.deleted(),
                        new EventVersion(change.eventVersion())))
                .toList();
        return new CalendarChangeSet(nextToken, changed);
    }

    private CalendarCollectionJpaEntity lockedCollection(
            CalendarId calendarId,
            CalendarScope scope,
            Instant timestamp) {
        CalendarCollectionId id = collectionKey(calendarId, scope);
        CalendarCollectionJpaEntity existing = collections.lockById(id).orElse(null);
        if (existing != null) {
            return existing;
        }
        return collections.saveAndFlush(CalendarCollectionJpaEntity.create(id, scope, timestamp));
    }

    private void requireExpectedVersion(
            EventVersion expected,
            String current,
            String operation) {
        if (expected != null
                && expected.value() != null
                && !expected.value().equals(current)) {
            throw conflict(operation);
        }
    }

    private long parseSyncToken(
            CalendarId calendarId,
            CalendarScope scope,
            String token,
            long latestSequence) {
        if (token == null || token.isBlank()) {
            return 0L;
        }
        String prefix = SYNC_TOKEN_PREFIX + scopeDigest(calendarId, scope) + "-";
        String normalized = token.trim();
        if (!normalized.startsWith(prefix)) {
            throw conflict("sync-events");
        }
        try {
            long sequence = Long.parseLong(normalized.substring(prefix.length()));
            if (sequence < 0 || sequence > latestSequence) {
                throw conflict("sync-events");
            }
            return sequence;
        } catch (NumberFormatException exception) {
            throw conflict("sync-events");
        }
    }

    private String syncToken(CalendarId calendarId, CalendarScope scope, long sequence) {
        return SYNC_TOKEN_PREFIX + scopeDigest(calendarId, scope) + "-" + sequence;
    }

    private String scopeDigest(CalendarId calendarId, CalendarScope scope) {
        return digest(calendarId.value() + "\u0000" + scopeKey(scope)).substring(0, 24);
    }

    private String eventVersion(CalendarEvent event, long sequence) {
        return "\"weave-calendar-" + sequence + "-" + eventFingerprint(event).substring(0, 24) + "\"";
    }

    private String tombstoneVersion(
            CalendarId calendarId,
            CalendarScope scope,
            EventId eventId,
            long sequence) {
        return "\"weave-calendar-" + sequence + "-"
                + digest(calendarId.value() + "\u0000" + scopeKey(scope) + "\u0000" + eventId.value() + "\u0000deleted")
                        .substring(0, 24)
                + "\"";
    }

    private boolean sameEvent(CalendarEvent stored, CalendarEvent incoming) {
        return eventFingerprint(stored).equals(eventFingerprint(incoming));
    }

    private String eventFingerprint(CalendarEvent event) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, event.calendarId().value());
        append(canonical, event.id().value());
        append(canonical, scopeKey(event.scope()));
        append(canonical, event.title());
        append(canonical, event.description());
        append(canonical, event.localStart().toString());
        append(canonical, event.localEnd().toString());
        append(canonical, event.timezone().getId());
        append(canonical, Boolean.toString(event.allDay()));
        append(canonical, event.location());
        for (Attendee attendee : event.attendees()) {
            append(canonical, attendee.memberRef());
            append(canonical, attendee.displayName());
            append(canonical, attendee.address());
            append(canonical, attendee.role());
            append(canonical, attendee.response());
        }
        RecurrenceSet recurrence = event.recurrence();
        if (recurrence == null) {
            append(canonical, null);
        } else {
            append(canonical, recurrence.frequency().name());
            append(canonical, Integer.toString(recurrence.interval()));
            append(canonical, recurrence.count() == null ? null : recurrence.count().toString());
            append(canonical, recurrence.until() == null ? null : recurrence.until().toString());
            recurrence.additionalDates().forEach(value -> append(canonical, value.toString()));
            recurrence.excludedDates().forEach(value -> append(canonical, value.toString()));
        }
        return digest(canonical.toString());
    }

    private static void append(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append("-1:");
        } else {
            canonical.append(value.length()).append(':').append(value);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static CalendarCollectionId collectionKey(CalendarId calendarId, CalendarScope scope) {
        return new CalendarCollectionId(calendarId.value(), scopeKey(scope));
    }

    private static CalendarEventId eventKey(
            CalendarId calendarId,
            CalendarScope scope,
            EventId eventId) {
        return new CalendarEventId(calendarId.value(), scopeKey(scope), eventId.value());
    }

    private static String scopeKey(CalendarScope scope) {
        return scope.type().name()
                + ":" + encode(scope.teamId())
                + ":" + encode(scope.channelId());
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireCalendarAndScope(CalendarId calendarId, CalendarScope scope) {
        if (calendarId == null || scope == null) {
            throw invalid("calendar-operation", "calendar id and scope are required");
        }
    }

    private static void requireRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw invalid("calendar-query", "calendar query requires a bounded valid time range");
        }
    }

    private static CalendarAdapterException invalid(String operation, String message) {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.INVALID_REQUEST,
                message,
                details(operation));
    }

    private static CalendarAdapterException notFound(String operation) {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.NOT_FOUND,
                "Calendar event was not found.",
                details(operation));
    }

    private static CalendarAdapterException conflict(String operation) {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.CONFLICT,
                "Calendar state precondition was not satisfied.",
                details(operation));
    }

    private static Map<String, Object> details(String operation) {
        return Map.of(
                "module", "calendar",
                "operation", operation,
                "supportSafe", true,
                "providerDataPlaneExposed", false,
                "diagnosticsRedacted", true);
    }
}
