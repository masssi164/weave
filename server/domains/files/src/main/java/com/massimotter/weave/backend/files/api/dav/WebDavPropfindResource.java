package com.massimotter.weave.backend.files.api.dav;

import com.massimotter.weave.backend.files.api.FileItemResponse;

public record WebDavPropfindResource(
        FileItemResponse item,
        String etag) {
}
