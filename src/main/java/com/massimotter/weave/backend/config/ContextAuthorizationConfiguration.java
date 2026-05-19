package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.InMemoryContextAuthorizationAdapter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default Context/Space authorization policy point.
 *
 * The default adapter has no memberships or relation tuples and therefore fails closed until
 * a repository- or environment-backed policy source is wired in.
 */
@Configuration
public class ContextAuthorizationConfiguration {

    @Bean
    ContextAuthorizationPort contextAuthorizationPort() {
        return new InMemoryContextAuthorizationAdapter(List.of(), List.of(), List.of());
    }
}
