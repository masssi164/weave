package com.massimotter.weave.backend.service;

public interface ProductProfileOverrideRepository {

    ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey);

    ProductProfileOverride saveForPrimaryIdentityKey(String primaryIdentityKey, ProductProfileOverride profile);

    default String persistencePosture() {
        return "file-json-default";
    }
}
