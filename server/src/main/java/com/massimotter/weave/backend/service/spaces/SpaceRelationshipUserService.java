package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.files.FilesUserApiService;
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
    private static final String FILE_RELATION_PREFIX = "relation:file:";
    private final OrganizationIdentityContextResolver identities;
    private final ContextAuthorizationProperties context;
    private final SpaceAccessPort spaces;
    private final FilesUserResourceRepository fileResources;
    private final FilesUserApiService files;

    public SpaceRelationshipUserService(OrganizationIdentityContextResolver identities,
            ContextAuthorizationProperties context, SpaceAccessPort spaces,
            FilesUserResourceRepository fileResources, FilesUserApiService files) {
        this.identities = identities;
        this.context = context;
        this.spaces = spaces;
        this.fileResources = fileResources;
        this.files = files;
    }

    public SpaceRelationshipListResponse list(Jwt jwt, String spaceRef,
            String afterRelationRef, int limit) {
        if (jwt == null || "weave-mcp-server".equals(jwt.getClaimAsString("azp"))) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member session is required.");
        }
        var identity = identities.resolve(jwt);
        if (identity.roles().size() != 1 || spaceRef == null || spaceRef.isBlank()
                || !spaces.allows(identity.organizationId(), spaceRef,
                        identity.accountId(), Permission.VIEW)) {
            throw error(HttpStatus.NOT_FOUND, "space-not-found", "The Space was not found.");
        }
        if (limit < 1 || limit > 100 || (afterRelationRef != null
                && (!afterRelationRef.startsWith(FILE_RELATION_PREFIX)
                        || afterRelationRef.length() > FILE_RELATION_PREFIX.length() + 255
                        || afterRelationRef.substring(FILE_RELATION_PREFIX.length()).isBlank()
                        || afterRelationRef.chars().anyMatch(Character::isISOControl)))) {
            throw error(HttpStatus.BAD_REQUEST, "space-query-invalid", "The relation page request is invalid.");
        }
        String owner = context.principalRef(jwt.getSubject());
        if (owner == null) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member identity is required.");
        }
        String afterFileId = afterRelationRef == null ? ""
                : afterRelationRef.substring(FILE_RELATION_PREFIX.length());
        var candidates = fileResources.activeInSpace(identity.organizationId(), spaceRef,
                owner, afterFileId, limit);
        var visible = new ArrayList<SpaceRelationshipResponse>();
        for (var candidate : candidates) {
            try {
                files.inspect(jwt, candidate.fileId());
            } catch (ApiErrorException denied) {
                if (denied.status() == HttpStatus.FORBIDDEN) {
                    return new SpaceRelationshipListResponse(java.util.List.of(), null);
                }
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "space-resource-check-unavailable",
                        "Current resource access could not be verified.");
            }
            visible.add(new SpaceRelationshipResponse(
                    FILE_RELATION_PREFIX + candidate.fileId(), "CONTAINS", "FILE", candidate.fileId()));
        }
        String next = candidates.size() == limit
                ? FILE_RELATION_PREFIX + candidates.getLast().fileId() : null;
        return new SpaceRelationshipListResponse(visible, next);
    }

    private static ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "spaces"));
    }
}
