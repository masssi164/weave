package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.model.files.FileQuotaResponse;
import java.util.List;

public record WebDavPropfindListing(
        WebDavPropfindResource requested,
        List<WebDavPropfindResource> children,
        FileQuotaResponse quota) {
}
