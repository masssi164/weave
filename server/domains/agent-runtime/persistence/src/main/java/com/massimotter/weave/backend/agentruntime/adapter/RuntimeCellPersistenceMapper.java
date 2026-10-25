package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import java.time.Instant;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * Compile-time checked mapping between the canonical Runtime Cell and its flattened persistence
 * representation.
 *
 * <p>The JPA entity keeps persistence-only lease transition behavior. The intermediate row makes
 * every field crossing the domain boundary explicit and lets MapStruct fail the build when the
 * canonical aggregate changes without a corresponding persistence decision.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RuntimeCellPersistenceMapper {

  RuntimeCellPersistenceMapper INSTANCE =
      Mappers.getMapper(RuntimeCellPersistenceMapper.class);

  @Mapping(target = "memberIssuer", source = "memberBinding.issuer")
  @Mapping(target = "memberSubject", source = "memberBinding.subject")
  @Mapping(target = "workloadIssuer", source = "workloadBinding.issuer")
  @Mapping(target = "workloadSubject", source = "workloadBinding.subject")
  @Mapping(target = "workloadClientId", source = "workloadBinding.clientId")
  @Mapping(target = "workloadAuthenticationMethod", source = "workloadBinding.authenticationMethod")
  @Mapping(target = "workloadCredentialRef", source = "workloadBinding.credentialRef")
  RuntimeCellRow toRow(RuntimeCell domain);

  @Mapping(
      target = "memberBinding",
      expression = "java(new RuntimeMemberBinding(row.memberIssuer(), row.memberSubject()))")
  @Mapping(
      target = "workloadBinding",
      expression =
          "java(new RuntimeWorkloadBinding(row.workloadIssuer(), row.workloadSubject(), "
              + "row.workloadClientId(), row.workloadAuthenticationMethod(), "
              + "row.workloadCredentialRef()))")
  RuntimeCell toDomain(RuntimeCellRow row);

  default RuntimeCellJpaEntity toEntity(RuntimeCell domain) {
    return RuntimeCellJpaEntity.fromRow(toRow(domain));
  }

  default RuntimeCell toDomain(RuntimeCellJpaEntity entity) {
    return toDomain(entity.toRow());
  }

  record RuntimeCellRow(
      UUID recordId,
      String organizationRef,
      String personRef,
      String memberIssuer,
      String memberSubject,
      String cellRef,
      String workloadIssuer,
      String workloadSubject,
      String workloadClientId,
      RuntimeWorkloadBinding.AuthenticationMethod workloadAuthenticationMethod,
      String workloadCredentialRef,
      RuntimeEntitlementState entitlementState,
      String entitlementRevision,
      RuntimeCellState desiredState,
      RuntimeCellState observedState,
      String runtimeProfileId,
      String runtimeProfileHash,
      String workspaceRevision,
      String workspaceManifestRef,
      String runtimeStateStoreRef,
      long fencingEpoch,
      UUID leaseId,
      Instant leaseExpiresAt,
      long version,
      String auditRef,
      Instant createdAt,
      Instant updatedAt) {}
}
