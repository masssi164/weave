package com.massimotter.weave.agentruntime.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Owns only this module's mapped types and repositories; no servlet or provider construction. */
@AutoConfiguration
@EntityScan(basePackages = {
        "com.massimotter.weave.backend.agentruntime.adapter"
})
@EnableJpaRepositories(basePackages = {
        "com.massimotter.weave.backend.agentruntime.adapter"
})
public class AgentRuntimePersistenceAutoConfiguration {}
