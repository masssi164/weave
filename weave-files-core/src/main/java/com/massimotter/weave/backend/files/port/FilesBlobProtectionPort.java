package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import java.util.Set;

/** Supplies private blob bindings that an unfinished durable mutation still protects. */
@FunctionalInterface
public interface FilesBlobProtectionPort {

    Set<BlobReference> protectedBindings(FilesScope scope);

    static FilesBlobProtectionPort none() {
        return scope -> Set.of();
    }
}
