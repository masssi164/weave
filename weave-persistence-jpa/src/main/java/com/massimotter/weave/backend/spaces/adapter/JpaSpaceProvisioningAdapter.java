package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Serializes explicit Space setup to the persisted aggregate and membership rows. */
@Repository
public class JpaSpaceProvisioningAdapter implements SpaceProvisioningPort {
    private final SpaceJpaRepository spaces;
    private final SpaceMembershipJpaRepository memberships;
    private final Clock clock;

    @Autowired
    public JpaSpaceProvisioningAdapter(
            SpaceJpaRepository spaces, SpaceMembershipJpaRepository memberships) {
        this(spaces, memberships, Clock.systemUTC());
    }

    JpaSpaceProvisioningAdapter(
            SpaceJpaRepository spaces, SpaceMembershipJpaRepository memberships, Clock clock) {
        this.spaces = spaces;
        this.memberships = memberships;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result provision(String organizationRef, String spaceRef,
            String actorAccountRef, Set<String> memberAccountRefs) {
        Map<String, String> expected = new HashMap<>();
        for (String accountRef : memberAccountRefs) {
            expected.put(accountRef, "VIEW");
        }
        expected.put(actorAccountRef, "VIEW,EDIT,ADMIN");
        SpaceId id = new SpaceId(organizationRef, spaceRef);
        var existing = spaces.findById(id);
        if (existing.isPresent()) {
            if (!existing.get().active()) {
                throw new Conflict();
            }
            Map<String, String> current = new HashMap<>();
            for (var member : memberships.findByIdOrganizationRefAndIdSpaceRef(
                    organizationRef, spaceRef)) {
                if (member.active()) {
                    current.put(member.accountRef(), member.permissionSet());
                }
            }
            if (!expected.equals(current)) {
                throw new Conflict();
            }
            return Result.UNCHANGED;
        }
        Instant now = Instant.now(clock);
        spaces.save(SpaceJpaEntity.active(organizationRef, spaceRef, now));
        for (var entry : expected.entrySet()) {
            memberships.save(SpaceMembershipJpaEntity.active(
                    organizationRef, spaceRef, entry.getKey(), entry.getValue(), now));
        }
        return Result.CREATED;
    }
}
