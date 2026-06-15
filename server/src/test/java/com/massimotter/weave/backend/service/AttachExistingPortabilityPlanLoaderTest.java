package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class AttachExistingPortabilityPlanLoaderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsSupportSafePlanFromPackagedContractProjection() throws Exception {
        AttachExistingPortabilityPlanLoader loader = new AttachExistingPortabilityPlanLoader(objectMapper);

        var plan = loader.load("audit:inspection:test");

        assertThat(plan.planId()).isEqualTo("attach-existing-files-portability-plan-mvp");
        assertThat(plan.contractVersion()).isEqualTo(AttachExistingPortabilityPlanLoader.CONTRACT_VERSION);
        assertThat(plan.mode()).isEqualTo("attach_existing");
        assertThat(plan.domainKey()).isEqualTo("files");
        assertThat(plan.supportSafe()).isTrue();
        assertThat(plan.destructiveActionAllowed()).isFalse();
        assertThat(plan.providerMutationPerformed()).isFalse();
        assertThat(plan.memberVisibleProviderInternals()).isFalse();
        assertThat(plan.adapterBindings()).filteredOn(binding -> binding.activeBinding()).hasSize(1);
        assertThat(plan.auditRefs()).contains("audit:inspection:test");
        assertThat(plan.capabilityMap()).extracting("canonicalCapability")
                .containsExactly("files.read", "files.share_links", "files.retention_labels");
    }

    @Test
    void packagedProjectionMatchesCheckedInPortabilityContractFixture() throws Exception {
        String packaged = new String(new ClassPathResource(AttachExistingPortabilityPlanLoader.RESOURCE_PATH).getInputStream().readAllBytes());
        String fixture = Files.readString(Path.of("..", "specs", "0006-portability-contract", "attach-existing-files-portability-plan-mvp.json"));

        assertThat(objectMapper.readTree(packaged)).isEqualTo(objectMapper.readTree(fixture));
    }

    @Test
    void rejectsProviderMutationInMalformedPlan() throws Exception {
        String malformed = """
                {
                  "planId": "attach-existing-files-portability-plan-mvp",
                  "domainKey": "files",
                  "redaction": "support_safe",
                  "mode": "attach_existing",
                  "claimBoundary": "Read-only discovery only.",
                  "adapterMapper": {
                    "capabilityMap": [{"canonicalCapability":"files.read","sourceProviderCapability":"source","targetProviderCapability":"target","memberState":"available"}],
                    "permissionImpactRef": "permission-impact:attach-existing-files:mvp",
                    "lossReportRef": "loss-report:attach-existing-files:mvp",
                    "conflictReportRef": "conflict-report:attach-existing-files:mvp",
                    "auditRefs": ["audit:plan-generated"],
                    "recommendedTarget": {"providerKey":"target","reason":"support-safe candidate"},
                    "nextSteps": {"cutover": ["review only"], "rollback": ["keep active binding"]}
                  },
                  "adapterBindings": [{"adapterKey":"source","domainKeys":["files"],"providerPosture":"existing","activeBindingStatus":"active","discoveryMode":"read_only","providerMutationPerformed":true,"memberVisibleProviderInternals":false,"auditRef":"audit:source"}],
                  "reports": {"permissionImpact": [], "loss": [], "conflicts": []},
                  "memberCapabilityStates": ["available"],
                  "adminOnlyProviderDetails": true,
                  "negativeChecks": {"noDestructiveActionInDiscoveryMode": true, "noMemberVisibleProviderInternals": true, "exactlyOneActiveBindingPerDomain": true}
                }
                """;

        AttachExistingPortabilityPlanLoader loader = new AttachExistingPortabilityPlanLoader(
                objectMapper,
                new ByteArrayResource(malformed.getBytes()));

        assertThatThrownBy(() -> loader.load("audit:inspection:test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }
}
