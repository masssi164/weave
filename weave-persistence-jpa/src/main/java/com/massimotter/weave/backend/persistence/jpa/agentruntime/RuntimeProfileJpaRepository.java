package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RuntimeProfileJpaRepository extends JpaRepository<RuntimeProfileEntity, String> {
  Optional<RuntimeProfileEntity> findByCellRefAndProfileHash(String cellRef, String profileHash);

  @Query(
"""
select profile from RuntimeProfileEntity profile, RuntimeCellEntity cell
 where profile.profileHash=:hash and cell.cellRef=profile.cellRef
   and cell.runtimeProfileHash=profile.profileHash and cell.runtimeProfileId=profile.profileId
   and cell.entitlementState='ENTITLED' and profile.revokedAt is null
   and profile.issuedAt<=:now and profile.expiresAt>:now
   and cell.workloadIssuer=:issuer and cell.workloadSubject=:subject and cell.workloadClientId=:client
""")
  Optional<RuntimeProfileEntity> findCurrentForWorkload(
      @Param("hash") String hash,
      @Param("issuer") String issuer,
      @Param("subject") String subject,
      @Param("client") String client,
      @Param("now") Instant now);
}
