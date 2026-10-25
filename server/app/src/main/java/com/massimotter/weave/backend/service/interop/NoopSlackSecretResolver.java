package com.massimotter.weave.backend.service.interop;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoopSlackSecretResolver implements SlackSecretResolver {
    @Override
    public Optional<String> resolveSigningSecret(String signingSecretRef) {
        return Optional.empty();
    }
}
