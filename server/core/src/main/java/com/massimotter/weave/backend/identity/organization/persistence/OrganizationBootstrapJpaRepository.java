package com.massimotter.weave.backend.identity.organization.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationBootstrapJpaRepository
        extends JpaRepository<OrganizationBootstrapEntity, String> {
}
