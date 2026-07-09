package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.model.files.FileListResponse;
import java.util.Map;

public record VersionedFileListResponse(
        FileListResponse listing,
        String requestedVersionToken,
        Map<String, String> childVersionTokens) {
}
