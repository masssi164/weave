package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.util.List;
import java.util.Optional;
import com.massimotter.weave.backend.spaces.port.SpaceReadPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/** Reads only persisted active Space and membership state; bootstrap config is not an authority. */
@Transactional(readOnly = true)
public class JpaSpaceAccessAdapter implements SpaceAccessPort, SpaceReadPort {
    private final SpaceJpaRepository spaces;
    private final SpaceMembershipJpaRepository memberships;

    public JpaSpaceAccessAdapter(SpaceJpaRepository spaces, SpaceMembershipJpaRepository memberships) {
        this.spaces = spaces;
        this.memberships = memberships;
    }

    @Override
    public boolean allows(String organizationRef, String spaceRef, String principalRef, Permission permission) {
        if (permission == null || organizationRef == null || spaceRef == null || principalRef == null) {
            return false;
        }
        return spaces.findById(new SpaceId(organizationRef, spaceRef))
                .filter(SpaceJpaEntity::active)
                .flatMap(ignored -> memberships.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                        organizationRef, spaceRef, principalRef))
                .filter(SpaceMembershipJpaEntity::active)
                .map(member -> permits(member.permissionSet(), permission))
                .orElse(false);
    }

    @Override
    public List<String> visibleSpaceRefs(
            String organizationRef, String principalRef, String afterSpaceRef, int limit) {
        if (organizationRef == null || organizationRef.isBlank()
                || principalRef == null || principalRef.isBlank()
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Space listing requires an organization, member and limit 1..100");
        }
        return memberships.visibleSpaceRefs(
                organizationRef, principalRef, afterSpaceRef == null ? "" : afterSpaceRef,
                PageRequest.of(0, limit));
    }

    @Override
    public List<SpaceSummary> visibleSpaces(String organizationRef, String principalRef,
            String afterSpaceRef, int limit) {
        if (organizationRef == null || organizationRef.isBlank() || principalRef == null || principalRef.isBlank()
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Space listing requires an organization, member and limit 1..100");
        }
        return memberships.visibleSpaces(organizationRef, principalRef, afterSpaceRef == null ? "" : afterSpaceRef,
                PageRequest.of(0, limit));
    }

    @Override
    public Optional<SpaceSummary> visibleSpace(String organizationRef, String principalRef, String spaceRef) {
        if (organizationRef == null || principalRef == null || spaceRef == null) return Optional.empty();
        return memberships.visibleSpace(organizationRef, principalRef, spaceRef);
    }

    private static boolean permits(String permissionSet, Permission permission) {
        return switch (permissionSet) {
            case "VIEW" -> permission == Permission.VIEW;
            case "VIEW,EDIT" -> permission == Permission.VIEW || permission == Permission.EDIT;
            case "VIEW,EDIT,ADMIN" -> true;
            default -> false;
        };
    }
}
