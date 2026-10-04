package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.NextcloudFilesProperties;
import java.util.Optional;

/** Resolves a private Nextcloud account by both organization and binding configuration reference. */
public interface NextcloudFilesAccountResolver {

    Optional<NextcloudFilesProperties> resolve(String organizationRef, String configurationRef);

    boolean available();
}
