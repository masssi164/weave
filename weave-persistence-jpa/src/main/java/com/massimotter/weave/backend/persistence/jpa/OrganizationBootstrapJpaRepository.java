package com.massimotter.weave.backend.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationBootstrapJpaRepository
        extends JpaRepository<OrganizationBootstrapEntity, String> {
}
