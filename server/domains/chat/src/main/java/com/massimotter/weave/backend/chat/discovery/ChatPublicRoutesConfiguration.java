package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.chat.config.ChatE2eProofSecurityConfiguration;
import com.massimotter.weave.backend.security.http.PublicHttpRoute;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Exact discovery and isolated proof entry points; authentication remains in Chat's scoped chains. */
@Configuration(proxyBeanMethods = false)
public class ChatPublicRoutesConfiguration {

    @Bean
    PublicHttpRoute matrixWellKnownPublicRoute() {
        return new PublicHttpRoute("chat", "/.well-known/matrix/client", null);
    }

    @Bean
    PublicHttpRoute matrixVersionsPublicRoute() {
        return new PublicHttpRoute("chat", "/_matrix/client/versions", HttpMethod.GET);
    }

    @Bean
    PublicHttpRoute matrixV3VersionsPublicRoute() {
        return new PublicHttpRoute("chat", "/_matrix/client/v3/versions", HttpMethod.GET);
    }

    @Bean
    PublicHttpRoute matrixV3LoginPublicRoute() {
        return new PublicHttpRoute("chat", "/_matrix/client/v3/login", HttpMethod.GET);
    }

    @Bean
    PublicHttpRoute matrixR0LoginPublicRoute() {
        return new PublicHttpRoute("chat", "/_matrix/client/r0/login", HttpMethod.GET);
    }

    @Bean
    PublicHttpRoute chatProofPublicRoute() {
        return new PublicHttpRoute("chat", ChatE2eProofSecurityConfiguration.PATH, null);
    }

}
