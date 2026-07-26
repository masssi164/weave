package com.massimotter.weave.backend.persistence.jpa.security;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialJpaRepository
    extends JpaRepository<DeviceCredentialEntity, String> {
  List<DeviceCredentialEntity> findByDomainAndPrincipalRefOrderByIssuedAtAsc(
      String domain, String principalRef);
}
