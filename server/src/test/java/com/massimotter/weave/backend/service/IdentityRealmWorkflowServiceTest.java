package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyProperties;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDesiredState;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunRequest;
import com.massimotter.weave.backend.identity.realm.InMemoryIdentityRealmEvidenceRepository;
import com.massimotter.weave.backend.identity.realm.KeycloakRealmDryRunProvider;
import com.massimotter.weave.backend.identity.realm.KeycloakRealmLiveApplyAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdentityRealmWorkflowServiceTest {

    @Test
    void dryRunUsesInjectedClockForDeterministicAuditRefAndEvidenceTimestamp() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        IdentityRealmWorkflowService service = identityRealmWorkflowService(auditPublisher);

        var report = service.dryRunIdentityRealm(new IdentityRealmDryRunRequest(null, desiredState(), "deterministic clock check"), jwt());

        assertThat(report.auditRefs()).containsExactly("identity-realm-dry-run-1779843819000");
        assertThat(auditPublisher.events()).hasSize(1);
        assertThat(auditPublisher.events().get(0).occurredAt()).isEqualTo(Instant.parse("2026-05-27T01:03:39Z"));
        assertThat(auditPublisher.events().get(0).action()).isEqualTo(AuditAction.PROVIDER_REPLACEMENT_DRY_RUN);
        assertThat(auditPublisher.events().get(0).payload())
                .containsEntry("providerKey", "keycloak-realm")
                .containsEntry("supportSafe", true)
                .containsEntry("dryRunReasonPresent", true);
    }

    @Test
    void malformedApplyWithMissingDesiredStateIsBlockedWithoutServerError() {
        IdentityRealmWorkflowService service = identityRealmWorkflowService();
        var request = new com.massimotter.weave.backend.identity.realm.IdentityRealmApplyRequest(
                null,
                null,
                "missing desired state",
                false,
                false,
                List.of("issuer+subject:admin"),
                null,
                "malformed apply check");

        var report = service.applyIdentityRealm(request, jwt());

        assertThat(report.decision()).isEqualTo("blocked");
        assertThat(report.lastAdminGuardPassed()).isFalse();
        assertThat(report.blockedReasons()).contains("last-admin guard requires at least one retained immutable admin identity key");
    }

    private IdentityRealmWorkflowService identityRealmWorkflowService() {
        return identityRealmWorkflowService(new InMemoryAuditEventPublisher());
    }

    private IdentityRealmWorkflowService identityRealmWorkflowService(InMemoryAuditEventPublisher auditPublisher) {
        return new IdentityRealmWorkflowService(
                mock(WorkspaceCapabilityService.class),
                auditPublisher,
                List.of(new KeycloakRealmDryRunProvider()),
                new InMemoryIdentityRealmEvidenceRepository(),
                List.of(new KeycloakRealmLiveApplyAdapter(new IdentityRealmApplyProperties())),
                new IdentityRealmApplyProperties(),
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC));
    }

    private IdentityRealmDesiredState desiredState() {
        return new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(),
                List.of("owner", "admin", "member"),
                List.of(),
                List.of("openid", "profile", "email"),
                List.of(),
                List.of("https://app.weave.test/oauthredirect"),
                List.of(),
                List.of(),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("issuer+subject:admin", "retained admin", true, List.of("owner"))),
                List.of("issuer+subject:admin"),
                "sub",
                List.of(),
                List.of());
    }

    private Jwt jwt() {
        return new Jwt(
                "token",
                Instant.parse("2026-05-27T01:00:00Z"),
                Instant.parse("2026-05-27T02:00:00Z"),
                Map.of("alg", "none"),
                Map.of("sub", "admin", "weave_tenant", "weave-dogfood"));
    }
}
