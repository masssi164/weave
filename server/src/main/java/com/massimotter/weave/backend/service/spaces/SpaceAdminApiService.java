package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.SpaceMemberRevokedException;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionRequest;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionResponse;
import com.massimotter.weave.backend.model.spaces.SpaceMemberChangeRequest;
import com.massimotter.weave.backend.model.spaces.SpaceMemberListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceMemberResponse;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort;
import java.time.Instant;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin setup against durable Space authority. No configuration seed becomes a runtime grant. */
@Service
public class SpaceAdminApiService {
    private final DeploymentOrganizationAdmission admission;
    private final OrganizationIdentityContextResolver identities;
    private final WorkspaceCapabilityService capabilities;
    private final SpaceProvisioningPort spaces;
    private final SpaceMembershipAdministrationPort memberships;
    private final AuditEventPublisher audit;

    public SpaceAdminApiService(DeploymentOrganizationAdmission admission,
            OrganizationIdentityContextResolver identities,
            WorkspaceCapabilityService capabilities, SpaceProvisioningPort spaces,
            SpaceMembershipAdministrationPort memberships, AuditEventPublisher audit) {
        this.admission = admission;
        this.identities = identities;
        this.capabilities = capabilities;
        this.spaces = spaces;
        this.memberships = memberships;
        this.audit = audit;
    }

