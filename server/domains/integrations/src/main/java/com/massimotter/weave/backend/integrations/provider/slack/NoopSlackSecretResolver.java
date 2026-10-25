package com.massimotter.weave.backend.integrations.provider.slack;

import com.massimotter.weave.backend.integrations.port.SlackSecretResolver;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoopSlackSecretResolver implements SlackSecretResolver {
    @Override
    public Optional<String> resolveSigningSecret(String signingSecretRef) {
        return Optional.empty();
    }
}
