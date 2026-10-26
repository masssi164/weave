package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.identity.organization.persistence.JpaOrganizationBootstrapRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** Organization bootstrap authority is shared Identity persistence, including non-web entry points. */
@AutoConfiguration(after = CorePersistenceAutoConfiguration.class,
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@Import(JpaOrganizationBootstrapRepository.class)
public class CoreOrganizationPersistenceAutoConfiguration {}
