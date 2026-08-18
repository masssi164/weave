package com.massimotter.weave.backend.providerbinding.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.provider-bindings.bootstrap.files")
public record ProviderBindingBootstrapProperties(
        boolean enabled,
        String organizationRef,
        String adapterKey,
        String configurationRef) {

    public String requiredOrganizationRef() {
        return required(organizationRef, "organization-ref");
    }

    public String requiredAdapterKey() {
        return required(adapterKey, "adapter-key");
    }

    public String requiredConfigurationRef() {
        return required(configurationRef, "configuration-ref");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Files provider binding bootstrap requires " + field);
        }
        return value.trim();
    }
}
