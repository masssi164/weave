package com.massimotter.weave.backend.identity.profile.port;

import com.massimotter.weave.backend.identity.profile.domain.ProductProfileOverride;

public interface ProductProfileOverrideRepository {

    ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey);

    ProductProfileOverride saveForPrimaryIdentityKey(String primaryIdentityKey, ProductProfileOverride profile);

    default String persistencePosture() {
        return "file-json-default";
    }
}
