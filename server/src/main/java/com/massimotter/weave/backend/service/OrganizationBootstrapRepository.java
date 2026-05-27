package com.massimotter.weave.backend.service;

import java.util.Optional;

public interface OrganizationBootstrapRepository {

    Optional<OrganizationBootstrapRecord> findByOrganizationId(String organizationId);

    OrganizationBootstrapRecord save(OrganizationBootstrapRecord record);
}
