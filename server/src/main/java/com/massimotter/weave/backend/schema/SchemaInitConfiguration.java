package com.massimotter.weave.backend.schema;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Minimal non-web application graph for the one-shot schema initializer. */
@Configuration(proxyBeanMethods = false)
@Profile("schema-init")
@EnableAutoConfiguration
class SchemaInitConfiguration {}
