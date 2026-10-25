package com.massimotter.weave.backend.agentruntime.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/** Validates installed secret mounts at serving startup, after metadata/AOT graph construction. */
final class AgentRuntimeSecretReadiness implements ApplicationRunner, Ordered {
    private final Runnable check;

    AgentRuntimeSecretReadiness(Runnable check) {
        this.check = check;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        check.run();
    }
}
