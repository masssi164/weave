package com.massimotter.weave.backend.service.spaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionRequest;
import com.massimotter.weave.backend.model.spaces.SpaceMemberChangeRequest;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class SpaceAdminApiServiceTest {
    private final DeploymentOrganizationAdmission admission = mock(DeploymentOrganizationAdmission.class);
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final SpaceProvisioningPort spaces = mock(SpaceProvisioningPort.class);
    private final SpaceMembershipAdministrationPort memberships = mock(SpaceMembershipAdministrationPort.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private final SpaceAdminApiService service = new SpaceAdminApiService(admission,
            OrganizationIdentityContextResolver.configured(
                    new ContextAuthorizationProperties(null, null, null, null, null, null, null, null)),
            capabilities, spaces, memberships, audit);
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String OWNER_ACCOUNT = IdentityReferences.accountId(ISSUER, "owner");
    private static final String MEMBER_ACCOUNT = IdentityReferences.accountId(ISSUER, "member");

    @Test
    void ownerProvisionRequiresCurrentPolicyAndPublishesSupportSafeAudit() {
        Jwt owner = token("owner", "owner");
        when(admission.allows(owner)).thenReturn(true);
        when(spaces.provision("tenant-default", "workspace-default", OWNER_ACCOUNT,
                Set.of(MEMBER_ACCOUNT))).thenReturn(SpaceProvisioningPort.Result.CREATED);
        var response = service.provision(owner,
                new SpaceProvisionRequest("workspace-default", List.of(MEMBER_ACCOUNT)));
        assertThat(response.spaceRef()).isEqualTo("workspace-default");
        assertThat(response.result()).isEqualTo("CREATED");
        verify(capabilities).requireCapability(owner, "admin.policy.edit", "spaces", "provision");
        verify(audit).publish(org.mockito.ArgumentMatchers.argThat((AuditEvent event) ->
                event.tenantId().equals("tenant-default")
                        && event.contextId().equals("workspace-default")
                        && event.actorRef().equals(OWNER_ACCOUNT)
                        && !event.payload().toString().contains(MEMBER_ACCOUNT)));
    }

    @Test
    void memberForeignOrganizationAndInvalidAccountsNeverProvision() {
        Jwt member = token("member", "member");
        when(admission.allows(member)).thenReturn(true);
        assertThatThrownBy(() -> service.provision(member,
                new SpaceProvisionRequest("workspace-default", List.of())))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        Jwt owner = token("owner", "owner");
        assertThatThrownBy(() -> service.provision(owner,
                new SpaceProvisionRequest("workspace-default", List.of())))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        when(admission.allows(owner)).thenReturn(true);
        assertThatThrownBy(() -> service.provision(owner,
                new SpaceProvisionRequest("workspace-default", List.of("user:bare-subject"))))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(spaces, audit);
    }

    @Test
    void changedRequestConflictsWithoutAuditOrMembershipOverwrite() {
        Jwt owner = token("owner", "owner");
        when(admission.allows(owner)).thenReturn(true);
        when(spaces.provision("tenant-default", "workspace-default", OWNER_ACCOUNT, Set.of()))
                .thenThrow(new SpaceProvisioningPort.Conflict());
        assertThatThrownBy(() -> service.provision(owner,
                new SpaceProvisionRequest("workspace-default", List.of())))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.code()).isEqualTo("space-provision-conflict");
                });
        verifyNoInteractions(audit);
    }

    @Test
    void versionedMembershipChangeRequiresCurrentAdminAndNeverAuditsAStaleMutation() {
        Jwt owner = token("owner", "owner");
        when(admission.allows(owner)).thenReturn(true);
        var request = new SpaceMemberChangeRequest(List.of(
                SpaceMemberChangeRequest.PermissionValue.VIEW));
        assertThatThrownBy(() -> service.grant(owner, "workspace-default", MEMBER_ACCOUNT,
                request, null, null)).isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED));
        when(memberships.grant("tenant-default", "workspace-default", OWNER_ACCOUNT,
                MEMBER_ACCOUNT, Set.of(Permission.VIEW), null, true))
                .thenReturn(new SpaceMembershipAdministrationPort.MemberState(
                        MEMBER_ACCOUNT, Set.of(Permission.VIEW), "\"sm-0\""));
        var created = service.grant(owner, "workspace-default", MEMBER_ACCOUNT,
                request, null, "*");
        assertThat(created.strongEtag()).isEqualTo("\"sm-0\"");
        when(memberships.grant("tenant-default", "workspace-default", OWNER_ACCOUNT,
                MEMBER_ACCOUNT, Set.of(Permission.VIEW), "\"sm-0\"", false))
                .thenThrow(new SpaceMembershipAdministrationPort.Stale());
        assertThatThrownBy(() -> service.grant(owner, "workspace-default", MEMBER_ACCOUNT,
                request, "\"sm-0\"", null))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED));
        verify(audit, times(1)).publish(any());
        verify(capabilities, times(3)).requireCapability(owner,
                "admin.policy.edit", "spaces", "grant");
    }

    @Test
    void memberReadbackIsBoundedToTheCurrentOrganizationAndUsesAStableCursor() {
        Jwt owner = token("owner", "owner");
        when(admission.allows(owner)).thenReturn(true);
        var ownerState = new SpaceMembershipAdministrationPort.MemberState(
                OWNER_ACCOUNT, Set.of(Permission.VIEW, Permission.EDIT, Permission.ADMIN), "\"sm-0\"");
        var memberState = new SpaceMembershipAdministrationPort.MemberState(
                MEMBER_ACCOUNT, Set.of(Permission.VIEW), "\"sm-1\"");
        when(memberships.list("tenant-default", "workspace-default", OWNER_ACCOUNT, "", 2))
                .thenReturn(List.of(ownerState, memberState));
        var first = service.listMembers(owner, "workspace-default", null, 1);
        assertThat(first.members()).hasSize(1);
        assertThat(first.nextCursor()).isEqualTo(OWNER_ACCOUNT);
        assertThat(first.members().getFirst().permissions()).containsExactly(
                SpaceMemberChangeRequest.PermissionValue.VIEW,
                SpaceMemberChangeRequest.PermissionValue.EDIT,
                SpaceMemberChangeRequest.PermissionValue.ADMIN);
        when(memberships.get("tenant-default", "workspace-default", OWNER_ACCOUNT, MEMBER_ACCOUNT))
                .thenReturn(memberState);
        assertThat(service.getMember(owner, "workspace-default", MEMBER_ACCOUNT).strongEtag())
                .isEqualTo("\"sm-1\"");
        assertThatThrownBy(() -> service.listMembers(owner, "workspace-default", null, 101))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Jwt token(String subject, String role) {
        return Jwt.withTokenValue(subject).header("alg", "none")
                .subject(subject).issuer(ISSUER)
                .claim("organization", HumanJwtTestSupport.organizationWithRole(role))
                .build();
    }
}
