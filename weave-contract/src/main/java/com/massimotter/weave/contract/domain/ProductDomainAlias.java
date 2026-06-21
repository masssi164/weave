package com.massimotter.weave.contract.domain;

import java.util.List;

public record ProductDomainAlias(
        String key,
        String label,
        List<String> productDomainKeys,
        AliasKind kind,
        String note) {

    public ProductDomainAlias {
        key = text(key, "key");
        label = text(label, "label");
        productDomainKeys = List.copyOf(productDomainKeys == null ? List.of() : productDomainKeys);
        if (productDomainKeys.isEmpty()) throw new IllegalArgumentException("productDomainKeys must not be empty");
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        note = text(note, "note");
    }

    public enum AliasKind { AGGREGATION_ALIAS }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
