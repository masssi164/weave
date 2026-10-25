package com.massimotter.weave.backend.identity.profile.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductProfileOverrideJpaRepository
    extends JpaRepository<ProductProfileOverrideJpaEntity, String> {}
