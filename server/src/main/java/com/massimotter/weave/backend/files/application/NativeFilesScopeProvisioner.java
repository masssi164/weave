package com.massimotter.weave.backend.files.application;

import java.time.Instant;

/** Explicit lifecycle boundary that provisions a native Files stream before mutations are accepted. */
@FunctionalInterface
public interface NativeFilesScopeProvisioner {

    String DEFAULT_SPACE_REF = "workspace-default";

    void provisionScope(FilesScope scope, Instant provisionedAt);
}
