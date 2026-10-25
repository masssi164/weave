package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort.FilesRequestScope;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Resolves one active, versioned Files authority before a request reaches a provider. */
@Component
public final class FilesProviderResolver {

    private static final String DOMAIN = "files";

    private final ProviderBindingRepository bindings;
    private final Map<String, FilesProviderPort> adapters;

    public FilesProviderResolver(ProviderBindingRepository bindings, List<FilesProviderPort> adapters) {
        this.bindings = Objects.requireNonNull(bindings, "bindings must not be null");
        Map<String, FilesProviderPort> keyed = new HashMap<>();
        for (FilesProviderPort adapter : adapters) {
            String key = adapter.conformanceProfile().adapterKey();
            if (keyed.putIfAbsent(key, adapter) != null) {
                throw new IllegalStateException("duplicate Files adapter key: " + key);
            }
        }
        this.adapters = Map.copyOf(keyed);
    }

    public FilesProviderPort current(String organizationRef, String spaceRef) {
        ProviderBinding binding = bindings.current(organizationRef, DOMAIN)
                .filter(candidate -> candidate.state() == State.ACTIVE)
                .orElseThrow(ProviderUnavailableException::new);
        return scoped(binding, organizationRef, spaceRef);
    }

    public FilesProviderPort pinned(ProviderBinding binding, String organizationRef, String spaceRef) {
        return pinned(binding, organizationRef, spaceRef, false);
    }

    public FilesProviderPort pinned(
            ProviderBinding binding, String organizationRef, String spaceRef, boolean allowRetired) {
        Objects.requireNonNull(binding, "binding must not be null");
        ProviderBinding stored = bindings.revision(organizationRef, DOMAIN, binding.revision())
                .orElseThrow(ProviderUnavailableException::new);
        if (!stored.equals(binding) || stored.state() == State.REVOKED) {
            throw new StaleBindingException();
        }
        if (stored.state() == State.RETIRED && allowRetired) {
            return scoped(stored, organizationRef, spaceRef);
        }
        ProviderBinding current = bindings.current(organizationRef, DOMAIN)
                .filter(candidate -> candidate.state() == State.ACTIVE)
                .orElseThrow(ProviderUnavailableException::new);
        if (!current.equals(stored)) {
            throw new StaleBindingException();
        }
        return scoped(stored, organizationRef, spaceRef);
    }

    private FilesProviderPort scoped(ProviderBinding binding, String organizationRef, String spaceRef) {
        if (!DOMAIN.equals(binding.domain()) || !organizationRef.equals(binding.organizationRef())) {
            throw new ProviderUnavailableException();
        }
        FilesProviderPort adapter = adapters.get(binding.adapterKey());
        if (adapter == null || !adapter.configured()) {
            throw new ProviderUnavailableException();
        }
        return adapter.scoped(new FilesRequestScope(
                organizationRef, spaceRef, binding.revision(), binding.configurationRef()));
    }

    public static final class ProviderUnavailableException extends RuntimeException {}

    public static final class StaleBindingException extends RuntimeException {}
}
