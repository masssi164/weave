package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceHomeRecentActivityServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String SHARED_CONTEXT = "workspace-a";

    @Test
    void projectsOnlyAuthorizedCompletedSupportSafeActivityWithoutPayloadOrIdentityLeakage() {
        InMemoryAuditEventPublisher audit = auditFixture();
        List<ContextAuthorizationRequest> authorizationChecks = new ArrayList<>();
        WorkspaceHomeRecentActivityService service = new WorkspaceHomeRecentActivityService(
                audit,
                request -> {
                    authorizationChecks.add(request);
                    return SHARED_CONTEXT.equals(request.contextId())
                            ? ContextAuthorizationDecision.allow("shared workspace membership")
                            : ContextAuthorizationDecision.deny("outside the shared workspace");
                },
                properties(),
                identityContexts());

        var collaboratorView = service.recentActivity(jwt("collaborator-sub", TENANT));
        var authorView = service.recentActivity(jwt("author-sub", TENANT));

        assertThat(collaboratorView).hasSize(2);
        assertThat(collaboratorView).extracting(item -> item.occurredAt()).containsExactly(
                Instant.parse("2026-07-12T10:02:00Z"),
                Instant.parse("2026-07-12T10:01:00Z"));
        assertThat(collaboratorView).allSatisfy(item -> {
            assertThat(item.activityRef()).matches("activity:sha256:[0-9a-f]{64}");
            assertThat(item.actorRefHash()).matches("sha256:[0-9a-f]{64}");
            assertThat(item.domain()).isEqualTo("files");
            assertThat(item.action()).isEqualTo("files.webdav_write.completed");
            assertThat(item.visibility()).isEqualTo("workspace");
            assertThat(item.supportSafe()).isTrue();
        });
        assertThat(collaboratorView).extracting(item -> item.actorIsCurrentUser())
                .containsExactly(true, false);
        assertThat(authorView).extracting(item -> item.actorIsCurrentUser())
                .containsExactly(false, true);
        assertThat(authorView).extracting(item -> item.activityRef())
                .containsExactlyElementsOf(collaboratorView.stream().map(item -> item.activityRef()).toList());
        assertThat(authorView).extracting(item -> item.actorRefHash())
                .containsExactlyElementsOf(collaboratorView.stream().map(item -> item.actorRefHash()).toList());

        String rendered = collaboratorView.toString();
        assertThat(rendered)
                .doesNotContain("author-sub")
                .doesNotContain("collaborator-sub")
                .doesNotContain("quarterly-plan.pdf")
                .doesNotContain("provider-resource-42")
                .doesNotContain("files.example.test");
        assertThat(authorizationChecks).allSatisfy(request ->
                assertThat(request.permission()).isEqualTo(ContextPermission.VIEW));
        assertThat(authorizationChecks).anySatisfy(request ->
                assertThat(request.contextId()).isEqualTo("workspace-private"));
    }

    @Test
    void outsiderAndWrongTenantCannotObserveSharedActivity() {
        InMemoryAuditEventPublisher audit = auditFixture();
        List<ContextAuthorizationRequest> checks = new ArrayList<>();
        WorkspaceHomeRecentActivityService service = new WorkspaceHomeRecentActivityService(
                audit,
                request -> {
                    checks.add(request);
                    return ContextAuthorizationDecision.deny("outsider has no Context VIEW membership");
                },
                properties(),
                identityContexts());

        assertThat(service.recentActivity(jwt("outsider-sub", TENANT))).isEmpty();
        assertThat(checks).isNotEmpty().allSatisfy(request -> {
            assertThat(request.tenantId()).isEqualTo(TENANT);
            assertThat(request.principalRef()).isEqualTo("user:outsider-sub");
            assertThat(request.permission()).isEqualTo(ContextPermission.VIEW);
        });
        checks.clear();
        assertThat(service.recentActivity(jwt("outsider-sub", "tenant-b"))).isEmpty();

        assertThat(checks).isNotEmpty().allSatisfy(request -> {
            assertThat(request.tenantId()).isEqualTo("tenant-b");
            assertThat(request.principalRef()).isEqualTo("user:outsider-sub");
            assertThat(request.permission()).isEqualTo(ContextPermission.VIEW);
        });
    }

    @Test
    void auditOrAuthorizationFailureFailsClosedWithoutBreakingHome() {
        AuditEventPublisher unreadablePublisher = new AuditEventPublisher() {
            @Override
            public void publish(AuditEvent event) {
                throw new IllegalStateException("audit storage is unavailable");
            }

            @Override
            public List<AuditEvent> events() {
                throw new IllegalStateException("raw audit storage failure");
            }
        };
        WorkspaceHomeRecentActivityService unreadableAudit = new WorkspaceHomeRecentActivityService(
                unreadablePublisher,
                request -> ContextAuthorizationDecision.allow("unused"),
                properties(),
                identityContexts());
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        audit.publish(event(
                TENANT,
                SHARED_CONTEXT,
                "user:author-sub",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "activity-failure-test",
                "2026-07-12T10:00:00Z"));
        WorkspaceHomeRecentActivityService failedAuthorization = new WorkspaceHomeRecentActivityService(
                audit,
                request -> {
                    throw new IllegalStateException("authorization backend failure");
                },
                properties(),
                identityContexts());

        assertThat(unreadableAudit.recentActivity(jwt("author-sub", TENANT))).isEmpty();
        assertThat(failedAuthorization.recentActivity(jwt("author-sub", TENANT))).isEmpty();
    }

    @Test
    void usesTheConfiguredOrganizationWhenNativeKeycloakClaimsHaveNoTenantAlias() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        audit.publish(event(
                "tenant-default",
                SHARED_CONTEXT,
                "user:author-sub",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "native-organization-file-write",
                "2026-07-12T10:08:00Z"));
        List<ContextAuthorizationRequest> checks = new ArrayList<>();
        WorkspaceHomeRecentActivityService service = new WorkspaceHomeRecentActivityService(
                audit,
                request -> {
                    checks.add(request);
                    return ContextAuthorizationDecision.allow("shared workspace membership");
                },
                properties(),
                identityContexts());

        assertThat(service.recentActivity(jwtWithoutTenantAlias("author-sub"))).hasSize(1);
        assertThat(checks).singleElement().satisfies(request -> {
            assertThat(request.tenantId()).isEqualTo("tenant-default");
            assertThat(request.principalRef()).isEqualTo("user:author-sub");
        });
    }

    private InMemoryAuditEventPublisher auditFixture() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        audit.publish(event(
                TENANT,
                SHARED_CONTEXT,
                "user:author-sub",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "author-file-write",
                "2026-07-12T10:01:00Z"));
        audit.publish(event(
                TENANT,
                SHARED_CONTEXT,
                "user:collaborator-sub",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "collaborator-file-write",
                "2026-07-12T10:02:00Z"));
        audit.publish(event(
                TENANT,
                SHARED_CONTEXT,
                "user:author-sub",
                AuditAction.FILES_WEBDAV_WRITE_ATTEMPTED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "attempted-file-write",
                "2026-07-12T10:03:00Z"));
        audit.publish(event(
                TENANT,
                SHARED_CONTEXT,
                "user:author-sub",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.INTERNAL_REDACTED,
                "internal-file-write",
                "2026-07-12T10:04:00Z"));
        audit.publish(event(
                TENANT,
                SHARED_CONTEXT,
                "user:author-sub",
                AuditAction.CHAT_MESSAGE_SENT,
                AuditRedactionLevel.SUPPORT_SAFE,
                "unknown-home-action",
                "2026-07-12T10:05:00Z"));
        audit.publish(event(
                TENANT,
                "workspace-private",
                "user:author-sub",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "private-file-write",
                "2026-07-12T10:06:00Z"));
        audit.publish(event(
                "tenant-b",
                SHARED_CONTEXT,
                "user:other-tenant",
                AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                AuditRedactionLevel.SUPPORT_SAFE,
                "other-tenant-file-write",
                "2026-07-12T10:07:00Z"));
        return audit;
    }

    private AuditEvent event(
            String tenant,
            String context,
            String actor,
            AuditAction action,
            AuditRedactionLevel redactionLevel,
            String idempotencyKey,
            String occurredAt) {
        return new AuditEvent(
                tenant,
                context,
                actor,
                "files:webdav",
                action,
                Instant.parse(occurredAt),
                idempotencyKey,
                redactionLevel,
                Map.of(
                        "productPath", "/private/quarterly-plan.pdf",
                        "providerResourceId", "provider-resource-42",
                        "providerUrl", "https://files.example.test/raw",
                        "supportSafe", true));
    }

    private Jwt jwt(String subject, String tenant) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(subject)
                .claim("weave_tenant_id", tenant)
                .build();
    }

    private Jwt jwtWithoutTenantAlias(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(subject)
                .build();
    }

    private ContextAuthorizationProperties properties() {
        return new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    }

    private OrganizationIdentityContextResolver identityContexts() {
        return OrganizationIdentityContextResolver.configured(properties());
    }
}
