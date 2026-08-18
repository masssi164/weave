package com.massimotter.weave.backend.agentruntime.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuntimeCellTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");

    @Test
    void rejectsSharedOrHumanShapedWorkloadClients() {
        assertThatThrownBy(() -> new RuntimeWorkloadBinding(
                "https://auth.weave.test/realms/weave",
                "member-subject",
                "weave-mcp-server",
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                "credentialref://weave/runtime/cell"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weaver-cell");
    }

    @Test
    void requiresProfileIdAndHashAsOneBinding() {
        RuntimeCell cell = validCell();
        assertThatThrownBy(() -> new RuntimeCell(
                cell.recordId(), cell.organizationRef(), cell.personRef(), cell.memberBinding(), cell.cellRef(),
                cell.workloadBinding(), cell.entitlementState(), cell.entitlementRevision(), cell.desiredState(),
                cell.observedState(), "rp_example", null, cell.workspaceRevision(), cell.workspaceManifestRef(),
                cell.runtimeStateStoreRef(), cell.fencingEpoch(), cell.leaseId(), cell.leaseExpiresAt(),
                cell.version(), cell.auditRef(), cell.createdAt(), cell.updatedAt()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("set together");
    }

    private static RuntimeCell validCell() {
        return RuntimeCell.provisioning(
                "org:example",
                "person:example",
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "member-123"),
                "cell:example",
                new RuntimeWorkloadBinding(
                        "https://auth.weave.test/realms/weave",
                        "service-account-weaver-cell-example",
                        "weaver-cell-example",
                        RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                        "credentialref://weave/runtime/cell-example"),
                "entitlement:1",
                "workspace:1",
                "webdav-manifest:workspace:1",
                "runtime-state://org/example/person/example/state/1",
                "audit:example",
                NOW);
    }
}
