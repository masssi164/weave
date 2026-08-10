package com.massimotter.weave.backend.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.files.native")
public record WeaveNativeFilesProperties(
        Path filesystemRoot,
        long maximumBlobBytes,
        int reconciliationLimit) {

    public WeaveNativeFilesProperties {
        filesystemRoot = (filesystemRoot == null
                ? Path.of(".weave", "files", "blobs")
                : filesystemRoot).toAbsolutePath().normalize();
        maximumBlobBytes = maximumBlobBytes < 1 ? 64L * 1024L * 1024L : maximumBlobBytes;
        reconciliationLimit = reconciliationLimit < 1 ? 10_000 : reconciliationLimit;
    }
}
