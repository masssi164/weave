package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChangeSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.security.device.InMemoryDeviceCredentialRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
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
    void delegatesListWithCanonicalCalendarIdentityAndTimeWindow() {
        AtomicReference<CalendarId> capturedCalendar = new AtomicReference<>();
        AtomicReference<Instant> capturedFrom = new AtomicReference<>();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public List<CalendarEvent> query(CalendarId calendarId, CalendarScope scope, Instant from, Instant to) {
                capturedCalendar.set(calendarId);
                capturedFrom.set(from);
                return List.of(event("event-id", scope));
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
        OffsetDateTime from = OffsetDateTime.parse("2026-04-25T10:00:00+02:00");

        var response = service(adapter).list(from, OffsetDateTime.parse("2026-04-27T11:00:00+02:00"));

        assertThat(response.scope().type()).isEqualTo("workspace");
        assertThat(response.events()).singleElement().satisfies(event -> {
            assertThat(event.id()).isEqualTo("event-id");
            assertThat(event.title()).isEqualTo("Planning");
        });
        assertThat(capturedCalendar.get().value()).isEqualTo("massimo");
        assertThat(capturedFrom.get()).isEqualTo(from.toInstant());
    }

    @Test
    void exposesWorkspaceTeamAndChannelCalendarScopes() {
        authenticate();

        var response = service(new StubCalendarProvider()).scopes();

        assertThat(response.scopes()).extracting(CalendarScopeResponse::type)
                .containsExactly("workspace", "team", "channel");
        assertThat(response.scopes().get(0).contextId()).isEqualTo("workspace-default");
        assertThat(response.scopes().get(1).contextId()).isEqualTo("team-engineering");
        assertThat(response.scopes().get(2).contextId()).isEqualTo("channel-engineering-general");
        assertThat(response.scopes().get(2).capabilities()).contains("read", "create", "edit", "delete");
    }

    @Test
    void listReturnsChannelScopedEventsWithoutProviderReferences() {
        AtomicReference<CalendarScope> capturedScope = new AtomicReference<>();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public List<CalendarEvent> query(CalendarId calendarId, CalendarScope scope, Instant from, Instant to) {
                capturedScope.set(scope);
                return List.of(event("raw-event-id", scope));
            }
        };
        authenticate();

        var response = service(adapter).list(
                OffsetDateTime.parse("2026-04-25T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-27T11:00:00+02:00"),
                "channel",
                "engineering",
                "engineering-general");

        assertThat(capturedScope.get())
                .isEqualTo(new CalendarScope(ScopeType.CHANNEL, "engineering", "engineering-general"));
        assertThat(response.scope().contextId()).isEqualTo("channel-engineering-general");
        assertThat(response.events()).singleElement().satisfies(event -> {
            assertThat(event.id()).isEqualTo("raw-event-id");
            assertThat(event.scope().type()).isEqualTo("channel");
            assertThat(event.threadRef().contextId()).isEqualTo("channel-engineering-general");
            assertThat(event.threadRef().matrixRoomId()).isNull();
            assertThat(event.providerRef().rawProviderPathExposed()).isFalse();
        });
    }

    @Test
    void createReadAndDeletePreserveTeamScopeFacadeIds() {
        AtomicReference<EventId> readId = new AtomicReference<>();
        AtomicReference<EventId> deletedId = new AtomicReference<>();
        AtomicReference<CalendarScope> writeScope = new AtomicReference<>();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent write(CalendarWrite write) {
                writeScope.set(write.event().scope());
                return event("raw-event-id", write.event().scope());
            }

            @Override
            public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
                readId.set(id);
                return event(id.value(), scope);
            }

            @Override
            public void delete(CalendarId calendarId, CalendarScope scope, EventId id, EventVersion expectedVersion) {
                deletedId.set(id);
            }
        };
        authenticate();

        var created = service(adapter).create(request(CalendarScopeResponse.team(
                "engineering", "Engineering team calendar")));
        var read = service(adapter).read(created.id());
        service(adapter).delete(created.id());

        assertThat(writeScope.get()).isEqualTo(new CalendarScope(ScopeType.TEAM, "engineering", null));
        assertThat(created.scope().type()).isEqualTo("team");
        assertThat(read.scope().teamId()).isEqualTo("engineering");
        assertThat(read.threadRef().meetingThreadId()).isEqualTo(created.threadRef().meetingThreadId());
        assertThat(readId.get().value()).isEqualTo("raw-event-id");
        assertThat(deletedId.get().value()).isEqualTo("raw-event-id");
    }

    @Test
    void createPassesNormalizedChannelScopeToCanonicalPort() {
        AtomicReference<CalendarScope> capturedScope = new AtomicReference<>();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent write(CalendarWrite write) {
                capturedScope.set(write.event().scope());
                return event("raw-event-id", write.event().scope());
            }
        };
        authenticate();
        CalendarScopeResponse partial = new CalendarScopeResponse(
                null, "channel", null, "workspace", null, "engineering", null, null, List.of());

        var created = service(adapter).create(request(partial));

        assertThat(capturedScope.get())
                .isEqualTo(new CalendarScope(ScopeType.CHANNEL, "engineering", "engineering-general"));
        assertThat(created.scope().contextId()).isEqualTo("channel-engineering-general");
    }

    @Test
    void rejectsInvalidListRangeBeforeCallingProvider() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-04-26T10:00:00+02:00");
        authenticate();

        assertThatThrownBy(() -> service(new StubCalendarProvider()).list(timestamp, timestamp))
                .isInstanceOf(ApiErrorException.class)
                .extracting("code")
                .isEqualTo("validation-error");
    }

    @Test
    void delegatesReadWithWorkspaceScopeAndCanonicalCalendarId() {
        AtomicReference<CalendarId> capturedCalendar = new AtomicReference<>();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
                capturedCalendar.set(calendarId);
                assertThat(scope).isEqualTo(CalendarScope.workspace());
                assertThat(id.value()).isEqualTo("event-id");
                return event("event-id", scope);
            }
        };
        authenticate();

        var response = service(adapter).read("event-id");

        assertThat(response.title()).isEqualTo("Planning");
        assertThat(capturedCalendar.get().value()).isEqualTo("massimo");
    }

    @Test
    void mapsProviderNotFoundToStableApiError() {
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
                throw new CalendarAdapterException(CalendarAdapterException.Type.NOT_FOUND, "missing");
            }
        };
        authenticate();

        assertThatThrownBy(() -> service(adapter).read("missing-event"))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(404);
                    assertThat(error.code()).isEqualTo("calendar-event-not-found");
                    assertThat(error.details()).containsEntry("operation", "read-event");
                });
    }

    @Test
    void mapsProviderConflictToStableApiError() {
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent write(CalendarWrite write) {
                throw new CalendarAdapterException(CalendarAdapterException.Type.CONFLICT, "conflict");
            }
        };
        authenticate();

        assertThatThrownBy(() -> service(adapter).create(request(CalendarScopeResponse.workspace())))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(409);
                    assertThat(error.code()).isEqualTo("calendar-event-conflict");
                    assertThat(error.details()).containsEntry("operation", "create-event");
                });
    }

    @Test
    void calDavUpdateValidatesNorthboundEtagAndUsesExactProviderVersion() {
        AtomicReference<EventVersion> capturedVersion = new AtomicReference<>();
        EventVersion providerVersion = new EventVersion("\"provider-current\"");
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
                return event(id.value(), scope, providerVersion);
            }

            @Override
            public CalendarEvent write(CalendarWrite write) {
                capturedVersion.set(write.expectedVersion());
                return event(write.event().id().value(), write.event().scope(), new EventVersion("\"provider-next\""));
            }
        };
        authenticate();

        var response = service(adapter).putCalDavEventIcs(
                "planning",
                calendarData("planning", "Updated planning"),
                "W/\"provider-current\"",
                null,
                CalendarScopeResponse.workspace());

        assertThat(capturedVersion.get()).isSameAs(providerVersion);
        assertThat(response.etag()).isEqualTo("\"provider-next\"");
    }

    @Test
    void calDavUpdateRejectsStaleNorthboundEtagBeforeProviderWrite() {
        AtomicBoolean providerWriteCalled = new AtomicBoolean();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
                return event(id.value(), scope, new EventVersion("\"provider-current\""));
            }

            @Override
            public CalendarEvent write(CalendarWrite write) {
                providerWriteCalled.set(true);
                return write.event();
            }
        };
        authenticate();

        assertThatThrownBy(() -> service(adapter).putCalDavEventIcs(
                        "planning",
                        calendarData("planning", "Updated planning"),
                        "\"provider-stale\"",
                        null,
                        CalendarScopeResponse.workspace()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.PRECONDITION_FAILED);
                    assertThat(error.code()).isEqualTo("caldav-precondition-failed");
                    assertThat(error.getMessage()).doesNotContain("provider-current");
                    assertThat(error.details())
                            .containsEntry("supportSafe", true)
                            .containsEntry("providerDataPlaneExposed", false)
                            .containsEntry("diagnosticsRedacted", true);
                });
        assertThat(providerWriteCalled).isFalse();
    }

    @Test
    void calDavUpdateMapsInvalidTimeRangeToSupportSafeValidationError() {
        authenticate();

        assertThatThrownBy(() -> service(new StubCalendarProvider()).putCalDavEventIcs(
                        "planning",
                        """
                                BEGIN:VCALENDAR
                                VERSION:2.0
                                BEGIN:VEVENT
                                UID:planning
                                DTSTART;TZID=UTC:20260708T120000
                                DTEND;TZID=UTC:20260708T120000
                                SUMMARY:Invalid planning
                                END:VEVENT
                                END:VCALENDAR
                                """,
                        null,
                        null,
                        CalendarScopeResponse.workspace()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(error.code()).isEqualTo("calendar-ics-invalid");
                    assertThat(error.getMessage()).isEqualTo("Calendar data is not a supported iCalendar VEVENT.");
                    assertThat(error.details())
                            .containsEntry("reason", "invalid-event-data")
                            .containsEntry("supportSafe", true)
                            .containsEntry("providerDataPlaneExposed", false);
                });
    }

    @Test
    void listFailsClosedWhenContextAuthorizationDeniesScopeAccess() {
        authenticate();

        assertThatThrownBy(() -> service(
                        new StubCalendarProvider(),
                        request -> ContextAuthorizationDecision.deny("no matching context membership"))
                .list(
                        OffsetDateTime.parse("2026-04-25T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-04-27T11:00:00+02:00"),
                        "channel",
                        "engineering",
                        "engineering-general"))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo("calendar-forbidden");
                    assertThat(error.details())
                            .containsEntry("contextId", "channel-engineering-general")
                            .containsEntry("permission", "view")
                            .containsEntry("reason", "no matching context membership");
                });
    }

    @Test
    void createRequiresEditPermissionBeforeCallingProvider() {
        AtomicReference<ContextAuthorizationRequest> captured = new AtomicReference<>();
        authenticate();

        assertThatThrownBy(() -> service(
                        new StubCalendarProvider(),
                        request -> {
                            captured.set(request);
                            return ContextAuthorizationDecision.deny("edit denied");
                        })
                .create(request(CalendarScopeResponse.team("engineering", "Engineering team calendar"))))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.details()).containsEntry("permission", "edit");
                });
        assertThat(captured.get().tenantId()).isEqualTo("tenant-default");
        assertThat(captured.get().contextId()).isEqualTo("team-engineering");
        assertThat(captured.get().principalRef()).isEqualTo("user:massimo");
        assertThat(captured.get().permission()).isEqualTo(ContextPermission.EDIT);
    }

    @Test
    void memberJwtWithoutCalendarEditorCapabilityIsDeniedBeforeRebacAndProviderWrite() {
        AtomicBoolean contextChecked = new AtomicBoolean();
        AtomicBoolean providerCalled = new AtomicBoolean();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarEvent write(CalendarWrite write) {
                providerCalled.set(true);
                return event("unexpected", write.event().scope());
            }
        };
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                memberJwtWithoutCalendarManagement(), null));

        assertThatThrownBy(() -> service(adapter, request -> {
                    contextChecked.set(true);
                    return ContextAuthorizationDecision.allow("would allow ReBAC");
                }).create(request(CalendarScopeResponse.workspace())))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                    assertThat(error.code()).isEqualTo("capability-policy-blocked");
                    assertThat(error.getMessage())
                            .doesNotContain("massimo")
                            .doesNotContain("Keycloak")
                            .doesNotContain("CalDAV provider");
                    assertThat(error.details())
                            .containsEntry("module", "calendar")
                            .containsEntry("operation", "create-event")
                            .containsEntry("requiredCapability", "calendar.manage_events")
                            .containsEntry("diagnosticsRedacted", true);
                });
        assertThat(contextChecked).isFalse();
        assertThat(providerCalled).isFalse();
    }

    @Test
    void syncCollectionWrapsProviderTokensAndScopesCursorsToOneCalendar() {
        AtomicReference<String> capturedProviderToken = new AtomicReference<>();
        CalendarProviderPort adapter = new StubCalendarProvider() {
            @Override
            public CalendarChangeSet changes(CalendarId calendarId, CalendarScope scope, String sinceToken) {
                capturedProviderToken.set(sinceToken);
                String next = sinceToken == null ? "raw-provider-token-1" : "raw-provider-token-2";
                return new CalendarChangeSet(next, List.of(new CalendarChange(
                        next,
                        new EventId("event-id"),
                        false,
                        new EventVersion("\"etag\""))));
            }

            @Override
            public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
                return event(id.value(), scope);
            }
        };
        authenticate();
        CalendarFacadeService service = service(adapter);

        var first = service.syncCalDavResources(CalendarScopeResponse.workspace(), null);
        var second = service.syncCalDavResources(CalendarScopeResponse.workspace(), first.syncToken());

        assertThat(first.syncToken()).startsWith("weave-caldav-sync-").doesNotContain("raw-provider");
        assertThat(second.syncToken()).startsWith("weave-caldav-sync-").isNotEqualTo(first.syncToken());
        assertThat(capturedProviderToken.get()).isEqualTo("raw-provider-token-1");
        assertThat(first.changedResources()).singleElement()
                .satisfies(resource -> assertThat(resource.eventId()).isEqualTo("event-id"));
        assertThatThrownBy(() -> service.syncCalDavResources(
                        CalendarScopeResponse.team("engineering", "Engineering"),
                        first.syncToken()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(409);
                    assertThat(error.code()).isEqualTo("caldav-sync-token-invalid");
                });
    }

    private CalendarFacadeService service(CalendarProviderPort adapter) {
        return service(adapter, request -> ContextAuthorizationDecision.allow("test allow"));
    }

    private CalendarFacadeService service(
            CalendarProviderPort adapter,
            ContextAuthorizationPort contextAuthorizationPort) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("calendarProviderPort", adapter);
        return new CalendarFacadeService(
                beanFactory.getBeanProvider(CalendarProviderPort.class),
                "https://files.weave.test",
                contextAuthorizationPort,
                contextAuthorizationProperties(),
                new DeviceCredentialService(new InMemoryDeviceCredentialRepository()),
                workspaceCapabilityService());
    }

    private CreateCalendarEventRequest request(CalendarScopeResponse scope) {
        return new CreateCalendarEventRequest(
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false,
                scope);
    }

    private CalendarEvent event(String id, CalendarScope scope) {
        return event(id, scope, new EventVersion("\"etag\""));
    }

    private CalendarEvent event(String id, CalendarScope scope, EventVersion version) {
        return new CalendarEvent(
                new CalendarId("massimo"),
                new EventId(id),
                scope,
                "Planning",
                null,
                LocalDateTime.parse("2026-04-26T10:00:00"),
                LocalDateTime.parse("2026-04-26T11:00:00"),
                ZoneId.of("Europe/Berlin"),
                false,
                null,
                List.of(),
                null,
                version,
                null);
    }

    private String calendarData(String id, String title) {
        return """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:%s
                DTSTART:20260708T120000Z
                DTEND:20260708T130000Z
                SUMMARY:%s
                END:VEVENT
                END:VCALENDAR
                """.formatted(id, title);
    }

    private ContextAuthorizationProperties contextAuthorizationProperties() {
        return new ContextAuthorizationProperties(
                "weave_tenant_id",
                "tenant_id",
                "tenant-default",
                "preferred_username",
                "user:",
                List.of(),
                List.of(),
                List.of());
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt(), null));
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject("user-123")
                .claim("preferred_username", "massimo")
                .claim("weave_tenant_id", "tenant-default")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin"))))
                .claim("groups", List.of())
                .build();
    }

    private Jwt memberJwtWithoutCalendarManagement() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject("user-123")
                .claim("preferred_username", "massimo")
                .claim("weave_tenant_id", "tenant-default")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                .claim("groups", List.of())
                .build();
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties resourceServer = new OAuth2ResourceServerProperties();
        resourceServer.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return new WorkspaceCapabilityService(
                resourceServer,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }

    private static class StubCalendarProvider implements CalendarProviderPort {
        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public ProviderReadiness readiness() {
            return ProviderReadiness.ready("calendar-storage-ready");
        }

        @Override
        public ProviderConformanceProfile conformanceProfile() {
            return new ProviderConformanceProfile(
                    "calendar", "test", Set.of(), Map.of(), true, true, true);
        }

        @Override
        public List<CalendarEvent> query(CalendarId calendarId, CalendarScope scope, Instant from, Instant to) {
            throw new AssertionError("unexpected query call");
        }

        @Override
        public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
            throw new AssertionError("unexpected read call");
        }

        @Override
        public CalendarEvent write(CalendarWrite write) {
            throw new AssertionError("unexpected write call");
        }

        @Override
        public void delete(CalendarId calendarId, CalendarScope scope, EventId id, EventVersion expectedVersion) {
            throw new AssertionError("unexpected delete call");
        }

        @Override
        public List<FreeBusyWindow> freeBusy(CalendarId calendarId, CalendarScope scope, Instant from, Instant to) {
            throw new AssertionError("unexpected free-busy call");
        }

        @Override
        public CalendarChangeSet changes(CalendarId calendarId, CalendarScope scope, String sinceToken) {
            throw new AssertionError("unexpected changes call");
        }
    }
}
