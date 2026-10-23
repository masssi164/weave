package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.DogfoodSpacePreparationPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Two-target absent-first change using the existing Space aggregate lock and version authority. */
@Repository
public class JpaDogfoodSpacePreparationAdapter implements DogfoodSpacePreparationPort {
    private static final String SPACE = "workspace-default";
    private final SpaceJpaRepository spaces;
    private final SpaceMembershipJpaRepository members;

    public JpaDogfoodSpacePreparationAdapter(SpaceJpaRepository spaces, SpaceMembershipJpaRepository members) {
        this.spaces = spaces;
        this.members = members;
    }

    @Override
    @Transactional(timeout = 30)
    public void verifyOwner(String organizationRef, String ownerAccountRef) {
        requireOwner(organizationRef, ownerAccountRef);
    }

    @Override
    @Transactional(timeout = 45)
    public List<Grant> grantTwo(String organizationRef, String ownerAccountRef,
            List<Target> targets, Runnable recheckIdentity, java.util.function.Consumer<List<Grant>> audit) {
        List<String> targetAccountRefs = targets == null ? null : targets.stream().map(Target::accountRef).toList();
        if (targetAccountRefs == null || targetAccountRefs.size() != 2
                || targetAccountRefs.stream().anyMatch(value -> value == null || !value.matches("acct_[0-9a-f]{32}"))
                || targetAccountRefs.get(0).equals(targetAccountRefs.get(1))
                || targetAccountRefs.contains(ownerAccountRef)) throw new Conflict();
        requireOwner(organizationRef, ownerAccountRef);
        List<SpaceMembershipJpaEntity> existing = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            String target = targetAccountRefs.get(index);
            var row = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(organizationRef, SPACE, target);
            if (row.isPresent() && (!row.get().active() || !"VIEW,EDIT".equals(row.get().permissionSet()))) {
                throw new Conflict();
            }
            String expected = targets.get(index).expectedEtag();
            if (expected != null && (row.isEmpty() || !("\"sm-" + row.get().version() + "\"").equals(expected))) {
                throw new VersionConflict();
            }
            existing.add(row.orElse(null));
        }
        recheckIdentity.run();
        List<Grant> result = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            var row = existing.get(index);
            boolean created = row == null;
            if (created) row = members.saveAndFlush(SpaceMembershipJpaEntity.active(
                    organizationRef, SPACE, targetAccountRefs.get(index), "VIEW,EDIT", Instant.now()));
            result.add(new Grant(row.accountRef(), "\"sm-" + row.version() + "\"", created));
        }
        audit.accept(List.copyOf(result));
        return List.copyOf(result);
    }

    private void requireOwner(String organizationRef, String ownerAccountRef) {
        if (spaces.lockById(new SpaceId(organizationRef, SPACE)).filter(SpaceJpaEntity::active).isEmpty()
                || members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(organizationRef, SPACE, ownerAccountRef)
                .filter(SpaceMembershipJpaEntity::active)
                .filter(row -> "VIEW,EDIT,ADMIN".equals(row.permissionSet())).isEmpty()) throw new OwnerConflict();
    }
}
