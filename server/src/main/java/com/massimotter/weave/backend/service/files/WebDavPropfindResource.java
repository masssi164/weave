package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.model.files.FileItemResponse;

public record WebDavPropfindResource(
        FileItemResponse item,
        String etag) {
}
