package com.massimotter.weave.backend.chat.provider.synapse;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/** Validates real serving startup before background workflows; AOT graph construction stays offline. */
public final class MatrixApplicationServiceReadiness implements ApplicationRunner, Ordered {
    private final MatrixApplicationServiceSecrets secrets;

    public MatrixApplicationServiceReadiness(MatrixApplicationServiceSecrets secrets) {
        this.secrets = secrets;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        secrets.requireReady();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
