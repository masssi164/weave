package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RuntimeEntitlementJpaRepository
    extends JpaRepository<RuntimeEntitlementEntity, UUID> {
  Optional<RuntimeEntitlementEntity> findByEntitlementRef(String ref);

  Optional<RuntimeEntitlementEntity>
      findFirstByOrganizationRefAndPersonRefAndEntitlementStateOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
          String org, String person, String state);

  Optional<RuntimeEntitlementEntity>
      findFirstByOrganizationRefAndPersonRefOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
          String org, String person);

  Optional<RuntimeEntitlementEntity> findByOrganizationRefAndPersonRefAndEntitlementRevision(
      String org, String person, String revision);

  @Query(
      "select e from RuntimeEntitlementEntity e where e.organizationRef=:org and"
          + " e.personRef=:person and e.entitlementRevision=:revision and"
          + " e.entitlementState='ENTITLED' and e.effectiveAt<=:now and e.expiresAt>:now")
  Optional<RuntimeEntitlementEntity> findEffective(
      @Param("org") String org,
      @Param("person") String person,
      @Param("revision") String revision,
      @Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from RuntimeEntitlementEntity e where e.entitlementRef=:ref")
  Optional<RuntimeEntitlementEntity> lockByRef(@Param("ref") String ref);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RuntimeEntitlementEntity>
      findFirstByOrganizationRefAndPersonRefAndMemberIssuerAndMemberSubjectAndSourceProviderAndSourceGroupRefAndCapabilityRevisionAndEntitlementStateOrderByLastObservedAtDescCreatedAtDesc(
          String org,
          String person,
          String issuer,
          String subject,
          String provider,
          String group,
          String capability,
          String state);
}
