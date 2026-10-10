package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reads only persisted active Space and membership state; bootstrap config is not an authority. */
@Repository
@Transactional(readOnly = true)
public class JpaSpaceAccessAdapter implements SpaceAccessPort {
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

    private static boolean permits(String permissionSet, Permission permission) {
        return switch (permissionSet) {
            case "VIEW" -> permission == Permission.VIEW;
            case "VIEW,EDIT" -> permission == Permission.VIEW || permission == Permission.EDIT;
            case "VIEW,EDIT,ADMIN" -> true;
            default -> false;
        };
    }
}
