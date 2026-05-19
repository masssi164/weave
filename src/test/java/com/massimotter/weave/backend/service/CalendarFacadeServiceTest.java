package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.service.calendar.CalendarAdapter;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import com.massimotter.weave.backend.service.calendar.CalendarPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarFacadeServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesListToAdapterWithNextcloudUserClaim() {
        AtomicReference<CalendarPrincipal> capturedPrincipal = new AtomicReference<>();
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-04-26T10:00:00+02:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-04-26T11:00:00+02:00");
        CalendarEventResponse event = new CalendarEventResponse(
                "event-id", "Planning", null, startsAt, endsAt, "Europe/Berlin", null, false, "etag");
        CalendarAdapter adapter = new StubCalendarAdapter() {
            @Override
            public List<CalendarEventResponse> list(CalendarPrincipal principal, OffsetDateTime from, OffsetDateTime to) {
                capturedPrincipal.set(principal);
                return List.of(event);
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        var response = service(adapter).list(startsAt.minusDays(1), endsAt.plusDays(1));

        assertThat(response.scope().type()).isEqualTo("workspace");
        assertThat(response.events()).containsExactly(event);
        assertThat(capturedPrincipal.get().subject()).isEqualTo("user-123");
        assertThat(capturedPrincipal.get().nextcloudUserId()).isEqualTo("massimo");
    }

    @Test
    void exposesWorkspaceTeamAndChannelCalendarScopes() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        var response = service(new StubCalendarAdapter()).scopes();

        assertThat(response.scopes()).extracting(CalendarScopeResponse::type)
                .containsExactly("workspace", "team", "channel");
        assertThat(response.scopes().get(1).teamId()).isEqualTo("engineering");
        assertThat(response.scopes().get(2).channelId()).isEqualTo("engineering-general");
        assertThat(response.scopes().get(2).capabilities()).contains("read", "create", "edit", "delete");
    }

    @Test
    void listCanReturnChannelScopedEventsWithScopedFacadeIds() {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-04-26T10:00:00+02:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-04-26T11:00:00+02:00");
        CalendarEventResponse event = new CalendarEventResponse(
                "raw-event-id", "Planning", null, startsAt, endsAt, "Europe/Berlin", null, false, "etag");
        CalendarAdapter adapter = new StubCalendarAdapter() {
            @Override
            public List<CalendarEventResponse> list(CalendarPrincipal principal, OffsetDateTime from, OffsetDateTime to) {
                return List.of(event);
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        var response = service(adapter).list(startsAt.minusDays(1), endsAt.plusDays(1),
                "channel", "engineering", "engineering-general");

        assertThat(response.scope().type()).isEqualTo("channel");
        assertThat(response.scope().teamId()).isEqualTo("engineering");
        assertThat(response.scope().channelId()).isEqualTo("engineering-general");
        CalendarEventResponse scopedEvent = response.events().get(0);
        assertThat(scopedEvent.scope().type()).isEqualTo("channel");
        assertThat(scopedEvent.id()).startsWith("scoped:");
    }

    @Test
    void createReadAndDeletePreserveTeamScopeFacadeIds() {
        AtomicReference<String> readId = new AtomicReference<>();
        AtomicReference<String> deletedId = new AtomicReference<>();
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-04-26T10:00:00+02:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-04-26T11:00:00+02:00");
        CalendarEventResponse event = new CalendarEventResponse(
                "raw-event-id", "Planning", null, startsAt, endsAt, "Europe/Berlin", null, false, "etag");
        CalendarAdapter adapter = new StubCalendarAdapter() {
            @Override
            public CalendarEventResponse create(CalendarPrincipal principal, CreateCalendarEventRequest request) {
                assertThat(request.scope().type()).isEqualTo("team");
                return event;
            }

            @Override
            public CalendarEventResponse read(CalendarPrincipal principal, String id) {
                readId.set(id);
                return event;
            }

            @Override
            public void delete(CalendarPrincipal principal, String id) {
                deletedId.set(id);
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Planning",
                null,
                startsAt,
                endsAt,
                "Europe/Berlin",
                null,
                false,
                CalendarScopeResponse.team("engineering", "Engineering team calendar"));

        var created = service(adapter).create(request);
        var read = service(adapter).read(created.id());
        service(adapter).delete(created.id());

        assertThat(created.scope().type()).isEqualTo("team");
        assertThat(read.scope().teamId()).isEqualTo("engineering");
        assertThat(readId.get()).isEqualTo("raw-event-id");
        assertThat(deletedId.get()).isEqualTo("raw-event-id");
    }

    @Test
    void rejectsInvalidListRangeBeforeCallingAdapter() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-04-26T10:00:00+02:00");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        assertThatThrownBy(() -> service(new StubCalendarAdapter()).list(timestamp, timestamp))
                .isInstanceOf(ApiErrorException.class)
                .extracting("code")
                .isEqualTo("validation-error");
    }

    @Test
    void delegatesReadToAdapterWithWorkspaceScope() {
        AtomicReference<CalendarPrincipal> capturedPrincipal = new AtomicReference<>();
        CalendarEventResponse event = new CalendarEventResponse(
                "event-id",
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false,
                "etag");
        CalendarAdapter adapter = new StubCalendarAdapter() {
            @Override
            public CalendarEventResponse read(CalendarPrincipal principal, String id) {
                capturedPrincipal.set(principal);
                assertThat(id).isEqualTo("event-id");
                return event;
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        var response = service(adapter).read("event-id");

        assertThat(response.scope().type()).isEqualTo("workspace");
        assertThat(response.title()).isEqualTo("Planning");
        assertThat(capturedPrincipal.get().subject()).isEqualTo("user-123");
        assertThat(capturedPrincipal.get().nextcloudUserId()).isEqualTo("massimo");
    }

    @Test
    void mapsReadNotFoundToStableApiError() {
        CalendarAdapter adapter = new StubCalendarAdapter() {
            @Override
            public CalendarEventResponse read(CalendarPrincipal principal, String id) {
                throw new CalendarAdapterException(CalendarAdapterException.Type.NOT_FOUND, "missing");
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        assertThatThrownBy(() -> service(adapter).read("missing-event"))
                .isInstanceOf(ApiErrorException.class)
                .satisfies(error -> {
                    ApiErrorException apiError = (ApiErrorException) error;
                    assertThat(apiError.status().value()).isEqualTo(404);
                    assertThat(apiError.code()).isEqualTo("calendar-event-not-found");
                    assertThat(apiError.details()).containsEntry("module", "calendar");
                    assertThat(apiError.details()).containsEntry("operation", "read-event");
                });
    }

    @Test
    void mapsAdapterConflictToStableApiError() {
        CalendarAdapter adapter = new StubCalendarAdapter() {
            @Override
            public CalendarEventResponse create(CalendarPrincipal principal, CreateCalendarEventRequest request) {
                throw new CalendarAdapterException(CalendarAdapterException.Type.CONFLICT, "conflict");
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false);

        assertThatThrownBy(() -> service(adapter).create(request))
                .isInstanceOf(ApiErrorException.class)
                .satisfies(error -> {
                    ApiErrorException apiError = (ApiErrorException) error;
                    assertThat(apiError.status().value()).isEqualTo(409);
                    assertThat(apiError.code()).isEqualTo("calendar-event-conflict");
                    assertThat(apiError.details()).containsEntry("module", "calendar");
                    assertThat(apiError.details()).containsEntry("operation", "create-event");
                });
    }

    @Test
    void listFailsClosedWhenContextAuthorizationDeniesScopeAccess() {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-04-26T10:00:00+02:00");
        OffsetDateTime endsAt = OffsetDateTime.parse("2026-04-26T11:00:00+02:00");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));

        assertThatThrownBy(() -> service(
                        new StubCalendarAdapter(),
                        request -> ContextAuthorizationDecision.deny("no matching context membership"))
                        .list(startsAt.minusDays(1), endsAt.plusDays(1), "channel", "engineering", "engineering-general"))
                .isInstanceOf(ApiErrorException.class)
                .satisfies(error -> {
                    ApiErrorException apiError = (ApiErrorException) error;
                    assertThat(apiError.status().value()).isEqualTo(403);
                    assertThat(apiError.code()).isEqualTo("calendar-forbidden");
                    assertThat(apiError.details()).containsEntry("module", "calendar");
                    assertThat(apiError.details()).containsEntry("operation", "list-events");
                    assertThat(apiError.details()).containsEntry("contextId", "channel-engineering-general");
                    assertThat(apiError.details()).containsEntry("permission", "view");
                    assertThat(apiError.details()).containsEntry("reason", "no matching context membership");
                });
    }

    @Test
    void createRequiresEditPermissionForTeamContextBeforeCallingAdapter() {
        AtomicReference<ContextAuthorizationRequest> captured = new AtomicReference<>();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false,
                CalendarScopeResponse.team("engineering", "Engineering team calendar"));

        assertThatThrownBy(() -> service(
                        new StubCalendarAdapter(),
                        authzRequest -> {
                            captured.set(authzRequest);
                            return ContextAuthorizationDecision.deny("edit denied");
                        })
                        .create(request))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.details()).containsEntry("permission", "edit");
                });
        assertThat(captured.get().tenantId()).isEqualTo("tenant-default");
        assertThat(captured.get().contextId()).isEqualTo("team-engineering");
        assertThat(captured.get().principalRef()).isEqualTo("user:user-123");
        assertThat(captured.get().permission()).isEqualTo(ContextPermission.EDIT);
    }

    private CalendarFacadeService service(CalendarAdapter adapter) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"));
    }

    private CalendarFacadeService service(CalendarAdapter adapter, ContextAuthorizationPort contextAuthorizationPort) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("calendarAdapter", adapter);
        return new CalendarFacadeService(beanFactory.getBeanProvider(CalendarAdapter.class), contextAuthorizationPort);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("preferred_username", "massimo")
                .build();
    }

    private static class StubCalendarAdapter implements CalendarAdapter {
        @Override
        public List<CalendarEventResponse> list(CalendarPrincipal principal, OffsetDateTime from, OffsetDateTime to) {
            throw new AssertionError("unexpected list call");
        }

        @Override
        public CalendarEventResponse create(CalendarPrincipal principal, CreateCalendarEventRequest request) {
            throw new AssertionError("unexpected create call");
        }

        @Override
        public CalendarEventResponse read(CalendarPrincipal principal, String id) {
            throw new AssertionError("unexpected read call");
        }

        @Override
        public CalendarEventResponse update(com.massimotter.weave.backend.service.calendar.CalendarPrincipal principal,
                String id, com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest request) {
            throw new AssertionError("unexpected update call");
        }

        @Override
        public void delete(CalendarPrincipal principal, String id) {
            throw new AssertionError("unexpected delete call");
        }
    }
}
