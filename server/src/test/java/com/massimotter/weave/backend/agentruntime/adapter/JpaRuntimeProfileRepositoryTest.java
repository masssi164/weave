package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JpaRuntimeProfileRepositoryTest {
  private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");
  private static final String ISSUER = "https://auth.weave.test/realms/weave";
  private static final String ENTITLEMENT_REVISION = "sha256:" + "1".repeat(64);

  private JdbcTemplate jdbc;
  private JpaRuntimeCellRepository cells;
  private JpaRuntimeProfileRepository profiles;
  private RuntimeCell cell;

  @BeforeEach
  void setUp() {
    var database =
        com.massimotter.weave.backend.testing.JpaTestDatabase
            .migratedDataSource("arc-profile");
    jdbc = new JdbcTemplate(database);
    insertEntitlement(jdbc);
    AgentRuntimeJpaTestFactory.Context persistence = AgentRuntimeJpaTestFactory.create(database);
    cells = persistence.cells();
    profiles = persistence.profiles();
    cell = cells.insert(cell());
  }

  @Test
  void activationAtomicallyBindsAnImmutableProfileToItsCellAndWorkload() {
    SignedRuntimeProfile profile = profile("1", "key-1");

    profiles.activate(cell, profile, NOW);
    RuntimeCell bound = cells.findByCellRef(cell.cellRef()).orElseThrow();

    assertThat(bound.runtimeProfileId()).isEqualTo(profile.profileId());
    assertThat(bound.runtimeProfileHash()).isEqualTo(profile.profileHash());
    assertThat(
            profiles.findCurrentForWorkload(
                profile.profileHash(),
                ISSUER,
                cell.workloadBinding().subject(),
                cell.workloadBinding().clientId(),
                NOW.plusSeconds(1)))
        .contains(profile);
    assertThat(
            profiles.findCurrentForWorkload(
                profile.profileHash(),
                ISSUER,
                "another-workload",
                cell.workloadBinding().clientId(),
                NOW.plusSeconds(1)))
        .isEmpty();
  }

  @Test
  void overlapSignatureCanChangeWithoutChangingTheSemanticProfileBinding() {
    SignedRuntimeProfile first = profile("1", "key-1");
    SignedRuntimeProfile overlap =
        new SignedRuntimeProfile(
            "eyJraWQiOiJrZXktMiJ9",
            first.payload(),
            "B".repeat(86),
            first.profileHash(),
            first.profileId(),
            first.cellRef(),
            "key-2",
            first.issuedAt(),
            first.expiresAt());

    profiles.activate(cell, first, NOW);
    RuntimeCell bound = cells.findByCellRef(cell.cellRef()).orElseThrow();
    profiles.activate(bound, overlap, NOW.plusSeconds(1));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from weave_agent_runtime_profile_signatures where profile_hash=?",
                Integer.class,
                first.profileHash()))
        .isEqualTo(2);
    assertThat(cells.findByCellRef(cell.cellRef()).orElseThrow().runtimeProfileHash())
        .isEqualTo(first.profileHash());
    assertThat(
            profiles.findCurrentForWorkload(
                first.profileHash(),
                ISSUER,
                cell.workloadBinding().subject(),
                cell.workloadBinding().clientId(),
                NOW.plusSeconds(2)))
        .get()
        .extracting(SignedRuntimeProfile::keyId)
        .isEqualTo("key-2");
  }

  @Test
  void staleCellActivationRollsBackTheProfileAndSignatureTogether() {
    RuntimeCell stale =
        new RuntimeCell(
            cell.recordId(),
            cell.organizationRef(),
            cell.personRef(),
            cell.memberBinding(),
            cell.cellRef(),
            cell.workloadBinding(),
            cell.entitlementState(),
            cell.entitlementRevision(),
            cell.desiredState(),
            cell.observedState(),
            null,
            null,
            cell.workspaceRevision(),
            cell.workspaceManifestRef(),
            cell.runtimeStateStoreRef(),
            cell.fencingEpoch(),
            cell.leaseId(),
            cell.leaseExpiresAt(),
            99,
            cell.auditRef(),
            cell.createdAt(),
            cell.updatedAt());

    assertThatThrownBy(() -> profiles.activate(stale, profile("2", "key-1"), NOW))
        .isInstanceOf(StaleRuntimeCellException.class);
    assertThat(
            jdbc.queryForObject("select count(*) from weave_agent_runtime_profiles", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from weave_agent_runtime_profile_signatures", Integer.class))
        .isZero();
  }

  @Test
  void revocationAndExpiryRemoveTheProfileFromWorkloadResolution() {
    SignedRuntimeProfile profile = profile("1", "key-1");
    profiles.activate(cell, profile, NOW);

    assertThat(
            profiles.findCurrentForWorkload(
                profile.profileHash(),
                ISSUER,
                cell.workloadBinding().subject(),
                cell.workloadBinding().clientId(),
                profile.expiresAt()))
        .isEmpty();
    profiles.revokeCurrent(cell.cellRef(), "entitlement-revoked", NOW.plusSeconds(2));
    assertThat(
            profiles.findCurrentForWorkload(
                profile.profileHash(),
                ISSUER,
                cell.workloadBinding().subject(),
                cell.workloadBinding().clientId(),
                NOW.plusSeconds(3)))
        .isEmpty();
    assertThatThrownBy(
            () ->
                profiles.activate(
                    cells.findByCellRef(cell.cellRef()).orElseThrow(), profile, NOW.plusSeconds(3)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void expiredOrNotYetValidProfilesCannotBeActivated() {
    SignedRuntimeProfile profile = profile("1", "key-1");

    assertThatThrownBy(() -> profiles.activate(cell, profile, NOW.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> profiles.activate(cell, profile, profile.expiresAt()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void oneProfileIdOrHashCannotBeReboundToOtherSemantics() {
    SignedRuntimeProfile first = profile("1", "key-1");
    profiles.activate(cell, first, NOW);
    SignedRuntimeProfile conflict =
        new SignedRuntimeProfile(
            first.protectedHeader(),
            "eyJkaWZmZXJlbnQiOnRydWV9",
            first.signature(),
            first.profileHash(),
            first.profileId(),
            first.cellRef(),
            "key-2",
            first.issuedAt(),
            first.expiresAt());

    assertThatThrownBy(
            () ->
                profiles.activate(
                    cells.findByCellRef(cell.cellRef()).orElseThrow(),
                    conflict,
                    NOW.plusSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("other semantics");
  }

  private static RuntimeCell cell() {
    return RuntimeCell.provisioning(
        "org:example",
        "person:example",
        new RuntimeMemberBinding(ISSUER, "member-example"),
        "cell:example",
        new RuntimeWorkloadBinding(
            ISSUER,
            "service-account-weaver-cell-example",
            "weaver-cell-example",
            RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
            "credentialref://weave/runtime/example"),
        ENTITLEMENT_REVISION,
        "workspace:1",
        "webdav-manifest:workspace:1",
        "runtime-state://org/example/person/example/state/1",
        "audit:example",
        NOW);
  }

  private static void insertEntitlement(JdbcTemplate jdbc) {
    jdbc.update(
        """
        insert into weave_agent_runtime_entitlements (
          record_id, entitlement_ref, entitlement_revision, organization_ref, person_ref,
          member_issuer, member_subject, source_provider, source_group_ref, capability_revision,
          entitlement_state, effective_at, last_observed_at, expires_at, revocation_ref,
          revoked_at, audit_ref, created_at, updated_at
        ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        UUID.randomUUID(),
        "entitlement:" + "1".repeat(64),
        ENTITLEMENT_REVISION,
        "org:example",
        "person:example",
        ISSUER,
        "member-example",
        "keycloak",
        "sha256:" + "3".repeat(64),
        "sha256:" + "4".repeat(64),
        "ENTITLED",
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW.plusSeconds(3600)),
        null,
        null,
        "audit:entitlement",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private static SignedRuntimeProfile profile(String id, String keyId) {
    return new SignedRuntimeProfile(
        "eyJhbGciOiJFZERTQSJ9",
        "eyJwcm9maWxlVmVyc2lvbiI6IndlYXZlLnJ1bnRpbWUtcHJvZmlsZS92MiJ9",
        "A".repeat(86),
        "sha256:" + id.repeat(64),
        "rp_example_" + id,
        "cell:example",
        keyId,
        NOW,
        NOW.plusSeconds(900));
  }
}
