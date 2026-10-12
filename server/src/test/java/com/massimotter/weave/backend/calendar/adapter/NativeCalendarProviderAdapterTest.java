package com.massimotter.weave.backend.calendar.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalKind;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.calendar.CalendarOccurrenceEngine;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import com.massimotter.weave.backend.service.calendar.Ical4jRecurrenceEngine;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class NativeCalendarProviderAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-20T08:00:00Z"), ZoneOffset.UTC);
    private static final CalendarId CALENDAR = new CalendarId("workspace-main");
    private static final CalendarScope WORKSPACE = CalendarScope.workspace();

    @Test
    void canonicalWritesSurviveRestartAndKeepStableVersions() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-restart-v2");
        NativeCalendarProviderAdapter first = adapter(database);
        CalendarEvent created = first.write(new CalendarWrite(event("planning", "Planning"), WriteIntent.CREATE, EventVersion.unknown()));
        NativeCalendarProviderAdapter restarted = adapter(database);

        assertThat(restarted.read(CALENDAR, WORKSPACE, created.id()).title()).isEqualTo("Planning");
        assertThat(restarted.read(CALENDAR, WORKSPACE, created.id()).version()).isEqualTo(created.version());
    }

    @Test
    void updateRequiresMatchingVersionAndDeleteIsDurable() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-update-v2");
        NativeCalendarProviderAdapter adapter = adapter(database);
        CalendarEvent created = adapter.write(new CalendarWrite(event("planning", "Planning"), WriteIntent.CREATE, EventVersion.unknown()));

        assertThatThrownBy(() -> adapter.write(new CalendarWrite(
                withTitle(created, "Wrong"), WriteIntent.UPDATE, new EventVersion("\"stale\""))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> adapter.write(new CalendarWrite(
                created, WriteIntent.UPDATE, new EventVersion("\"stale\""))))
                .isInstanceOf(RuntimeException.class);

        CalendarEvent updated = adapter.write(new CalendarWrite(withTitle(created, "Updated"), WriteIntent.UPDATE, created.version()));
        assertThatThrownBy(() -> adapter.delete(CALENDAR, WORKSPACE, updated.id(), created.version())).isInstanceOf(RuntimeException.class);
        assertThat(adapter.read(CALENDAR, WORKSPACE, updated.id()).title()).isEqualTo("Updated");
        adapter.delete(CALENDAR, WORKSPACE, updated.id(), updated.version());

        assertThatThrownBy(() -> adapter.read(CALENDAR, WORKSPACE, updated.id())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void queryAndFreeBusyUseBoundedIcal4jRecurrenceWithRdateAndExdate() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-recurrence-v2");
        NativeCalendarProviderAdapter adapter = adapter(database);
        adapter.write(new CalendarWrite(event("planning", "Planning"), WriteIntent.CREATE, EventVersion.unknown()));

        Instant from = Instant.parse("2026-03-27T00:00:00Z");
        Instant to = Instant.parse("2026-04-04T00:00:00Z");

        assertThat(adapter.query(CALENDAR, WORKSPACE, from, to)).singleElement();
        assertThat(adapter.freeBusy(CALENDAR, WORKSPACE, from, to))
                .extracting(window -> window.start().atZone(ZoneId.of("Europe/Berlin")).toLocalDate().toString())
                .containsExactly("2026-03-28", "2026-03-29", "2026-03-31", "2026-04-02");
    }

    @Test
    void providerBackedPreviewDoesNotMapOnBrowseAndMaterializedEventSurvivesRestart() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("weave.test.postgres"), "Authoritative PostgreSQL gate");
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-preview-v1");
        CalendarProviderPort first = mock(CalendarProviderPort.class, AdditionalAnswers.delegatesTo(adapter(database)));
        ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
        ContextAuthorizationPort rights = mock(ContextAuthorizationPort.class);
        WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        Map<String, ProviderObjectMapping> mappings = new HashMap<>();
        ProviderBinding binding = new ProviderBinding("tenant-default", "calendar", 1, "weave-native",
                CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH);
        when(bindings.current("tenant-default", "calendar")).thenReturn(Optional.of(binding));
        when(bindings.mappingByProviderRef(anyString(), eq("calendar"), anyLong(), anyString()))
                .thenAnswer(call -> mappings.values().stream().filter(value -> value.providerObjectRef().equals(call.getArgument(3))).findFirst());
        when(bindings.mappingByCanonicalId(anyString(), eq("calendar"), anyLong(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(mappings.get(call.getArgument(3))));
        when(bindings.saveMapping(any())).thenAnswer(call -> {
            ProviderObjectMapping mapping = call.getArgument(0);
            mappings.put(mapping.canonicalObjectId(), mapping);
            return mapping;
        });
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.allow("member"));
        ContextAuthorizationProperties context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
        Jwt member = Jwt.withTokenValue("member").header("alg", "none").subject("member")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")).build();
        CalendarUserApiService service = new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(),
                OrganizationIdentityContextResolver.configured(context), context, rights, capabilities,
                bindings, List.of(first), audit);
        String calendarRef = service.calendars(member).calendars().getFirst().id();
        Instant from = Instant.parse("2026-03-28T00:00:00Z");
        Instant to = Instant.parse("2026-03-29T00:00:00Z");
        assertThat(service.agenda(member, calendarRef, from, to, "UTC").events()).isEmpty();
        ArgumentCaptor<CalendarId> providerCalendar = ArgumentCaptor.forClass(CalendarId.class);
        verify(first).query(providerCalendar.capture(), eq(WORKSPACE), any(), any());
        CalendarEvent source = new CalendarEvent(providerCalendar.getValue(), new EventId("external-native"), WORKSPACE,
                "Provider planning", "Preserved", TemporalValue.floating(LocalDateTime.parse("2026-03-28T09:00:00")),
                TemporalValue.floating(LocalDateTime.parse("2026-03-28T10:00:00")), null, List.of(), null, List.of(),
                EventVersion.unknown(), CLOCK.instant());
        first.write(new CalendarWrite(source, WriteIntent.CREATE, EventVersion.unknown()));

        var agenda = service.agenda(member, calendarRef, from, to, "UTC");
        assertThat(agenda.events()).isEmpty();
        assertThat(agenda.previews()).singleElement().satisfies(preview -> {
            assertThat(preview.handle()).startsWith("pv_").doesNotContain("external-native");
            assertThat(preview.content().title()).isEqualTo("Provider planning");
        });
        assertThat(mappings).isEmpty();
        verify(bindings, never()).saveMapping(any());
        String handle = agenda.previews().getFirst().handle();
        assertThat(service.readPreview(member, calendarRef, handle).content().title()).isEqualTo("Provider planning");
        var materialized = service.materializePreview(member, calendarRef, handle);
        assertThat(service.materializePreview(member, calendarRef, handle).id()).isEqualTo(materialized.id());
        assertThat(mappings).hasSize(1);
        verify(bindings, times(1)).saveMapping(any());

        CalendarUserApiService restarted = new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(),
                OrganizationIdentityContextResolver.configured(context), context, rights, capabilities,
                bindings, List.of(adapter(database)), audit);
        assertThat(restarted.read(member, calendarRef, materialized.id()).content().title()).isEqualTo("Provider planning");
        assertThat(restarted.agenda(member, calendarRef, from, to, "UTC").events())
                .extracting(com.massimotter.weave.backend.model.calendar.CalendarUserModels.Event::id)
                .containsExactly(materialized.id());
    }

    @Test
    void syncTokensAreScopeBoundAndSnapshotBounded() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-sync-v2");
        NativeCalendarProviderAdapter adapter = adapter(database);
        CalendarEvent first = adapter.write(new CalendarWrite(event("one", "One"), WriteIntent.CREATE, EventVersion.unknown()));
        var initial = adapter.changes(CALENDAR, WORKSPACE, null);
        adapter.write(new CalendarWrite(event("two", "Two"), WriteIntent.CREATE, EventVersion.unknown()));

        assertThat(initial.changes()).extracting(change -> change.eventId().value()).containsExactly(first.id().value());
        assertThat(adapter.changes(CALENDAR, WORKSPACE, initial.syncToken()).changes())
                .extracting(change -> change.eventId().value()).containsExactly("two");
    }

    private NativeCalendarProviderAdapter adapter(DataSource database) {
        if (Boolean.getBoolean("weave.test.postgres")) {
            NativeCalendarRelationalStore store = new NativeCalendarRelationalStore(new JdbcTemplate(database));
            NativeCalendarProviderAdapter target = new NativeCalendarProviderAdapter(
                    JpaTestDatabase.repository(database, CalendarCollectionJpaRepository.class),
                    JpaTestDatabase.repository(database, CalendarEventJpaRepository.class),
                    JpaTestDatabase.repository(database, CalendarChangeJpaRepository.class),
                    JpaTestDatabase.repository(database, CalendarSnapshotChangeRepository.class), store,
                    new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()), ZoneOffset.UTC, CLOCK);
            return JpaTestDatabase.transactional(database, target);
        }
        NativeCalendarProviderAdapter target = new NativeCalendarProviderAdapter(
                JpaTestDatabase.repository(database, CalendarCollectionJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarEventJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarChangeJpaRepository.class),
                CLOCK);
        return JpaTestDatabase.transactional(database, target);
    }

    @ParameterizedTest
    @EnumSource(TemporalKind.class)
    void normalizedPostgresPayloadSurvivesRestartWithExactRecurrenceAndOverrides(TemporalKind kind) {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("weave.test.postgres"), "Authoritative PostgreSQL gate");
        DataSource database = JpaTestDatabase.entityFirstDataSource("calendar-normalized-" + kind);
        TemporalValue start = temporal(kind, "2026-03-28T09:00:00");
        TemporalValue end = temporal(kind, kind == TemporalKind.DATE ? "2026-03-29T10:00:00" : "2026-03-28T10:00:00");
        TemporalValue cancelled = temporal(kind, "2026-03-30T09:00:00");
        CalendarEvent incoming = new CalendarEvent(CALENDAR, new EventId("normalized"), WORKSPACE, "Normalized", "Exact payload", start, end,
                "Room", List.of(new Attendee(null, "Member", "member@example.test", "REQ-PARTICIPANT", "ACCEPTED")),
                new RecurrenceSet(RecurrenceFrequency.DAILY, 1, null,
                        (kind == TemporalKind.DATE ? java.time.LocalDate.parse("2026-04-01").atStartOfDay() : LocalDateTime.parse("2026-04-01T09:00:00")).atZone(ZoneOffset.UTC),
                        List.of(temporal(kind, "2026-04-05T09:00:00")), List.of(temporal(kind, "2026-03-29T09:00:00")),
                        List.of(), List.of(), List.of(), List.of(), "MO"),
                List.of(new RecurrenceOverride(cancelled, null, null, true, null, null, null),
                        new RecurrenceOverride(temporal(kind, "2026-03-31T09:00:00"), temporal(kind, "2026-04-03T11:00:00"),
                                temporal(kind, kind == TemporalKind.DATE ? "2026-04-04T12:00:00" : "2026-04-03T12:00:00"),
                                false, "Moved", "Preserved", "Other room")), EventVersion.unknown(), CLOCK.instant());
        CalendarEvent written = adapter(database).write(new CalendarWrite(incoming, WriteIntent.CREATE, EventVersion.unknown()));
        CalendarEvent read = adapter(database).read(CALENDAR, WORKSPACE, incoming.id());
        assertThat(read.startValue()).isEqualTo(incoming.startValue());
        assertThat(read.endValue()).isEqualTo(incoming.endValue());
        assertThat(read.recurrence()).isEqualTo(incoming.recurrence());
        assertThat(read.overrides()).isEqualTo(incoming.overrides());
        assertThat(read.attendees()).isEqualTo(incoming.attendees());
        assertThat(read.version()).isEqualTo(written.version());
        assertThat(adapter(database).query(CALENDAR, WORKSPACE, Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-04-07T00:00:00Z"))).hasSize(1);
        String endColumn = switch (kind) { case DATE -> "end_date"; case FLOATING, ZONED -> "end_local"; case UTC -> "end_instant"; };
        assertThatThrownBy(() -> new JdbcTemplate(database).update("update weave_calendar_event_temporals set " + endColumn + "=null"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        adapter(database).delete(CALENDAR, WORKSPACE, incoming.id(), written.version());
        assertThat(new JdbcTemplate(database).queryForObject("select count(*) from weave_calendar_event_temporals", Integer.class)).isZero();
    }

    private TemporalValue temporal(TemporalKind kind, String value) {
        LocalDateTime local = LocalDateTime.parse(value);
        return switch (kind) {
            case DATE -> TemporalValue.date(local.toLocalDate());
            case FLOATING -> TemporalValue.floating(local);
            case UTC -> TemporalValue.utc(local.toInstant(ZoneOffset.UTC));
            case ZONED -> TemporalValue.zoned(local, ZoneId.of("Europe/Berlin"));
        };
    }

    private CalendarEvent event(String id, String title) {
        ZoneId timezone = ZoneId.of("Europe/Berlin");
        return new CalendarEvent(
                CALENDAR,
                new EventId(id),
                WORKSPACE,
                title,
                "Canonical native event",
                LocalDateTime.parse("2026-03-28T09:00:00"),
                LocalDateTime.parse("2026-03-28T10:00:00"),
                timezone,
                false,
                "Workspace room",
                List.of(new Attendee(
                        "member:alex",
                        "Alex",
                        "mailto:alex@example.test",
                        "REQ-PARTICIPANT",
                        "ACCEPTED")),
                new RecurrenceSet(
                        RecurrenceFrequency.DAILY,
                        1,
                        4,
                        null,
                        List.of(TemporalValue.zoned(LocalDateTime.parse("2026-04-02T09:00:00"), timezone)),
                        List.of(TemporalValue.zoned(LocalDateTime.parse("2026-03-30T09:00:00"), timezone))),
                EventVersion.unknown(),
                null);
    }

    private CalendarEvent withTitle(CalendarEvent existing, String title) {
        return new CalendarEvent(
                existing.calendarId(),
                existing.id(),
                existing.scope(),
                title,
                existing.description(),
                existing.startValue(),
                existing.endValue(),
                existing.location(),
                existing.attendees(),
                existing.recurrence(),
                existing.overrides(),
                existing.version(),
                existing.updatedAt());
    }
}
