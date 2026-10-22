package com.massimotter.weave.backend.spaces.adapter;

import static org.assertj.core.api.Assertions.*;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpaceDisplayNamesPersistenceTest {
    @Test
    void currentNamesSurviveReopenAndConflictingProvisionDoesNotRenameOrReplaceGrants() {
        var source = JpaTestDatabase.entityFirstDataSource("space-names");
        var roots = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        var members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var provision = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(roots, members));
        assertThat(provision.provision("org", "workspace-default", "Research team", "owner", Set.of("peer")))
                .isEqualTo(SpaceProvisioningPort.Result.CREATED);
        assertThat(provision.provision("org", "workspace-default", " Research team ", "owner", Set.of("peer")))
                .isEqualTo(SpaceProvisioningPort.Result.UNCHANGED);
        assertThatThrownBy(() -> provision.provision("org", "workspace-default", "Different name", "owner", Set.of("peer")))
                .isInstanceOf(SpaceProvisioningPort.Conflict.class);
        assertThat(provision.prepare("org", "owner")).isEqualTo(DefaultSpacePreparationPort.Result.VERIFIED);
        var reopened = new JpaSpaceAccessAdapter(roots, members);
        assertThat(reopened.visibleSpace("org", "peer", "workspace-default").orElseThrow().displayName())
                .isEqualTo("Research team");
        assertThat(reopened.visibleSpaces("org", "peer", null, 10)).hasSize(1);
        assertThat(reopened.visibleSpace("foreign", "peer", "workspace-default")).isEmpty();
        var grant = members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", "peer").orElseThrow();
        grant.revoke(java.time.Instant.now());
        members.saveAndFlush(grant);
        assertThat(reopened.visibleSpace("org", "peer", "workspace-default")).isEmpty();
        assertThat(reopened.visibleSpaces("org", "peer", null, 10)).isEmpty();
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef("org", "workspace-default", "owner").orElseThrow().permissionSet())
                .isEqualTo("VIEW,EDIT,ADMIN");
    }

    @Test
    void explicitDefaultBootstrapCreatesCanonicalNameAndPreservesItsIdentityOnRetry() {
        var source = JpaTestDatabase.entityFirstDataSource("default-name");
        var roots = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        var members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var provision = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(roots, members));
        assertThat(provision.prepare("org", "owner")).isEqualTo(DefaultSpacePreparationPort.Result.CREATED);
        assertThat(provision.prepare("org", "owner")).isEqualTo(DefaultSpacePreparationPort.Result.VERIFIED);
        assertThat(roots.findById(new SpaceId("org", "workspace-default")).orElseThrow().displayName())
                .isEqualTo("Organization workspace");
    }
}
