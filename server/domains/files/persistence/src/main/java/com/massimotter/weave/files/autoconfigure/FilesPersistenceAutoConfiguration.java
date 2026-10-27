package com.massimotter.weave.files.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepository;
import com.massimotter.weave.backend.files.adapter.JpaFilesUserResourceRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Owns only this module's mapped types and repositories; no servlet or provider construction. */
@AutoConfiguration
@Import({JpaFilesAuthorityRepository.class, JpaFilesUserResourceRepository.class})
@EntityScan(basePackages = {
        "com.massimotter.weave.backend.files.adapter"
})
@EnableJpaRepositories(basePackages = {
        "com.massimotter.weave.backend.files.adapter"
})
public class FilesPersistenceAutoConfiguration {}
