package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.files.adapter.FilesVolumeAuthorityJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Minimal non-web application graph for the one-shot schema initializer. */
@Configuration(proxyBeanMethods = false)
@Profile("schema-init")
@EnableAutoConfiguration
@EntityScan("com.massimotter.weave.backend")
@EnableJpaRepositories(
    basePackageClasses = {
      SchemaAuthorityJpaRepository.class,
      FilesVolumeAuthorityJpaRepository.class
    })
class SchemaInitConfiguration {}
