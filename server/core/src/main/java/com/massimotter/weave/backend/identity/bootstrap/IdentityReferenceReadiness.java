package com.massimotter.weave.backend.identity.bootstrap;

import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/** Validate mounted identity references before serving startup workflows can make side effects. */
public final class IdentityReferenceReadiness implements ApplicationRunner, Ordered {
    private final IdentityOpaqueReferenceCodec references;

    public IdentityReferenceReadiness(IdentityOpaqueReferenceCodec references) {
        this.references = references;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        references.requireReady();
    }
}
