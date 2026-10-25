package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.model.files.FileItemResponse;

public record WebDavMutationResult(
        FileItemResponse item,
        String etag,
        boolean created) {
}
