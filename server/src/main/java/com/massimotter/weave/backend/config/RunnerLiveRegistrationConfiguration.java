package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.http.RunnerLiveRegistrationService;
import com.massimotter.weave.backend.runner.http.RunnerPublicCapabilityBundleVerifier;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Composes the authenticated Runner catalog and liveness boundary. */
@Configuration(proxyBeanMethods = false)
public class RunnerLiveRegistrationConfiguration {

    @Bean
    RunnerPublicCapabilityBundleVerifier runnerPublicCapabilityBundleVerifier(
            ObjectMapper objectMapper) {
        return new RunnerPublicCapabilityBundleVerifier(objectMapper);
    }

    @Bean
    RunnerLiveRegistrationService runnerLiveRegistrationService(
            RunnerCapabilityRegistry registry,
            RunnerPublicCapabilityBundleVerifier verifier) {
        return new RunnerLiveRegistrationService(registry, verifier, Clock.systemUTC());
    }
}
