package com.massimotter.weave.backend.schema.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaAuthorityJpaRepository
    extends JpaRepository<SchemaAuthorityJpaEntity, String> {}
