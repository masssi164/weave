package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuntimeCellPersistenceMapperTest {

  private final RuntimeCellPersistenceMapper mapper = RuntimeCellPersistenceMapper.INSTANCE;

  @Test
  void mapsEveryCanonicalFieldThroughTheFlattenedJpaRepresentation() {
    Instant now = Instant.parse("2026-07-26T08:00:00Z");
    RuntimeCell domain =
        RuntimeCell.provisioning(
            "organization:1",
            "person:1",
            new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "member-1"),
            "cell:1",
            new RuntimeWorkloadBinding(
                "https://auth.weave.test/realms/weave",
                "workload-1",
                "weaver-cell-1",
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                "credentialref://weave/runtime/cell-1"),
            "entitlement:1",
            "workspace:1",
            "manifest:1",
            "runtime-state://organization/1/person/1",
            "audit:1",
            now);

    RuntimeCellJpaEntity entity = mapper.toEntity(domain);
    RuntimeCell restored = mapper.toDomain(entity);

    assertThat(restored).isEqualTo(domain);
  }
}
