package com.massimotter.weave.backend.identity.organization.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.organization.api.OrganizationBootstrapRequest;
import com.massimotter.weave.backend.identity.organization.fixture.InMemoryOrganizationBootstrapRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrganizationBootstrapServiceTest {
    private static final String OWNER = "issuer+subject:https://auth.example.invalid/realms/weave#owner";
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final OrganizationIdentityContextResolver identities = mock(OrganizationIdentityContextResolver.class);
    private final InMemoryOrganizationBootstrapRepository repository = new InMemoryOrganizationBootstrapRepository();
    private final InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
    private final Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("owner").build();
    private final Instant now = Instant.parse("2026-10-07T09:00:00Z");
    private final OrganizationBootstrapService service = new OrganizationBootstrapService(
            capabilities, identities, repository, audit, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void permissionFailurePrecedesIdentityPersistenceAndAudit() {
        doThrow(new IllegalStateException("denied")).when(capabilities).requireCapability(
                jwt, "admin.policy.edit", "admin-control-plane", "bootstrap-organization");
        assertThatThrownBy(() -> service.bootstrapOrganization(request(List.of()), jwt)).hasMessage("denied");
        verifyNoInteractions(identities);
        assertThat(repository.findByOrganizationId("acme")).isEmpty();
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void recoveryKeysAlwaysRetainTheAdmittedOwnerAndAuditRedactsCredentials() {
        admittedOwner();
        var response = service.bootstrapOrganization(new OrganizationBootstrapRequest(
                " ACME ", "existing_org", List.of(OWNER, OWNER), "Bearer secret-value https://private.invalid"), jwt);
        assertThat(response.organizationId()).isEqualTo("acme");
        assertThat(response.retainedAdminPrimaryIdentityKeys()).containsExactly(OWNER);
        assertThat(response.bootstrappedAt()).isEqualTo(now);
        assertThat(repository.findByOrganizationId("acme")).isPresent();
        assertThat(audit.events()).singleElement().satisfies(event -> {
            assertThat(event.tenantId()).isEqualTo("acme");
            assertThat(event.payload().get("reason")).isEqualTo("[redacted]");
        });
    }

    @Test
    void invalidRecoveryKeysCannotPersistOrPublishSuccess() {
        admittedOwner();
        for (var keys : List.of(List.of("alice@example.invalid"), java.util.Collections.<String>nCopies(26, OWNER))) {
            assertThatThrownBy(() -> service.bootstrapOrganization(request(keys), jwt))
                    .isInstanceOfSatisfying(ApiErrorException.class,
                            error -> assertThat(error.code()).isEqualTo("organization-bootstrap-admin-key-invalid"));
        }
        assertThat(repository.findByOrganizationId("acme")).isEmpty();
        assertThat(audit.events()).isEmpty();
    }

    private OrganizationBootstrapRequest request(List<String> keys) {
        return new OrganizationBootstrapRequest("acme", "existing_org", keys, "bootstrap");
    }

    private void admittedOwner() {
        when(identities.resolve(jwt)).thenReturn(new OrganizationIdentityContext(
                "acme", "https://auth.example.invalid/realms/weave", "owner", OWNER, "account-owner",
                List.of("owner"), List.of(), List.of(), List.of()));
    }
}
