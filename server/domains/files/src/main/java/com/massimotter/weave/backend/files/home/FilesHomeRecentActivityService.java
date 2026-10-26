package com.massimotter.weave.backend.files.home;

import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEventQuery;
import com.massimotter.weave.backend.home.port.WorkspaceHomeActivitySource;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.home.api.WorkspaceHomeRecentActivityResponse;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

public class FilesHomeRecentActivityService implements WorkspaceHomeActivitySource {

    private static final int MAX_RECENT_ACTIVITIES = 20;
    private static final Map<AuditAction, CanonicalActivity> COMPLETED_ACTIVITIES = Map.of(
            AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
            new CanonicalActivity("files", AuditAction.FILES_WEBDAV_WRITE_COMPLETED.wireName(), "workspace"),
            AuditAction.FILES_OPERATION_INTENT_RECORDED,
            new CanonicalActivity("files", "files.user_write.completed", "private"));

    private final AuditEventPublisher auditEvents;
    private final SpaceAccessPort spaces;
    private final ContextAuthorizationProperties contextProperties;
    private final OrganizationIdentityContextResolver identityContexts;

    public FilesHomeRecentActivityService(
            AuditEventPublisher auditEvents,
            ContextAuthorizationProperties contextProperties,
            OrganizationIdentityContextResolver identityContexts,
            SpaceAccessPort spaces) {
        this.auditEvents = auditEvents;
        this.spaces = java.util.Objects.requireNonNull(spaces, "spaces");
        this.contextProperties = contextProperties;
        this.identityContexts = java.util.Objects.requireNonNull(identityContexts, "identityContexts");
    }

    @Override
    public String domainId() { return "files"; }

    @Override
    public List<WorkspaceHomeRecentActivityResponse> recentActivity(Jwt jwt) {
        CallerContext caller = caller(jwt);
        if (caller == null) {
            return List.of();
        }
        var visible = new java.util.ArrayList<WorkspaceHomeRecentActivityResponse>();
        try {
            for (int page = 0; visible.size() < MAX_RECENT_ACTIVITIES; page++) {
                var candidates = auditEvents.events(new AuditEventQuery(caller.tenantId,
                        List.copyOf(COMPLETED_ACTIVITIES.keySet()), List.of(AuditRedactionLevel.SUPPORT_SAFE),
                        page, AuditEventQuery.MAX_PAGE_SIZE));
                for (var event : candidates) {
                    if (eligible(event, caller) && mayView(event, caller)) {
                        visible.add(project(event, caller));
                        if (visible.size() == MAX_RECENT_ACTIVITIES) break;
                    }
                }
                if (candidates.size() < AuditEventQuery.MAX_PAGE_SIZE) break;
            }
            return List.copyOf(visible);
        } catch (RuntimeException failure) {
            return List.of();
        }
    }

    private boolean eligible(AuditEvent event, CallerContext caller) {
        return event != null
                && event.contextId() != null
                && event.tenantId().equals(caller.tenantId)
                && event.redactionLevel() == AuditRedactionLevel.SUPPORT_SAFE
                && COMPLETED_ACTIVITIES.containsKey(event.action())
                && (event.action() != AuditAction.FILES_OPERATION_INTENT_RECORDED
                        || ("files:user-http".equals(event.sourceRef())
                                && "completed".equals(event.payload().get("result"))
                                && event.actorRef().equals(caller.principalRef)));
    }

    private boolean mayView(AuditEvent event, CallerContext caller) {
        try {
            return spaces.allows(caller.tenantId, event.contextId(), caller.accountRef,
                    SpaceAccessPort.Permission.VIEW);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private WorkspaceHomeRecentActivityResponse project(AuditEvent event, CallerContext caller) {
        CanonicalActivity canonical = COMPLETED_ACTIVITIES.get(event.action());
        return new WorkspaceHomeRecentActivityResponse(
                "activity:" + sha256("weave-home-activity-v1", event.tenantId(), event.idempotencyKey()),
                canonical.domain,
                canonical.action,
                event.occurredAt(),
                canonical.visibility,
                sha256("weave-home-actor-v1", event.tenantId(), event.actorRef()),
                event.actorRef().equals(caller.principalRef),
                true);
    }

    private CallerContext caller(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        try {
            String principalClaim = firstText(
                    claim(jwt, contextProperties.principalClaim()),
                    jwt.getSubject());
            String principalRef = contextProperties.principalRef(principalClaim);
            var identity = identityContexts.resolve(jwt);
            return principalRef == null
                    ? null
                    : new CallerContext(identity.organizationId(), principalRef, identity.accountId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String claim(Jwt jwt, String claimName) {
        if (claimName == null || claimName.isBlank()) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String sha256(String namespace, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(namespace.getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                digest.update((byte) 0);
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for Home activity references", exception);
        }
    }

    private record CallerContext(String tenantId, String principalRef, String accountRef) {
    }

    private record CanonicalActivity(String domain, String action, String visibility) {
    }
}
