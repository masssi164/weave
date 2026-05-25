package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.service.interop.IdempotencyKeyService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationDryRunServiceTest {

    @Test
    void dryRunIncludesSupportSafeChatAndFilesMappingEvidence() {
        var service = new MigrationDryRunService(new IdempotencyKeyService());

        var response = service.dryRun(new MigrationDryRunRequest(
                "slack",
                new MigrationDryRunRequest.SourceInventory(
                        1,
                        2,
                        5,
                        13,
                        200,
                        List.of("channels:read", "users:read", "files:read"))));

        assertThat(response.supportSafe()).isTrue();
        assertThat(response.providerDiagnosticsRedacted()).isTrue();
        assertThat(response.replaySafe()).isTrue();
        assertThat(response.domainMappings()).extracting("domain").containsExactly("chat", "files");
        assertThat(response.domainMappings().get(0).weaveDomainObject()).contains("weave:chat");
        assertThat(response.domainMappings().get(1).weaveDomainObject()).contains("weave:files");
        assertThat(response.toString())
                .doesNotContain("https://")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("token");
    }
}
