package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ConfiguredContextAuthorizationAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default Context/Space authorization policy point.
 *
 * The default adapter has no memberships or relation tuples and therefore fails closed until
 * a repository- or environment-backed policy source is wired in.
 */
@Configuration
@EnableConfigurationProperties(ContextAuthorizationSeedFileProperties.class)
public class ContextAuthorizationConfiguration {

    @Bean
    ContextAuthorizationPort contextAuthorizationPort(
            ContextAuthorizationProperties properties,
            ContextAuthorizationSeedFileProperties seedFileProperties) {
        return new ConfiguredContextAuthorizationAdapter(
                ContextAuthorizationSeedFileLoader.load(
                        seedFileProperties,
                        properties.toMemberships()),
                properties.toRelationTuples(),
                properties.toGraphEdges());
    }
}
