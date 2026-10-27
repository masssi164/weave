package com.massimotter.weave.backend.files.api.dav;

import com.massimotter.weave.backend.files.api.FileItemResponse;

public record WebDavMutationResult(
        FileItemResponse item,
        String etag,
        boolean created) {
}
