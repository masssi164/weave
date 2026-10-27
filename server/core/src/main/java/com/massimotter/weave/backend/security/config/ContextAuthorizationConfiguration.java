package com.massimotter.weave.backend.security.config;

import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import com.massimotter.weave.backend.spaces.authorization.ConfiguredContextAuthorizationAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Default Context/Space authorization policy point.
 *
 * The default adapter has no memberships or relation tuples and therefore fails closed until
 * a repository- or environment-backed policy source is wired in.
 */
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
