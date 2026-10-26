package com.massimotter.weave.backend.admin.domain;

/** An installed owner's actual Admin endpoint, projected without importing its handler. */
public record AdminApiLink(String key, String path) {
    public AdminApiLink {
        if (key == null || key.isBlank() || path == null || !path.startsWith("/api/admin/")) {
            throw new IllegalArgumentException("Admin links require a key and protected Admin path");
        }
    }
}
