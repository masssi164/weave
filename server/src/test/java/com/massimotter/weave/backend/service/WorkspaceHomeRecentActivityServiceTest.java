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
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkspaceHomeRecentActivityServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String SHARED_CONTEXT = "workspace-a";

    @Test
    void durableSpaceRevocationRemovesHomeActivityDespiteStaticGrant() {
        SpaceAccessPort spaces = mock(SpaceAccessPort.class);
        AtomicBoolean granted = new AtomicBoolean(true);
        when(spaces.allows(any(), any(), any(), any())).thenAnswer(call ->
                granted.get() && SHARED_CONTEXT.equals(call.getArgument(1)));
        var staticRights = mock(com.massimotter.weave.backend.context.authz.ContextAuthorizationPort.class);
        WorkspaceHomeRecentActivityService service = new WorkspaceHomeRecentActivityService(
                auditFixture(), staticRights, properties(), identityContexts(), spaces);

        assertThat(service.recentActivity(jwt("collaborator-sub", TENANT))).hasSize(2);
        granted.set(false);
        assertThat(service.recentActivity(jwt("collaborator-sub", TENANT))).isEmpty();
        verify(spaces, atLeastOnce()).allows(TENANT, SHARED_CONTEXT,
                IdentityReferences.accountId("https://auth.weave.test/realms/weave", "collaborator-sub"),
                SpaceAccessPort.Permission.VIEW);
        verifyNoInteractions(staticRights);
    }

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
    void completedUserFilesActivityIsOwnerOnlyEvenWithSharedContextView() {
        InMemoryAuditEventPublisher audit = new InMemoryAuditEventPublisher();
        audit.publish(userFileEvent("files:user-http", "completed", "completed-user-write"));
        audit.publish(userFileEvent("files:user-http", "ambiguous", "ambiguous-user-write"));
        audit.publish(userFileEvent("files:other", "completed", "other-source-write"));
        WorkspaceHomeRecentActivityService service = new WorkspaceHomeRecentActivityService(
                audit,
                request -> ContextAuthorizationDecision.allow("shared workspace membership"),
                properties(),
                identityContexts());

        assertThat(service.recentActivity(jwt("author-sub", TENANT)))
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.action()).isEqualTo("files.user_write.completed");
                    assertThat(activity.visibility()).isEqualTo("private");
                    assertThat(activity.actorIsCurrentUser()).isTrue();
                    assertThat(activity.supportSafe()).isTrue();
                });
        assertThat(service.recentActivity(jwt("collaborator-sub", TENANT))).isEmpty();
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

    private AuditEvent userFileEvent(String source, String result, String key) {
        return new AuditEvent(
                TENANT, SHARED_CONTEXT, "user:author-sub", source,
                AuditAction.FILES_OPERATION_INTENT_RECORDED,
                Instant.parse("2026-07-12T10:08:00Z"), key, AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("domain", "files", "operation", "updateFilesItemContent",
                        "result", result, "fileId", "file:private"));
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
