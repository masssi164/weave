package com.massimotter.weave.backend.spaces.adapter;

import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import com.massimotter.weave.backend.spaces.port.SpaceProvisioningPort;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultSpaceBootstrapPersistenceTest {
    @Test void atomicInsertAndRootLockRemainCompatibleWithDevelopmentH2PostgresqlMode() {
        var source = JpaTestDatabase.entityFirstDataSource("default-space-bootstrap-h2");
        var spaces = JpaTestDatabase.repository(source, SpaceJpaRepository.class);
        var members = JpaTestDatabase.repository(source, SpaceMembershipJpaRepository.class);
        var adapter = JpaTestDatabase.transactional(source, new JpaSpaceProvisioningAdapter(spaces, members));
        assertThat(adapter.prepare("org:h2-bootstrap", "owner")).isEqualTo(DefaultSpacePreparationPort.Result.CREATED);
        assertThat(adapter.prepare("org:h2-bootstrap", "owner")).isEqualTo(DefaultSpacePreparationPort.Result.VERIFIED);
        assertThat(adapter.provision("org:h2-admin", "workspace-default", "Organization workspace", "admin", Set.of("peer")))
                .isEqualTo(SpaceProvisioningPort.Result.CREATED);
        assertThat(adapter.provision("org:h2-admin", "workspace-default", "Organization workspace", "admin", Set.of("peer")))
                .isEqualTo(SpaceProvisioningPort.Result.UNCHANGED);
        assertThatThrownBy(() -> adapter.prepare("org:h2-admin", "owner"))
                .isInstanceOf(DefaultSpacePreparationPort.Conflict.class);
        assertThat(members.findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                "org:h2-admin", "workspace-default", "owner")).isEmpty();
    }
}
