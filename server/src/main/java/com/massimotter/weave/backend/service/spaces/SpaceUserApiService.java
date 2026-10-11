package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.spaces.SpaceUserListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceUserResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** User projection of durable Space authority. Membership is checked on every request. */
@Service
public class SpaceUserApiService {
    private final OrganizationIdentityContextResolver identities;
    private final SpaceAccessPort spaces;

    public SpaceUserApiService(
            OrganizationIdentityContextResolver identities,
            SpaceAccessPort spaces) {
        this.identities = identities;
        this.spaces = spaces;
    }

    public SpaceUserListResponse list(Jwt jwt, String afterSpaceRef, int limit) {
        Member member = member(jwt);
        if (limit < 1 || limit > 100 || (afterSpaceRef != null &&
                (afterSpaceRef.length() > 255 || afterSpaceRef.chars().anyMatch(Character::isISOControl)))) {
            throw error(HttpStatus.BAD_REQUEST, "space-query-invalid", "The Space page request is invalid.");
        }
        List<String> refs = spaces.visibleSpaceRefs(
                member.organizationRef(), member.principalRef(), afterSpaceRef, limit);
        List<SpaceUserResponse> values = refs.stream().map(SpaceUserResponse::new).toList();
        return new SpaceUserListResponse(values, refs.size() == limit ? refs.getLast() : null);
    }

    public SpaceUserResponse inspect(Jwt jwt, String spaceRef) {
        Member member = member(jwt);
        if (spaceRef == null || spaceRef.isBlank() || spaceRef.length() > 255
                || !spaces.allows(member.organizationRef(), spaceRef, member.principalRef(), Permission.VIEW)) {
            throw error(HttpStatus.NOT_FOUND, "space-not-found", "The Space was not found.");
        }
        return new SpaceUserResponse(spaceRef);
    }

    private Member member(Jwt jwt) {
        if (jwt == null || "weave-mcp-server".equals(jwt.getClaimAsString("azp"))) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A member session is required.");
        }
        var identity = identities.resolve(jwt);
        if (identity.roles().size() != 1) {
            throw error(HttpStatus.FORBIDDEN, "space-member-required", "A single organization member role is required.");
        }
        return new Member(identity.organizationId(), identity.accountId());
    }

    private ApiErrorException error(HttpStatus status, String code, String message) {
        return new ApiErrorException(status, code, message, Map.of("module", "spaces"));
    }

    private record Member(String organizationRef, String principalRef) {}
}
