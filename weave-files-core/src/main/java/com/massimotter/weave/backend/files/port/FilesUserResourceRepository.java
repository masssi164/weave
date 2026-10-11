package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesUserResource;
import java.util.List;
import java.util.Optional;

/** Durable visibility boundary separate from private provider object mappings. */
public interface FilesUserResourceRepository {

    Optional<FilesUserResource> find(String organizationRef, String fileId);

    Optional<FilesUserResource> findActivePath(
            String organizationRef, long bindingRevision, String path);

    List<FilesUserResource> activeChildren(String organizationRef, String parentFileId);

    /** Bounded materialized identities for the owning Space, never a provider browse result. */
    List<FilesUserResource> activeInSpace(
            String organizationRef, String spaceRef, String ownerPrincipalRef,
            String afterFileId, int limit);

    FilesUserResource save(FilesUserResource resource);
}
