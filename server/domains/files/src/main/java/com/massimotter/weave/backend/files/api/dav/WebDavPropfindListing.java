package com.massimotter.weave.backend.files.api.dav;

import com.massimotter.weave.backend.files.api.FileQuotaResponse;
import java.util.List;

public record WebDavPropfindListing(
        WebDavPropfindResource requested,
        List<WebDavPropfindResource> children,
        FileQuotaResponse quota) {
}
