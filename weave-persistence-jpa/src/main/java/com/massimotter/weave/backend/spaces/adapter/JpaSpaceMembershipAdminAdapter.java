package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceAccessPort.Permission;
import com.massimotter.weave.backend.spaces.port.SpaceMembershipAdministrationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Applies one current member change under optimistic version fencing. */
@Repository
public class JpaSpaceMembershipAdminAdapter implements SpaceMembershipAdministrationPort {
    private static final String ADMIN_SET = "VIEW,EDIT,ADMIN";
    private final SpaceJpaRepository spaces;
    private final SpaceMembershipJpaRepository members;
    private final Clock clock;

    @Autowired
    public JpaSpaceMembershipAdminAdapter(
            SpaceJpaRepository spaces, SpaceMembershipJpaRepository members) {
        this(spaces, members, Clock.systemUTC());
    }

    JpaSpaceMembershipAdminAdapter(
            SpaceJpaRepository spaces, SpaceMembershipJpaRepository members, Clock clock) {
        this.spaces = spaces;
        this.members = members;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MemberState get(String organizationRef, String spaceRef, String actorAccountRef,
            String targetAccountRef) {
        requireAdmin(organizationRef, spaceRef, actorAccountRef);
        var member = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organizationRef, spaceRef, targetAccountRef)
                .orElseThrow(Absent::new);
        if (!member.active()) {
            throw new Revoked(etag(member));
        }
        return state(member);
    }

    @Override
    @Transactional
    public List<MemberState> list(String organizationRef, String spaceRef,
            String actorAccountRef, String afterAccountRef, int limit) {
        requireAdmin(organizationRef, spaceRef, actorAccountRef);
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("Space member page limit is invalid");
        }
        return members.currentMembers(organizationRef, spaceRef, afterAccountRef,
                PageRequest.of(0, limit)).stream().map(JpaSpaceMembershipAdminAdapter::state).toList();
    }

    @Override
    @Transactional
    public MemberState grant(String organizationRef, String spaceRef, String actorAccountRef,
            String targetAccountRef, Set<Permission> permissions,
            String ifMatch, boolean requireAbsent) {
        requireAdmin(organizationRef, spaceRef, actorAccountRef);
        String requested = canonical(permissions);
        var existing = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                organizationRef, spaceRef, targetAccountRef);
        if (requireAbsent) {
            if (existing.isPresent()) {
                throw new Stale();
            }
        } else if (existing.isEmpty()) {
            throw new Absent();
        } else if (!etag(existing.get()).equals(ifMatch)) {
            throw new Stale();
        }
        if (existing.isPresent() && existing.get().active()
                && ADMIN_SET.equals(existing.get().permissionSet())
                && !ADMIN_SET.equals(requested)) {
            requireAnotherAdministrator(organizationRef, spaceRef, targetAccountRef);
        }
        Instant now = Instant.now(clock);
        SpaceMembershipJpaEntity updated;
        if (existing.isPresent()) {
            updated = existing.get();
            updated.replacePermissions(requested, now);
        } else {
            updated = SpaceMembershipJpaEntity.active(
                    organizationRef, spaceRef, targetAccountRef, requested, now);
        }
        updated = members.saveAndFlush(updated);
        return state(updated);
    }

    @Override
    @Transactional
    public void revoke(String organizationRef, String spaceRef, String actorAccountRef,
            String targetAccountRef, String ifMatch) {
        requireAdmin(organizationRef, spaceRef, actorAccountRef);
        var existing = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                        organizationRef, spaceRef, targetAccountRef)
                .filter(SpaceMembershipJpaEntity::active)
                .orElseThrow(Absent::new);
        if (!etag(existing).equals(ifMatch)) {
            throw new Stale();
        }
        if (ADMIN_SET.equals(existing.permissionSet())) {
            requireAnotherAdministrator(organizationRef, spaceRef, targetAccountRef);
        }
        existing.revoke(Instant.now(clock));
        members.saveAndFlush(existing);
    }

    private void requireAdmin(String organizationRef, String spaceRef, String actorAccountRef) {
        if (spaces.lockById(new SpaceId(organizationRef, spaceRef))
                .filter(SpaceJpaEntity::active).isEmpty()
                || members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                        organizationRef, spaceRef, actorAccountRef)
                        .filter(SpaceMembershipJpaEntity::active)
                        .filter(member -> ADMIN_SET.equals(member.permissionSet())).isEmpty()) {
            throw new Denied();
        }
    }

    private void requireAnotherAdministrator(
            String organizationRef, String spaceRef, String targetAccountRef) {
        boolean another = members.findByIdOrganizationRefAndIdSpaceRef(
                        organizationRef, spaceRef).stream()
                .anyMatch(member -> member.active()
                        && !targetAccountRef.equals(member.accountRef())
                        && ADMIN_SET.equals(member.permissionSet()));
        if (!another) {
            throw new LastAdministrator();
        }
    }

    private static String canonical(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Space permissions are required");
        }
        Set<Permission> normalized = EnumSet.copyOf(permissions);
        if (normalized.equals(Set.of(Permission.VIEW))) {
            return "VIEW";
        }
        if (normalized.equals(Set.of(Permission.VIEW, Permission.EDIT))) {
            return "VIEW,EDIT";
        }
        if (normalized.equals(Set.of(Permission.VIEW, Permission.EDIT, Permission.ADMIN))) {
            return ADMIN_SET;
        }
        throw new IllegalArgumentException("Space permissions must be hierarchical");
    }

    private static String etag(SpaceMembershipJpaEntity member) {
        return "\"sm-" + member.version() + "\"";
    }

    private static MemberState state(SpaceMembershipJpaEntity member) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String permission : member.permissionSet().split(",")) {
            permissions.add(Permission.valueOf(permission));
        }
        return new MemberState(member.accountRef(), permissions, etag(member));
    }
}
