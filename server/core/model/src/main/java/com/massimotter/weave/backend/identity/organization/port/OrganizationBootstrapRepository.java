package com.massimotter.weave.backend.identity.organization.port;

import com.massimotter.weave.backend.identity.organization.domain.OrganizationBootstrapRecord;

import java.util.Optional;

public interface OrganizationBootstrapRepository {

    Optional<OrganizationBootstrapRecord> findByOrganizationId(String organizationId);

    OrganizationBootstrapRecord save(OrganizationBootstrapRecord record);
}
