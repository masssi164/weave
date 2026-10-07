package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Bounded direct materialized relation projection; each target is rechecked by its owning domain. */
@Service
public class SpaceRelationshipUserService {
    private static final String EVENT_RELATION_PREFIX = "relation:event:";
    private static final String FILE_RELATION_PREFIX = "relation:file:";
    private final OrganizationIdentityContextResolver identities;
    private final DeploymentOrganizationAdmission admission;
    private final ContextAuthorizationProperties context;
    private final SpaceAccessPort spaces;
    private final FilesUserResourceRepository fileResources;
    private final FilesUserApiService files;
    private final CalendarUserApiService calendar;

    public SpaceRelationshipUserService(OrganizationIdentityContextResolver identities,
            DeploymentOrganizationAdmission admission,
            ContextAuthorizationProperties context, SpaceAccessPort spaces,
            FilesUserResourceRepository fileResources, FilesUserApiService files,
            CalendarUserApiService calendar) {
        this.identities = identities;
        this.admission = admission;
        this.context = context;
        this.spaces = spaces;
        this.fileResources = fileResources;
        this.files = files;
        this.calendar = calendar;
    }

    public SpaceRelationshipListResponse list(Jwt jwt, String spaceRef,
            String afterRelationRef, int limit) {
        if (jwt == null || "weave-mcp-server".equals(jwt.getClaimAsString("azp"))
                || !admission.allows(jwt)) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member session is required.");
        }
        var identity = identities.resolve(jwt);
        if (identity.roles().size() != 1 || spaceRef == null || spaceRef.isBlank()
                || !spaces.allows(identity.organizationId(), spaceRef,
                        identity.accountId(), Permission.VIEW)) {
            throw error(HttpStatus.NOT_FOUND, "space-not-found", "The Space was not found.");
        }
        if (limit < 1 || limit > 100 || (afterRelationRef != null
                && !validCursor(afterRelationRef))) {
            throw error(HttpStatus.BAD_REQUEST, "space-query-invalid", "The relation page request is invalid.");
        }
        String owner = context.principalRef(jwt.getSubject());
        if (owner == null) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member identity is required.");
        }
        var visible = new ArrayList<SpaceRelationshipResponse>();
        if (afterRelationRef == null || afterRelationRef.startsWith(EVENT_RELATION_PREFIX)) {
            String afterEventId = afterRelationRef == null ? ""
                    : afterRelationRef.substring(EVENT_RELATION_PREFIX.length());
            int scanned = 0;
            while (visible.size() < limit) {
                java.util.List<String> candidates;
                try {
                    candidates = calendar.materializedEventRefsInSpace(jwt, spaceRef, afterEventId, 100);
                } catch (ApiErrorException denied) {
                    if (denied.status() == HttpStatus.FORBIDDEN) {
                        break;
                    }
                    throw resourceUnavailable();
                }
                if (candidates == null) {
                    throw resourceUnavailable();
                }
                for (String eventId : candidates) {
                    if (eventId == null || !eventId.matches("event:[0-9a-f]{64}")
                            || eventId.compareTo(afterEventId) <= 0) {
                        throw resourceUnavailable();
                    }
                    afterEventId = eventId;
                    try {
                        var checked = calendar.readMaterializedEventInSpace(jwt, spaceRef, eventId);
                        if (checked == null || !eventId.equals(checked.id())) {
                            throw resourceUnavailable();
                        }
                    } catch (ApiErrorException unavailable) {
                        if (unavailable.status() == HttpStatus.NOT_FOUND) {
                            continue;
                        }
                        throw resourceUnavailable();
                    }
                    visible.add(new SpaceRelationshipResponse(EVENT_RELATION_PREFIX + eventId,
                            "CONTAINS", "EVENT", eventId));
                    if (visible.size() == limit) {
                        return new SpaceRelationshipListResponse(visible,
                                visible.getLast().relationRef());
                    }
                }
                scanned += candidates.size();
                if (candidates.size() < 100) {
                    break;
                }
                if (scanned >= 1000) {
                    throw resourceUnavailable();
                }
            }
        }
        String afterFileId = afterRelationRef != null
                && afterRelationRef.startsWith(FILE_RELATION_PREFIX)
                ? afterRelationRef.substring(FILE_RELATION_PREFIX.length()) : "";
        var candidates = fileResources.activeInSpace(identity.organizationId(), spaceRef,
                owner, afterFileId, limit - visible.size());
        for (var candidate : candidates) {
            try {
                var checked = files.inspect(jwt, candidate.fileId());
                if (checked == null || !candidate.fileId().equals(checked.fileId())
                        || !checked.allowedActions().contains("inspect")) {
                    throw resourceUnavailable();
                }
            } catch (ApiErrorException denied) {
                if (denied.status() == HttpStatus.FORBIDDEN) {
                    return new SpaceRelationshipListResponse(visible, null);
                }
                throw resourceUnavailable();
            }
            visible.add(new SpaceRelationshipResponse(
                    FILE_RELATION_PREFIX + candidate.fileId(), "CONTAINS", "FILE", candidate.fileId()));
        }
        String next = visible.size() == limit ? visible.getLast().relationRef() : null;
        return new SpaceRelationshipListResponse(visible, next);
    }

    private static boolean validCursor(String value) {
        if (value.startsWith(EVENT_RELATION_PREFIX)) {
            return value.substring(EVENT_RELATION_PREFIX.length()).matches("event:[0-9a-f]{64}");
        }
        return value.startsWith(FILE_RELATION_PREFIX)
                && value.length() > FILE_RELATION_PREFIX.length()
                && value.length() <= FILE_RELATION_PREFIX.length() + 255
                && value.substring(FILE_RELATION_PREFIX.length()).startsWith("file:")
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static ApiErrorException resourceUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "space-resource-check-unavailable",
                "Current resource access could not be verified.");
    }

    private static ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "spaces"));
    }
}
