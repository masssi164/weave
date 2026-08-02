package com.massimotter.weave.backend.providerbinding.port;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import java.time.Instant;
import java.util.Optional;

public interface ProviderBindingRepository {

    Optional<ProviderBinding> current(String organizationRef, String domain);

    Optional<ProviderBinding> revision(String organizationRef, String domain, long revision);

    ProviderBinding activate(
            String organizationRef,
            String domain,
            long expectedRevision,
            String adapterKey,
            String configurationRef,
            Instant activatedAt);

    ProviderObjectMapping saveMapping(ProviderObjectMapping mapping);

    Optional<ProviderObjectMapping> mappingByCanonicalId(
            String organizationRef, String domain, long bindingRevision, String canonicalObjectId);

    Optional<ProviderObjectMapping> mappingByProviderRef(
            String organizationRef, String domain, long bindingRevision, String providerObjectRef);
}
