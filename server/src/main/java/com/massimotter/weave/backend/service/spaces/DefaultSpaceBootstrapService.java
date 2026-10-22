package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.spaces.DefaultSpaceBootstrapResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit product setup using live identity reads, never human impersonation or IAM writes. */
@Service
public class DefaultSpaceBootstrapService {
    private final KeycloakIdentityAdminClient keycloak;
    private final OrganizationIdentityContextResolver identities;
    private final WorkspaceCapabilityService capabilities;
    private final DefaultSpacePreparationPort spaces;
    private final AuditEventPublisher audit;
    private final OAuth2ResourceServerProperties resourceServer;
    private final DeploymentOrganizationProperties primaryOrganization;

    public DefaultSpaceBootstrapService(KeycloakIdentityAdminClient keycloak,
            OrganizationIdentityContextResolver identities, WorkspaceCapabilityService capabilities,
            DefaultSpacePreparationPort spaces, AuditEventPublisher audit,
            OAuth2ResourceServerProperties resourceServer, DeploymentOrganizationProperties primaryOrganization) {
        this.keycloak = keycloak;
        this.identities = identities;
        this.capabilities = capabilities;
        this.spaces = spaces;
        this.audit = audit;
        this.resourceServer = resourceServer;
        this.primaryOrganization = primaryOrganization;
    }

    @Transactional
    public DefaultSpaceBootstrapResponse prepare() {
        OrganizationIdentityContext owner = currentOwner();
        capabilities.requireCapabilityForVerifiedMember(owner, "admin.policy.edit", "spaces", "bootstrap");
        for (String capability : List.of("files.read", "files.upload", "calendar.read", "calendar.manage_events")) {
            capabilities.requireCapabilityForVerifiedMember(owner, capability, "spaces", "prepare-human-test");
        }
        var snapshot = capabilities.snapshotForVerifiedMember(owner);
        if (!ready(snapshot.files()) || !ready(snapshot.calendar())) {
            throw error(HttpStatus.CONFLICT, "default-space-capabilities-not-ready");
        }
        DefaultSpacePreparationPort.Result result;
        try {
            result = spaces.prepare(owner.organizationId(), owner.accountId());
        } catch (DefaultSpacePreparationPort.Conflict conflict) {
            throw error(HttpStatus.CONFLICT, "default-space-owner-access-conflict");
        }
        audit.publish(new AuditEvent(owner.organizationId(), "workspace-default",
                "operator:default-space-bootstrap", "default-space-bootstrap", AuditAction.SPACE_PROVISIONED,
                Instant.now(), "default-space-bootstrap-" + UUID.randomUUID(), AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("result", result.name(), "ownerAccessVerified", true)));
        return DefaultSpaceBootstrapResponse.passed(result.name());
    }

    private OrganizationIdentityContext currentOwner() {
        try {
            String providerOrganization = keycloak.configuredOrganizationId();
            if (!primaryOrganization.configured() || !primaryOrganization.keycloakId().equals(providerOrganization)) {
                throw error(HttpStatus.CONFLICT, "default-space-organization-mismatch");
            }
            var owners = keycloak.members(providerOrganization).stream()
                    .filter(member -> member.roles().contains("owner")).toList();
            if (owners.size() != 1 || !owners.getFirst().enabled()
                    || !owners.getFirst().roles().equals(List.of("owner"))) {
                throw error(HttpStatus.CONFLICT, "default-space-owner-unavailable");
            }
            var owner = keycloak.memberAuthorization(providerOrganization, owners.getFirst().subject());
            if (!owner.enabled() || !owner.activated() || !owner.roles().equals(List.of("owner"))
                    || !owner.subject().equals(owners.getFirst().subject())) {
                throw error(HttpStatus.CONFLICT, "default-space-owner-unavailable");
            }
            String issuer = resourceServer.getJwt().getIssuerUri();
            return new OrganizationIdentityContext(identities.configuredOrganizationId(), issuer, owner.subject(),
                    IdentityReferences.primaryIdentityKey(issuer, owner.subject()),
                    IdentityReferences.accountId(issuer, owner.subject()), owner.roles(), owner.groups(), List.of(),
                    Stream.concat(owner.roles().stream().map(role -> "role_claim:" + role),
                            owner.groups().stream().map(group -> "group_claim:" + group)).sorted().toList());
        } catch (ApiErrorException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "default-space-owner-verification-unavailable");
        }
    }

    private static boolean ready(WorkspaceCapabilityStatusResponse status) {
        return status != null && status.enabled() && status.policyState() == WorkspaceCapabilityPolicyState.ALLOWED
                && status.readiness() == WorkspaceCapabilityReadiness.READY;
    }

    private static ApiErrorException error(HttpStatus status, String code) {
        return new ApiErrorException(status, code, "Default Space preparation is unavailable.",
                Map.of("module", "spaces", "diagnosticsRedacted", true));
    }
}
