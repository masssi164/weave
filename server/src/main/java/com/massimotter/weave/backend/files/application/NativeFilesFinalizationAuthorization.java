package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.operation.domain.OperationIntent;

/** Live authorization seam rechecked while the native Files stream head is locked. */
@FunctionalInterface
public interface NativeFilesFinalizationAuthorization {

    boolean allowed(OperationIntent intent, String spaceRef);
}
