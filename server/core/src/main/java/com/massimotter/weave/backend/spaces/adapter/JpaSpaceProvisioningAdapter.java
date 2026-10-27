package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import com.massimotter.weave.backend.spaces.port.SpaceDisplayName;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Serializes explicit Space setup to the persisted aggregate and membership rows. */
public class JpaSpaceProvisioningAdapter implements SpaceProvisioningPort, DefaultSpacePreparationPort {
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
    public DefaultSpacePreparationPort.Result prepare(String organizationRef, String ownerAccountRef) {
        String spaceRef = "workspace-default";
        Instant now = Instant.now(clock);
        boolean created = spaces.insertIfAbsent(organizationRef, spaceRef, SpaceDisplayName.DEFAULT_WORKSPACE, SpaceJpaEntity.utc(now)) == 1;
        var existing = spaces.lockById(new SpaceId(organizationRef, spaceRef))
                .orElseThrow(DefaultSpacePreparationPort.Conflict::new);
        if (created) {
            membersFor(organizationRef, spaceRef, Map.of(ownerAccountRef, "VIEW,EDIT,ADMIN"), now);
            return DefaultSpacePreparationPort.Result.CREATED;
        }
        boolean ownerAdmin = existing.active() && memberships
                .findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(organizationRef, spaceRef, ownerAccountRef)
                .filter(SpaceMembershipJpaEntity::active)
                .map(member -> "VIEW,EDIT,ADMIN".equals(member.permissionSet()))
                .orElse(false);
        if (!ownerAdmin) {
            throw new DefaultSpacePreparationPort.Conflict();
        }
        return DefaultSpacePreparationPort.Result.VERIFIED;
    }

    @Override
    @Transactional
    public SpaceProvisioningPort.Result provision(String organizationRef, String spaceRef,
            String displayName, String actorAccountRef, Set<String> memberAccountRefs) {
        String name = SpaceDisplayName.normalize(displayName);
        Map<String, String> expected = new HashMap<>();
        for (String accountRef : memberAccountRefs) {
            expected.put(accountRef, "VIEW");
        }
        expected.put(actorAccountRef, "VIEW,EDIT,ADMIN");
        Instant now = Instant.now(clock);
        boolean created = spaces.insertIfAbsent(organizationRef, spaceRef, name, SpaceJpaEntity.utc(now)) == 1;
        var existing = spaces.lockById(new SpaceId(organizationRef, spaceRef))
                .orElseThrow(SpaceProvisioningPort.Conflict::new);
        if (!created) {
            if (!existing.active() || !name.equals(existing.displayName())) {
                throw new SpaceProvisioningPort.Conflict();
            }
            Map<String, String> current = new HashMap<>();
            for (var member : memberships.findByIdOrganizationRefAndIdSpaceRef(organizationRef, spaceRef)) {
                if (member.active()) {
                    current.put(member.accountRef(), member.permissionSet());
                }
            }
            if (!expected.equals(current)) {
                throw new SpaceProvisioningPort.Conflict();
            }
            return SpaceProvisioningPort.Result.UNCHANGED;
        }
        membersFor(organizationRef, spaceRef, expected, now);
        return SpaceProvisioningPort.Result.CREATED;
    }

    private void membersFor(String organizationRef, String spaceRef, Map<String, String> expected, Instant now) {
        for (var entry : expected.entrySet()) {
            memberships.save(SpaceMembershipJpaEntity.active(
                    organizationRef, spaceRef, entry.getKey(), entry.getValue(), now));
        }
    }
}
