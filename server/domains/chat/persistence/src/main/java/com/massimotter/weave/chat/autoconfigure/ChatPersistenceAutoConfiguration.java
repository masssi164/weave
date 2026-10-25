package com.massimotter.weave.chat.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Owns only this module's mapped types and repositories; no servlet or provider construction. */
@AutoConfiguration
@EntityScan(basePackages = {
        "com.massimotter.weave.backend.chat.store",
        "com.massimotter.weave.backend.chat.persistence"
})
@EnableJpaRepositories(basePackages = {
        "com.massimotter.weave.backend.chat.store",
        "com.massimotter.weave.backend.chat.persistence"
})
public class ChatPersistenceAutoConfiguration {}
