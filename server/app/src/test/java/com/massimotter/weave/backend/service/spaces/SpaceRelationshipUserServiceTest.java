package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.spaces.application.SpaceRelationshipUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.calendar.spaces.CalendarSpaceRelationshipContribution;
import com.massimotter.weave.backend.files.spaces.FilesSpaceRelationshipContribution;
import com.massimotter.weave.backend.chat.spaces.ChatSpaceRelationshipContribution;
import com.massimotter.weave.backend.chat.application.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.chat.provider.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.files.application.FilesUserApiService;
import com.massimotter.weave.backend.calendar.application.CalendarUserApiService;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class SpaceRelationshipUserServiceTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String ACCOUNT = IdentityReferences.accountId(ISSUER, "member");
    private final SpaceAccessPort spaces = mock(SpaceAccessPort.class);
    private final DeploymentOrganizationAdmission admission = mock(DeploymentOrganizationAdmission.class);
    private final FilesUserResourceRepository records = mock(FilesUserResourceRepository.class);
    private final FilesUserApiService files = mock(FilesUserApiService.class);
    private final CalendarUserApiService calendar = mock(CalendarUserApiService.class);
    private final ChatDomainFacadeService chat = mock(ChatDomainFacadeService.class);
    private final MatrixProtocolCoreService matrix = mock(MatrixProtocolCoreService.class);
    private final ContextAuthorizationProperties context =
            new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    private final SpaceRelationshipUserService service = new SpaceRelationshipUserService(
            OrganizationIdentityContextResolver.configured(context), admission,
            context, spaces, List.of(new CalendarSpaceRelationshipContribution(calendar),
                    new FilesSpaceRelationshipContribution(records, files), new ChatSpaceRelationshipContribution(chat, matrix)));
    private final Jwt member = Jwt.withTokenValue("member").header("alg", "none")
            .issuer(ISSUER).subject("member")
            .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
            .build();

    @BeforeEach
    void admitMember() {
        when(admission.allows(member)).thenReturn(true);
        when(calendar.materializedEventRefsInSpace(eq(member), anyString(), anyString(), eq(100)))
                .thenReturn(List.of());
        when(chat.joinedConversationRefsInSpace(eq(member), anyString(), anyString(), eq(100)))
                .thenReturn(List.of());
        when(files.inspect(eq(member), anyString())).thenAnswer(call ->
                new com.massimotter.weave.backend.files.api.FilesUserItemResponse(
                        call.getArgument(1), "file:root", "stable.txt", "/stable.txt",
                        "file", 0, null, Instant.EPOCH, "revision", List.of("inspect")));
    }

    @Test
    void showsOnlyConfirmedMaterializedOwnerFileWithStableRelationIdentity() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 25))
                .thenReturn(List.of(resource("file:stable")));
        var result = service.list(member, "workspace-default", null, 25);
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().getFirst().relationRef()).isEqualTo("relation:file:file:stable");
        assertThat(result.relationships().getFirst().targetRef()).isEqualTo("file:stable");
        assertThat(result.relationships().getFirst().targetKind()).isEqualTo("FILE");
        verify(files).inspect(member, "file:stable");
    }

    @Test
    void filesUseTheConfiguredContextPrincipalRatherThanTheImmutableAccountSubject() {
        var preferredContext = new ContextAuthorizationProperties(null, null, null,
                "preferred_username", null, null, null, null);
        var projection = new SpaceRelationshipUserService(
                OrganizationIdentityContextResolver.configured(preferredContext), admission,
                preferredContext, spaces, List.of(new CalendarSpaceRelationshipContribution(calendar),
                        new FilesSpaceRelationshipContribution(records, files), new ChatSpaceRelationshipContribution(chat, matrix)));
        Jwt session = Jwt.withTokenValue("member-preferred").header("alg", "none")
                .issuer(ISSUER).subject("stable-keycloak-subject")
                .claim("preferred_username", "member")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                .build();
        String account = IdentityReferences.accountId(ISSUER, "stable-keycloak-subject");
        when(admission.allows(session)).thenReturn(true);
        when(spaces.allows("tenant-default", "workspace-default", account, Permission.VIEW))
                .thenReturn(true);
        when(calendar.materializedEventRefsInSpace(session, "workspace-default", "", 100))
                .thenReturn(List.of());
        when(chat.joinedConversationRefsInSpace(session, "workspace-default", "", 100))
                .thenReturn(List.of());
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 25))
                .thenReturn(List.of(resource("file:stable")));
        when(files.inspect(session, "file:stable")).thenReturn(
                new com.massimotter.weave.backend.files.api.FilesUserItemResponse(
                        "file:stable", "file:root", "stable.txt", "/stable.txt",
                        "file", 0, null, Instant.EPOCH, "revision", List.of("inspect")));

        var result = projection.list(session, "workspace-default", null, 25);
        assertThat(result.relationships()).extracting(value -> value.targetRef())
                .containsExactly("file:stable");
        verify(records).activeInSpace("tenant-default", "workspace-default", "user:member", "", 25);
        verify(records, never()).activeInSpace(eq("tenant-default"), eq("workspace-default"),
                eq("user:stable-keycloak-subject"), anyString(), anyInt());
    }

    @Test
    void revokedSpaceAndProviderRightsUncertaintyFailClosed() {
        assertThatThrownBy(() -> service.list(member, "workspace-default", null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(records, files);

        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 25))
                .thenReturn(List.of(resource("file:stale")));
        when(files.inspect(member, "file:stale")).thenThrow(new ApiErrorException(
                HttpStatus.NOT_FOUND, "file-not-found", "unavailable", Map.of()));
        assertThatThrownBy(() -> service.list(member, "workspace-default", null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.code()).isEqualTo("space-resource-check-unavailable");
                });
    }

    @Test
    void deploymentOrganizationAdmissionPrecedesSpaceAndResourceLookup() {
        when(admission.allows(member)).thenReturn(false);
        assertThatThrownBy(() -> service.list(member, "workspace-default", null, 25))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(spaces, records, files);
    }

    @Test
    void deniedFileCapabilityPublishesNoResourceOrCursor() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 1))
                .thenReturn(List.of(resource("file:private")));
        when(files.inspect(member, "file:private")).thenThrow(new ApiErrorException(
                HttpStatus.FORBIDDEN, "files-forbidden", "denied", Map.of()));
        var result = service.list(member, "workspace-default", null, 1);
        assertThat(result.relationships()).isEmpty();
        assertThat(result.nextAfterRelationRef()).isNull();
    }

    @Test
    void materializedEventPrecedesFileAndUsesCurrentCalendarReadback() {
        String eventId = "event:" + "a".repeat(64);
        String eventRelation = "relation:event:" + eventId;
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(calendar.materializedEventRefsInSpace(member, "workspace-default", "", 100))
                .thenReturn(List.of(eventId));
        var checkedEvent = mock(com.massimotter.weave.backend.calendar.api.CalendarUserModels.Event.class);
        when(checkedEvent.id()).thenReturn(eventId);
        when(calendar.readMaterializedEventInSpace(member, "workspace-default", eventId))
                .thenReturn(checkedEvent);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 1))
                .thenReturn(List.of(resource("file:stable")));
        var first = service.list(member, "workspace-default", null, 1);
        assertThat(first.relationships()).extracting(value -> value.targetKind())
                .containsExactly("EVENT");
        assertThat(first.nextAfterRelationRef()).isEqualTo(eventRelation);
        verify(calendar).readMaterializedEventInSpace(member, "workspace-default", eventId);
        verifyNoInteractions(records, files);

        var second = service.list(member, "workspace-default", eventRelation, 1);
        assertThat(second.relationships()).extracting(value -> value.targetKind())
                .containsExactly("FILE");
        assertThat(second.relationships().getFirst().targetRef()).isEqualTo("file:stable");
        verify(files).inspect(member, "file:stable");
    }

    @Test
    void staleCalendarMappingNeverPublishesAPhantomRelation() {
        String eventId = "event:" + "b".repeat(64);
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(calendar.materializedEventRefsInSpace(member, "workspace-default", "", 100))
                .thenReturn(List.of(eventId));
        when(calendar.readMaterializedEventInSpace(member, "workspace-default", eventId))
                .thenThrow(new ApiErrorException(HttpStatus.NOT_FOUND,
                        "calendar-event-not-found", "missing", Map.of()));
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 25))
                .thenReturn(List.of(resource("file:stable")));
        var result = service.list(member, "workspace-default", null, 25);
        assertThat(result.relationships()).extracting(value -> value.targetKind())
                .containsExactly("FILE");
        assertThat(result.relationships().getFirst().targetRef()).isEqualTo("file:stable");
    }

    @Test
    void joinedCanonicalRoomProjectsStableMatrixReferenceAfterCurrentReadback() {
        String conversationId = "canonical-room-1";
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(chat.joinedConversationRefsInSpace(member, "workspace-default", "", 100))
                .thenReturn(List.of(conversationId));
        var checked = mock(com.massimotter.weave.backend.chat.domain.ChatConversation.class);
        when(checked.conversationId()).thenReturn(conversationId);
        when(chat.conversationInSpace(member, "workspace-default", conversationId))
                .thenReturn(checked);
        when(matrix.roomId(conversationId)).thenReturn("!weave-room-1:weave.test");

        var result = service.list(member, "workspace-default", null, 25);
        assertThat(result.relationships()).singleElement().satisfies(relation -> {
            assertThat(relation.relationRef()).isEqualTo("relation:room:" + conversationId);
            assertThat(relation.targetKind()).isEqualTo("ROOM");
            assertThat(relation.targetRef()).isEqualTo("!weave-room-1:weave.test");
        });
        verify(matrix).roomId(conversationId);
    }

    @Test
    void revokedChatMembershipDoesNotPublishRoomOrRoomId() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(chat.joinedConversationRefsInSpace(member, "workspace-default", "", 100))
                .thenReturn(List.of("room-revoked"));
        when(chat.conversationInSpace(member, "workspace-default", "room-revoked"))
                .thenThrow(new ChatAccessDeniedException());

        var result = service.list(member, "workspace-default", null, 25);
        assertThat(result.relationships()).isEmpty();
        verifyNoInteractions(matrix);
    }

    @Test
    void roomCursorResumesWithoutReopeningEarlierResourceCategories() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW))
                .thenReturn(true);
        when(chat.joinedConversationRefsInSpace(member, "workspace-default", "room-a", 100))
                .thenReturn(List.of("room-b"));
        var checked = mock(com.massimotter.weave.backend.chat.domain.ChatConversation.class);
        when(checked.conversationId()).thenReturn("room-b");
        when(chat.conversationInSpace(member, "workspace-default", "room-b"))
                .thenReturn(checked);
        when(matrix.roomId("room-b")).thenReturn("!room-b:weave.test");

        var result = service.list(member, "workspace-default", "relation:room:room-a", 25);
        assertThat(result.relationships()).singleElement()
                .satisfies(relation -> assertThat(relation.targetRef())
                        .isEqualTo("!room-b:weave.test"));
        verifyNoInteractions(calendar, records, files);
    }

    @Test
    void filesOnlyRuntimePaginatesAndRejectsAnOmittedDomainCursor() {
        var reduced = new SpaceRelationshipUserService(OrganizationIdentityContextResolver.configured(context), admission,
                context, spaces, List.of(new FilesSpaceRelationshipContribution(records, files)));
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW)).thenReturn(true);
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "", 1))
                .thenReturn(List.of(resource("file:a")));
        when(records.activeInSpace("tenant-default", "workspace-default", "user:member", "file:a", 1))
                .thenReturn(List.of(resource("file:b")));
        var first = reduced.list(member, "workspace-default", null, 1);
        var second = reduced.list(member, "workspace-default", first.nextAfterRelationRef(), 1);
        assertThat(first.relationships()).extracting(value -> value.targetRef()).containsExactly("file:a");
        assertThat(second.relationships()).extracting(value -> value.targetRef()).containsExactly("file:b");
        assertThatThrownBy(() -> reduced.list(member, "workspace-default", "relation:room:missing-room", 1))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> assertThat(error.code()).isEqualTo("space-query-invalid"));
        verifyNoInteractions(calendar, chat, matrix);
    }

    @Test
    void missingMiddleDomainDoesNotInterruptEventToRoomPagination() {
        var reduced = new SpaceRelationshipUserService(OrganizationIdentityContextResolver.configured(context), admission,
                context, spaces, List.of(new ChatSpaceRelationshipContribution(chat, matrix), new CalendarSpaceRelationshipContribution(calendar)));
        String eventId = "event:" + "c".repeat(64);
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW)).thenReturn(true);
        when(calendar.materializedEventRefsInSpace(member, "workspace-default", "", 100)).thenReturn(List.of(eventId));
        var event = mock(com.massimotter.weave.backend.calendar.api.CalendarUserModels.Event.class);
        when(event.id()).thenReturn(eventId);
        when(calendar.readMaterializedEventInSpace(member, "workspace-default", eventId)).thenReturn(event);
        when(chat.joinedConversationRefsInSpace(member, "workspace-default", "", 100)).thenReturn(List.of("room-one"));
        var room = mock(com.massimotter.weave.backend.chat.domain.ChatConversation.class);
        when(room.conversationId()).thenReturn("room-one");
        when(chat.conversationInSpace(member, "workspace-default", "room-one")).thenReturn(room);
        when(matrix.roomId("room-one")).thenReturn("!room-one:weave.test");
        var first = reduced.list(member, "workspace-default", null, 1);
        var second = reduced.list(member, "workspace-default", first.nextAfterRelationRef(), 1);
        assertThat(first.relationships()).extracting(value -> value.targetKind()).containsExactly("EVENT");
        assertThat(second.relationships()).extracting(value -> value.targetKind()).containsExactly("ROOM");
        verifyNoInteractions(records, files);
    }

    @Test
    void noInstalledResourceDomainsReturnsAnEmptyPageAndRejectsOldCursors() {
        var reduced = new SpaceRelationshipUserService(OrganizationIdentityContextResolver.configured(context), admission,
                context, spaces, List.of());
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW)).thenReturn(true);
        assertThat(reduced.list(member, "workspace-default", null, 25).relationships()).isEmpty();
        assertThatThrownBy(() -> reduced.list(member, "workspace-default", "relation:file:file:a", 25))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(records, files, calendar, chat, matrix);
    }

    @Test
    void calendarDeniedReadbackScanRemainsBoundedToOneThousandCandidates() {
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW)).thenReturn(true);
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        when(calendar.materializedEventRefsInSpace(eq(member), eq("workspace-default"), anyString(), eq(100)))
                .thenAnswer(call -> java.util.stream.IntStream.range(0, 100)
                        .mapToObj(ignored -> "event:" + String.format("%064x", next.incrementAndGet())).toList());
        when(calendar.readMaterializedEventInSpace(eq(member), eq("workspace-default"), anyString()))
                .thenThrow(new ApiErrorException(HttpStatus.NOT_FOUND, "calendar-event-not-found", "missing", Map.of()));
        assertThatThrownBy(() -> service.list(member, "workspace-default", null, 1))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> assertThat(error.code()).isEqualTo("space-resource-check-unavailable"));
        verify(calendar, times(10)).materializedEventRefsInSpace(eq(member), eq("workspace-default"), anyString(), eq(100));
        verifyNoInteractions(records, files, chat, matrix);
    }

    @Test
    void duplicateDomainContributionsAreRejectedBeforeResourceLookup() {
        assertThatThrownBy(() -> new SpaceRelationshipUserService(OrganizationIdentityContextResolver.configured(context), admission,
                context, spaces, List.of(new FilesSpaceRelationshipContribution(records, files), new FilesSpaceRelationshipContribution(records, files))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(records, files, calendar, chat, matrix);
    }

    @Test
    void chatMembershipRecheckScanRemainsBoundedWithoutProjectingDeniedRoomIds() {
        var reduced = new SpaceRelationshipUserService(OrganizationIdentityContextResolver.configured(context), admission,
                context, spaces, List.of(new ChatSpaceRelationshipContribution(chat, matrix)));
        when(spaces.allows("tenant-default", "workspace-default", ACCOUNT, Permission.VIEW)).thenReturn(true);
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        when(chat.joinedConversationRefsInSpace(eq(member), eq("workspace-default"), anyString(), eq(100)))
                .thenAnswer(call -> java.util.stream.IntStream.range(0, 100)
                        .mapToObj(ignored -> "room-" + String.format("%04d", next.incrementAndGet())).toList());
        when(chat.conversationInSpace(eq(member), eq("workspace-default"), anyString())).thenThrow(new ChatAccessDeniedException());
        assertThatThrownBy(() -> reduced.list(member, "workspace-default", null, 1))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> assertThat(error.code()).isEqualTo("space-resource-check-unavailable"));
        verify(chat, times(10)).joinedConversationRefsInSpace(eq(member), eq("workspace-default"), anyString(), eq(100));
        verifyNoInteractions(matrix, calendar, records, files);
    }

    private static FilesUserResource resource(String fileId) {
        return new FilesUserResource("tenant-default", fileId, 1, "workspace-default",
                "file:root", "/stable.txt", Kind.FILE, "user:member",
                FilesUserResource.State.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }
}
