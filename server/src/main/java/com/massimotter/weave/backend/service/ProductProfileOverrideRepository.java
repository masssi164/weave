package com.massimotter.weave.backend.service;

public interface ProductProfileOverrideRepository {

    ProductProfileOverride findBySubject(String subject);

    ProductProfileOverride save(String subject, ProductProfileOverride profile);
}
