package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.spaces.relationship.SpaceRelationshipContribution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Shared Space admission and stable pagination over installed, domain-owned resource checks. */
@Service
public class SpaceRelationshipUserService {
    private final OrganizationIdentityContextResolver identities;
    private final DeploymentOrganizationAdmission admission;
    private final ContextAuthorizationProperties context;
    private final SpaceAccessPort spaces;
    private final List<SpaceRelationshipContribution> contributions;

    public SpaceRelationshipUserService(OrganizationIdentityContextResolver identities,
            DeploymentOrganizationAdmission admission, ContextAuthorizationProperties context,
            SpaceAccessPort spaces, List<SpaceRelationshipContribution> contributions) {
        this.identities = identities;
        this.admission = admission;
        this.context = context;
        this.spaces = spaces;
        var domains = new HashSet<String>();
        var prefixes = new HashSet<String>();
        for (var contribution : contributions) {
            if (!domains.add(contribution.domain()) || !prefixes.add(contribution.relationPrefix())) {
                throw new IllegalArgumentException("Space relationship contribution is duplicated");
            }
        }
        // Existing stable cursor prefixes preserve event → file → room ordering.
        this.contributions = contributions.stream()
                .sorted(Comparator.comparing(SpaceRelationshipContribution::relationPrefix)).toList();
    }

    public SpaceRelationshipListResponse list(Jwt jwt, String spaceRef, String afterRelationRef, int limit) {
        if (jwt == null || "weave-mcp-server".equals(jwt.getClaimAsString("azp")) || !admission.allows(jwt)) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member session is required.");
        }
        var identity = identities.resolve(jwt);
        if (identity.roles().size() != 1 || spaceRef == null || spaceRef.isBlank()
                || !spaces.allows(identity.organizationId(), spaceRef, identity.accountId(), Permission.VIEW)) {
            throw error(HttpStatus.NOT_FOUND, "space-not-found", "The Space was not found.");
        }
        SpaceRelationshipContribution cursorOwner = afterRelationRef == null ? null : cursorOwner(afterRelationRef);
        if (limit < 1 || limit > 100 || (afterRelationRef != null && cursorOwner == null)) {
            throw error(HttpStatus.BAD_REQUEST, "space-query-invalid", "The relation page request is invalid.");
        }
        String owner = context.principalRef(jwt.getClaimAsString(context.principalClaim()));
        if (owner == null) throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member identity is required.");
        var request = new SpaceRelationshipContribution.Context(jwt, identity.organizationId(), spaceRef, owner);
        var visible = new ArrayList<SpaceRelationshipResponse>();
        for (var contribution : contributions) {
            if (cursorOwner != null && contribution.relationPrefix().compareTo(cursorOwner.relationPrefix()) < 0) continue;
            String after = contribution == cursorOwner ? afterRelationRef.substring(contribution.relationPrefix().length()) : "";
            try {
                var targets = contribution.list(request, after, limit - visible.size());
                if (targets == null || targets.size() > limit - visible.size()) throw new SpaceRelationshipContribution.Unavailable();
                for (var target : targets) {
                    if (target == null || !contribution.validTargetId(target.cursorId()) || target.cursorId().compareTo(after) <= 0
                            || target.targetRef() == null || target.targetRef().isBlank()) throw new SpaceRelationshipContribution.Unavailable();
                    after = target.cursorId();
                    visible.add(new SpaceRelationshipResponse(contribution.relationPrefix() + target.cursorId(),
                            "CONTAINS", contribution.targetKind(), target.targetRef()));
                }
            } catch (RuntimeException unavailable) {
                throw resourceUnavailable();
            }
            if (visible.size() == limit) return new SpaceRelationshipListResponse(visible, visible.getLast().relationRef());
        }
        return new SpaceRelationshipListResponse(visible, null);
    }

    private SpaceRelationshipContribution cursorOwner(String value) {
        return contributions.stream().filter(contribution -> value.startsWith(contribution.relationPrefix())
                && contribution.validTargetId(value.substring(contribution.relationPrefix().length()))).findFirst().orElse(null);
    }

    private static ApiErrorException resourceUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "space-resource-check-unavailable", "Current resource access could not be verified.");
    }

    private static ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "spaces"));
    }
}
