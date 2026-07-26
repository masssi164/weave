package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import java.time.Instant;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Compile-time checked mapping between the canonical Runtime Cell and its flattened JPA row.
 *
 * <p>The intermediate record makes every persisted field explicit while keeping JPA construction
 * and domain reconstruction out of the repository implementation.
 */
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RuntimeCellPersistenceMapper {

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
          "java(new RuntimeWorkloadBinding(row.workloadIssuer(), "
              + "row.workloadSubject(), row.workloadClientId(), "
              + "RuntimeWorkloadBinding.AuthenticationMethod.valueOf("
              + "row.workloadAuthenticationMethod()), "
              + "row.workloadCredentialRef()))")
  RuntimeCell toDomain(RuntimeCellRow row);

  default RuntimeCellEntity toEntity(RuntimeCell domain) {
    RuntimeCellRow row = toRow(domain);
    return new RuntimeCellEntity(
        row.recordId(),
        row.organizationRef(),
        row.personRef(),
        row.memberIssuer(),
        row.memberSubject(),
        row.cellRef(),
        row.workloadIssuer(),
        row.workloadSubject(),
        row.workloadClientId(),
        row.workloadAuthenticationMethod(),
        row.workloadCredentialRef(),
        row.entitlementState(),
        row.entitlementRevision(),
        row.desiredState(),
        row.observedState(),
        row.runtimeProfileId(),
        row.runtimeProfileHash(),
        row.workspaceRevision(),
        row.workspaceManifestRef(),
        row.runtimeStateStoreRef(),
        row.fencingEpoch(),
        row.leaseId(),
        row.leaseExpiresAt(),
        row.version(),
        row.auditRef(),
        row.createdAt(),
        row.updatedAt());
  }

  default RuntimeCell toDomain(RuntimeCellEntity entity) {
    return toDomain(
        new RuntimeCellRow(
            entity.recordId(),
            entity.organizationRef(),
            entity.personRef(),
            entity.memberIssuer(),
            entity.memberSubject(),
            entity.cellRef(),
            entity.workloadIssuer(),
            entity.workloadSubject(),
            entity.workloadClientId(),
            entity.workloadAuthenticationMethod(),
            entity.workloadCredentialRef(),
            entity.entitlementState(),
            entity.entitlementRevision(),
            entity.desiredState(),
            entity.observedState(),
            entity.runtimeProfileId(),
            entity.runtimeProfileHash(),
            entity.workspaceRevision(),
            entity.workspaceManifestRef(),
            entity.runtimeStateStoreRef(),
            entity.fencingEpoch(),
            entity.leaseId(),
            entity.leaseExpiresAt(),
            entity.version(),
            entity.auditRef(),
            entity.createdAt(),
            entity.updatedAt()));
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
      String workloadAuthenticationMethod,
      String workloadCredentialRef,
      String entitlementState,
      String entitlementRevision,
      String desiredState,
      String observedState,
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
      Instant updatedAt) {

    public RuntimeCellRow(
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
        Instant updatedAt) {
      this(
          recordId,
          organizationRef,
          personRef,
          memberIssuer,
          memberSubject,
          cellRef,
          workloadIssuer,
          workloadSubject,
          workloadClientId,
          workloadAuthenticationMethod.name(),
          workloadCredentialRef,
          entitlementState.name(),
          entitlementRevision,
          desiredState.name(),
          observedState.name(),
          runtimeProfileId,
          runtimeProfileHash,
          workspaceRevision,
          workspaceManifestRef,
          runtimeStateStoreRef,
          fencingEpoch,
          leaseId,
          leaseExpiresAt,
          version,
          auditRef,
          createdAt,
          updatedAt);
    }
  }
}
