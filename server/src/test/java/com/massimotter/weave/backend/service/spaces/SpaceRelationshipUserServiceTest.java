package com.massimotter.weave.backend.service.spaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
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
            context, spaces, records, files, calendar, chat, matrix);
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
                new com.massimotter.weave.backend.model.files.FilesUserItemResponse(
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
                preferredContext, spaces, records, files, calendar, chat, matrix);
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
                new com.massimotter.weave.backend.model.files.FilesUserItemResponse(
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
        var checkedEvent = mock(com.massimotter.weave.backend.model.calendar.CalendarUserModels.Event.class);
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

    private static FilesUserResource resource(String fileId) {
        return new FilesUserResource("tenant-default", fileId, 1, "workspace-default",
                "file:root", "/stable.txt", Kind.FILE, "user:member",
                FilesUserResource.State.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }
}