    @Transactional
    public SpaceProvisionResponse provision(Jwt jwt, SpaceProvisionRequest request) {
        var identity = requireAdministrator(jwt, "provision");
        if (request == null || request.spaceRef() == null
                || !request.spaceRef().matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,254}")
                || request.memberAccountRefs().size() > 100
                || request.memberAccountRefs().stream().anyMatch(account ->
                        account == null || !account.matches("acct_[0-9a-f]{32}"))) {
            throw error(HttpStatus.BAD_REQUEST, "space-provision-invalid", "The Space setup request is invalid.");
        }
        Set<String> members = new HashSet<>(request.memberAccountRefs());
        if (members.size() != request.memberAccountRefs().size()) {
            throw error(HttpStatus.BAD_REQUEST, "space-provision-invalid", "Duplicate member accounts are invalid.");
        }
        SpaceProvisioningPort.Result result;
        try {
            result = spaces.provision(identity.organizationId(), request.spaceRef(),
                    identity.accountId(), members);
        } catch (SpaceProvisioningPort.Conflict conflict) {
            throw error(HttpStatus.CONFLICT, "space-provision-conflict",
                    "The Space already has different membership intent.");
        }
        audit.publish(new AuditEvent(identity.organizationId(), request.spaceRef(),
                identity.accountId(), "admin-spaces", AuditAction.SPACE_PROVISIONED,
                Instant.now(), "space-provision-" + UUID.randomUUID(),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("result", result.name(), "memberCount", members.size() + 1)));
        return new SpaceProvisionResponse(request.spaceRef(), result.name());
    }

    public SpaceMembershipAdministrationPort.MemberState getMember(Jwt jwt, String spaceRef,
            String accountRef) {
        var identity = requireAdministrator(jwt, "read");
        validateRefs(spaceRef, accountRef);
        try {
            return memberships.get(identity.organizationId(), spaceRef,
                    identity.accountId(), accountRef);
        } catch (SpaceMembershipAdministrationPort.Absent absent) {
            throw error(HttpStatus.NOT_FOUND, "space-membership-absent", "The member is unavailable.");
        } catch (SpaceMembershipAdministrationPort.Revoked revoked) {
            throw new SpaceMemberRevokedException(revoked.strongEtag());
        } catch (SpaceMembershipAdministrationPort.Denied denied) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Space administration is denied.");
        }
    }

    public SpaceMemberListResponse listMembers(Jwt jwt, String spaceRef,
            String afterAccountRef, int limit) {
        var identity = requireAdministrator(jwt, "list");
        if (spaceRef == null || !spaceRef.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,254}")
                || (afterAccountRef != null && !afterAccountRef.matches("acct_[0-9a-f]{32}"))
                || limit < 1 || limit > 100) {
            throw error(HttpStatus.BAD_REQUEST, "space-membership-page-invalid", "The member page is invalid.");
        }
        List<SpaceMembershipAdministrationPort.MemberState> page;
        try {
            page = memberships.list(identity.organizationId(), spaceRef,
                    identity.accountId(), afterAccountRef == null ? "" : afterAccountRef,
                    limit + 1);
        } catch (SpaceMembershipAdministrationPort.Denied denied) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Space administration is denied.");
        }
        boolean hasMore = page.size() > limit;
        var visible = page.stream().limit(limit).toList();
        String nextCursor = hasMore ? visible.getLast().accountRef() : null;
        return new SpaceMemberListResponse(visible.stream().map(SpaceAdminApiService::response).toList(),
                nextCursor);
    }

    @Transactional
    public SpaceMembershipAdministrationPort.MemberState grant(Jwt jwt, String spaceRef,
            String accountRef, SpaceMemberChangeRequest request, String ifMatch,
            String ifNoneMatch) {
        var identity = requireAdministrator(jwt, "grant");
        validateRefs(spaceRef, accountRef);
        if (request == null || request.permissionLevel() == null) {
            throw error(HttpStatus.BAD_REQUEST, "space-membership-invalid", "A permission level is required.");
        }
        boolean firstGrant = "*".equals(ifNoneMatch);
        if (ifMatch == null && !firstGrant) {
            throw error(HttpStatus.PRECONDITION_REQUIRED, "space-membership-precondition-required",
                    "A current member precondition is required.");
        }
        if ((firstGrant && ifMatch != null) || (!firstGrant && ifNoneMatch != null)
                || (!firstGrant && !strongEtag(ifMatch))) {
            throw error(HttpStatus.BAD_REQUEST, "space-membership-precondition-invalid",
                    "The member precondition is invalid.");
        }
        Set<SpaceAccessPort.Permission> permissions = switch (request.permissionLevel()) {
            case VIEW -> EnumSet.of(SpaceAccessPort.Permission.VIEW);
            case EDIT -> EnumSet.of(SpaceAccessPort.Permission.VIEW, SpaceAccessPort.Permission.EDIT);
            case ADMIN -> EnumSet.allOf(SpaceAccessPort.Permission.class);
        };
        SpaceMembershipAdministrationPort.MemberState result;
        try {
            result = memberships.grant(identity.organizationId(), spaceRef,
                    identity.accountId(), accountRef, permissions, ifMatch, firstGrant);
        } catch (SpaceMembershipAdministrationPort.Stale stale) {
            throw error(HttpStatus.PRECONDITION_FAILED, "space-membership-stale", "The member version is stale.");
        } catch (SpaceMembershipAdministrationPort.Absent absent) {
            throw error(HttpStatus.NOT_FOUND, "space-membership-absent", "The member is unavailable.");
        } catch (SpaceMembershipAdministrationPort.Denied denied) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Space administration is denied.");
        } catch (SpaceMembershipAdministrationPort.LastAdministrator last) {
            throw error(HttpStatus.CONFLICT, "space-last-admin", "The last Space administrator must remain.");
        } catch (IllegalArgumentException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "space-membership-invalid", "Permissions must be hierarchical.");
        }
        publishMembershipAudit(identity.organizationId(), spaceRef, identity.accountId(),
                AuditAction.SPACE_MEMBERSHIP_UPDATED);
        return result;
    }

    @Transactional
    public void revoke(Jwt jwt, String spaceRef, String accountRef, String ifMatch) {
        var identity = requireAdministrator(jwt, "revoke");
        validateRefs(spaceRef, accountRef);
        if (ifMatch == null) {
            throw error(HttpStatus.PRECONDITION_REQUIRED, "space-membership-precondition-required",
                    "A current member precondition is required.");
        }
        if (!strongEtag(ifMatch)) {
            throw error(HttpStatus.BAD_REQUEST, "space-membership-precondition-invalid",
                    "A strong member version is required.");
        }
        try {
            memberships.revoke(identity.organizationId(), spaceRef,
                    identity.accountId(), accountRef, ifMatch);
        } catch (SpaceMembershipAdministrationPort.Stale stale) {
            throw error(HttpStatus.PRECONDITION_FAILED, "space-membership-stale", "The member version is stale.");
        } catch (SpaceMembershipAdministrationPort.Absent absent) {
            throw error(HttpStatus.NOT_FOUND, "space-membership-absent", "The member is unavailable.");
        } catch (SpaceMembershipAdministrationPort.Denied denied) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Space administration is denied.");
        } catch (SpaceMembershipAdministrationPort.LastAdministrator last) {
            throw error(HttpStatus.CONFLICT, "space-last-admin", "The last Space administrator must remain.");
        }
        publishMembershipAudit(identity.organizationId(), spaceRef, identity.accountId(),
                AuditAction.SPACE_MEMBERSHIP_REVOKED);
    }

    private OrganizationIdentityContext requireAdministrator(Jwt jwt, String operation) {
        if (!admission.allows(jwt)) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Organization access is denied.");
        }
        var identity = identities.resolve(jwt);
        if (identity.roles().size() != 1
                || !(identity.roles().contains("owner") || identity.roles().contains("admin"))) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Space setup requires an owner or administrator.");
        }
        capabilities.requireCapability(jwt, "admin.policy.edit", "spaces", operation);
        return identity;
    }

    private static void validateRefs(String spaceRef, String accountRef) {
        if (spaceRef == null || !spaceRef.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,254}")
                || accountRef == null || !accountRef.matches("acct_[0-9a-f]{32}")) {
            throw error(HttpStatus.BAD_REQUEST, "space-membership-invalid", "Space or account reference is invalid.");
        }
    }

    private static boolean strongEtag(String value) {
        return value.matches("\"sm-[0-9]+\"");
    }

    public static SpaceMemberResponse response(SpaceMembershipAdministrationPort.MemberState state) {
        return new SpaceMemberResponse(state.accountRef(), state.permissions().stream()
                .map(permission -> SpaceMemberChangeRequest.PermissionValue.valueOf(permission.name()))
                .sorted().toList());
    }

    private void publishMembershipAudit(String organizationRef, String spaceRef,
            String actorRef, AuditAction action) {
        audit.publish(new AuditEvent(organizationRef, spaceRef, actorRef,
                "admin-spaces", action, Instant.now(),
                "space-membership-" + UUID.randomUUID(), AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("result", "completed")));
    }

    private static ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "spaces"));
    }
}
