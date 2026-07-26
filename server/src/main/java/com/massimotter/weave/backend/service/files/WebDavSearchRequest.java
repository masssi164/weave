package com.massimotter.weave.backend.service.files;

/** Parsed, bounded subset of DAV:basicsearch supported by the Weave Files projection. */
public record WebDavSearchRequest(
        String scopePath,
        String query,
        int limit,
        MatchField matchField) {
    public WebDavSearchRequest {
        if (scopePath == null || scopePath.isBlank()) {
            scopePath = "/";
        }
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new IllegalArgumentException("A bounded search query is required");
        }
        query = query.trim();
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Search limit must be between 1 and 100");
        }
        matchField = matchField == null ? MatchField.DISPLAY_NAME_OR_PATH : matchField;
    }

    public enum MatchField {
        DISPLAY_NAME_OR_PATH,
        CANONICAL_ID
    }
}
