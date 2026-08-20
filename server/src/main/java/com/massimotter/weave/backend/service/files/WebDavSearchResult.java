package com.massimotter.weave.backend.service.files;

import java.util.List;

/** Provider-neutral result of the bounded RFC 5323 Files search profile. */
public record WebDavSearchResult(
        List<WebDavPropfindResource> resources,
        boolean truncated) {
    public WebDavSearchResult {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
