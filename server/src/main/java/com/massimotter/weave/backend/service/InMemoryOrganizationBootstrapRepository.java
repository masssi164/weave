package com.massimotter.weave.backend.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOrganizationBootstrapRepository implements OrganizationBootstrapRepository {

    private final ConcurrentHashMap<String, OrganizationBootstrapRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<OrganizationBootstrapRecord> findByOrganizationId(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(normalizeOrganizationId(organizationId)));
    }

    @Override
    public OrganizationBootstrapRecord save(OrganizationBootstrapRecord record) {
        records.put(normalizeOrganizationId(record.organizationId()), record);
        return record;
    }

    private String normalizeOrganizationId(String organizationId) {
        return organizationId.trim().toLowerCase();
    }

    public void clear() {
        records.clear();
    }
}
