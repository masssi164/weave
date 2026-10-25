package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.spaces.SpaceUserListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceUserResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.spaces.port.SpaceReadPort;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** User projection of durable Space authority. Membership is checked on every request. */
@Service
public class SpaceUserApiService {
    private final OrganizationIdentityContextResolver identities;
    private final SpaceReadPort spaces;

    public SpaceUserApiService(
            OrganizationIdentityContextResolver identities,
            SpaceReadPort spaces) {
        this.identities = identities;
        this.spaces = spaces;
    }

    public SpaceUserListResponse list(Jwt jwt, String afterSpaceRef, int limit) {
        Member member = member(jwt);
        if (limit < 1 || limit > 100 || (afterSpaceRef != null &&
                (afterSpaceRef.length() > 255 || afterSpaceRef.chars().anyMatch(Character::isISOControl)))) {
            throw error(HttpStatus.BAD_REQUEST, "space-query-invalid", "The Space page request is invalid.");
        }
        var refs = spaces.visibleSpaces(
                member.organizationRef(), member.principalRef(), afterSpaceRef, limit);
        List<SpaceUserResponse> values = refs.stream().map(space -> new SpaceUserResponse(space.spaceRef(), space.displayName())).toList();
        return new SpaceUserListResponse(values, refs.size() == limit ? refs.getLast().spaceRef() : null);
    }

    public SpaceUserResponse inspect(Jwt jwt, String spaceRef) {
        Member member = member(jwt);
        if (spaceRef == null || spaceRef.isBlank() || spaceRef.length() > 255
                ) {
            throw error(HttpStatus.NOT_FOUND, "space-not-found", "The Space was not found.");
        }
        return spaces.visibleSpace(member.organizationRef(), member.principalRef(), spaceRef)
                .map(space -> new SpaceUserResponse(space.spaceRef(), space.displayName()))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "space-not-found", "The Space was not found."));
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
