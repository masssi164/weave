package com.massimotter.weave.backend.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityAdminOperationJpaRepository
    extends JpaRepository<IdentityAdminOperationEntity, IdentityAdminOperationId> {}
