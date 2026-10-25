package com.massimotter.weave.backend.service.interop;

import java.util.Optional;

public interface SlackSecretResolver {
    Optional<String> resolveSigningSecret(String signingSecretRef);
}
