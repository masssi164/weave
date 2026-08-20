package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import java.util.List;
import java.util.Optional;

/** Read-only persistence boundary for one native Files change stream. */
public interface NativeFilesChangeRepository {

    Optional<StreamHead> findHead(String organizationRef, String spaceRef);

    List<FileChange> findChanges(
            String organizationRef,
            String spaceRef,
            long afterRevision,
            long capturedHighWater,
            int limit);
}
