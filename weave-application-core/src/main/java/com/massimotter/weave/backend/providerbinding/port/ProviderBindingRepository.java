package com.massimotter.weave.backend.providerbinding.port;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

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

    /** Bounded mappings for an explicitly materialized scope; never browses a provider. */
    List<ProviderObjectMapping> mappedByProviderRefPrefix(String organizationRef, String domain,
            long bindingRevision, String providerRefPrefix, String afterCanonicalId, int limit);
}
