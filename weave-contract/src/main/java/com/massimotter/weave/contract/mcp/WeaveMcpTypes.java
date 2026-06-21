package com.massimotter.weave.contract.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WeaveMcpTypes {
    private WeaveMcpTypes() {}

    static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    static Map<String, Object> copyMap(Map<String, Object> value) {
        return Map.copyOf(value == null ? Map.of() : new LinkedHashMap<>(value));
    }

    static List<String> copyStrings(List<String> value) {
        return List.copyOf(value == null ? List.of() : value.stream().map(item -> text(item, "list item")).toList());
    }
}
