package com.massimotter.weave.backend.persistence.jpa.identity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityAdminOperationJpaRepository
    extends JpaRepository<IdentityAdminOperationEntity, IdentityAdminOperationId> {}
