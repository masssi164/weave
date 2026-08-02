package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.WorkspaceHomeRecentActivityResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceHomeRecentActivityService {

    private static final int MAX_RECENT_ACTIVITIES = 20;
    private static final Map<AuditAction, CanonicalActivity> COMPLETED_ACTIVITIES = Map.of(
            AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
            new CanonicalActivity("files", AuditAction.FILES_WEBDAV_WRITE_COMPLETED.wireName(), "workspace"));

    private final AuditEventPublisher auditEvents;
    private final ContextAuthorizationPort contextAuthorization;
    private final ContextAuthorizationProperties contextProperties;
    private final OrganizationIdentityContextResolver identityContexts;

    public WorkspaceHomeRecentActivityService(
            AuditEventPublisher auditEvents,
            ContextAuthorizationPort contextAuthorization,
            ContextAuthorizationProperties contextProperties,
            OrganizationIdentityContextResolver identityContexts) {
        this.auditEvents = auditEvents;
        this.contextAuthorization = contextAuthorization;
        this.contextProperties = contextProperties;
        this.identityContexts = java.util.Objects.requireNonNull(identityContexts, "identityContexts");
    }

    public List<WorkspaceHomeRecentActivityResponse> recentActivity(Jwt jwt) {
        CallerContext caller = caller(jwt);
        if (caller == null) {
            return List.of();
        }
        List<AuditEvent> candidates;
        try {
            candidates = auditEvents.events();
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(event -> eligible(event, caller))
                .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed())
                .filter(event -> mayView(event, caller))
                .limit(MAX_RECENT_ACTIVITIES)
                .map(event -> project(event, caller))
                .toList();
    }

    private boolean eligible(AuditEvent event, CallerContext caller) {
        return event != null
                && event.contextId() != null
                && event.tenantId().equals(caller.tenantId)
                && event.redactionLevel() == AuditRedactionLevel.SUPPORT_SAFE
                && COMPLETED_ACTIVITIES.containsKey(event.action());
    }

    private boolean mayView(AuditEvent event, CallerContext caller) {
        try {
            return contextAuthorization.check(new ContextAuthorizationRequest(
                    caller.tenantId,
                    event.contextId(),
                    caller.principalRef,
                    ContextPermission.VIEW)).allowed();
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
            return principalRef == null
                    ? null
                    : new CallerContext(identityContexts.resolve(jwt).organizationId(), principalRef);
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

    private record CallerContext(String tenantId, String principalRef) {
    }

    private record CanonicalActivity(String domain, String action, String visibility) {
    }
}
