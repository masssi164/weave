package com.massimotter.weave.backend.persistence.jpa.schema;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaAuthorityJpaRepository
    extends JpaRepository<SchemaAuthorityJpaEntity, String> {}
