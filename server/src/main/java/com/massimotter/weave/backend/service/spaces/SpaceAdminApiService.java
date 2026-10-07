package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionRequest;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionResponse;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import java.time.Instant;
import java.util.HashSet;
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
    private final AuditEventPublisher audit;

    public SpaceAdminApiService(DeploymentOrganizationAdmission admission,
            OrganizationIdentityContextResolver identities,
            WorkspaceCapabilityService capabilities, SpaceProvisioningPort spaces,
            AuditEventPublisher audit) {
        this.admission = admission;
        this.identities = identities;
        this.capabilities = capabilities;
        this.spaces = spaces;
        this.audit = audit;
    }

    @Transactional
    public SpaceProvisionResponse provision(Jwt jwt, SpaceProvisionRequest request) {
        if (!admission.allows(jwt)) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Organization access is denied.");
        }
        var identity = identities.resolve(jwt);
        if (identity.roles().size() != 1
                || !(identity.roles().contains("owner") || identity.roles().contains("admin"))) {
            throw error(HttpStatus.FORBIDDEN, "space-admin-denied", "Space setup requires an owner or administrator.");
        }
        capabilities.requireCapability(jwt, "admin.policy.edit", "spaces", "provision");
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

    private static ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "spaces"));
    }
}
