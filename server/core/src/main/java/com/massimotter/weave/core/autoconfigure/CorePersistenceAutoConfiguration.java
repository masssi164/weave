package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.identity.invitation.persistence.JpaProvisioningIntentRepository;

import com.massimotter.weave.backend.security.persistence.JpaMemberSessionStateStore;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Owns only this module's mapped types and repositories; no servlet or provider construction. */
@AutoConfiguration
@Import({JpaMemberSessionStateStore.class,
        JpaProvisioningIntentRepository.class})
@EntityScan(basePackages = {
        "com.massimotter.weave.backend.identity",
        "com.massimotter.weave.backend.audit.persistence",
        "com.massimotter.weave.backend.security.persistence",
        "com.massimotter.weave.backend.schema.persistence",
        "com.massimotter.weave.backend.providerbinding.persistence",
        "com.massimotter.weave.backend.providerbinding.adapter",
        "com.massimotter.weave.backend.operation.adapter",
        "com.massimotter.weave.backend.spaces.adapter"
})
@EnableJpaRepositories(basePackages = {
        "com.massimotter.weave.backend.identity",
        "com.massimotter.weave.backend.audit.persistence",
        "com.massimotter.weave.backend.security.persistence",
        "com.massimotter.weave.backend.schema.persistence",
        "com.massimotter.weave.backend.providerbinding.persistence",
        "com.massimotter.weave.backend.providerbinding.adapter",
        "com.massimotter.weave.backend.operation.adapter",
        "com.massimotter.weave.backend.spaces.adapter"
})
public class CorePersistenceAutoConfiguration {}
