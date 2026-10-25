package com.massimotter.weave.backend.integrations.port;

import java.util.Optional;

public interface SlackSecretResolver {
    Optional<String> resolveSigningSecret(String signingSecretRef);
}
