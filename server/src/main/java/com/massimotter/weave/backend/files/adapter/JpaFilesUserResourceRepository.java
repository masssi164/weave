package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.files.port.FilesUserResourceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaFilesUserResourceRepository implements FilesUserResourceRepository {
    private final FilesUserResourceJpaRepository records;

    public JpaFilesUserResourceRepository(FilesUserResourceJpaRepository records) {
        this.records = records;
    }

    @Override public Optional<FilesUserResource> find(String organizationRef, String fileId) {
        return records.findById(new FilesUserResourceId(organizationRef, fileId))
                .map(FilesUserResourceJpaEntity::toDomain);
    }

    @Override public Optional<FilesUserResource> findActivePath(
            String organizationRef, long bindingRevision, String path) {
        return records.findByIdOrganizationRefAndBindingRevisionAndActivePathKey(
                        organizationRef, bindingRevision, path)
                .map(FilesUserResourceJpaEntity::toDomain);
    }

    @Override public List<FilesUserResource> activeChildren(String organizationRef, String parentFileId) {
        return records.findByIdOrganizationRefAndParentFileIdAndStateOrderByPath(
                        organizationRef, parentFileId, FilesUserResource.State.ACTIVE)
                .stream().map(FilesUserResourceJpaEntity::toDomain).toList();
    }

    @Override public List<FilesUserResource> activeInSpace(
            String organizationRef, String spaceRef, String ownerPrincipalRef,
            String afterFileId, int limit) {
        if (organizationRef == null || organizationRef.isBlank() || spaceRef == null || spaceRef.isBlank()
                || ownerPrincipalRef == null || ownerPrincipalRef.isBlank()
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Space Files projection requires organization, Space and limit 1..100");
        }
        return records.activeInSpace(organizationRef, spaceRef, ownerPrincipalRef,
                FilesUserResource.State.ACTIVE,
                afterFileId == null ? "" : afterFileId, PageRequest.of(0, limit))
                .stream().map(FilesUserResourceJpaEntity::toDomain).toList();
    }

    @Override @Transactional public FilesUserResource save(FilesUserResource resource) {
        FilesUserResourceId id = new FilesUserResourceId(resource.organizationRef(), resource.fileId());
        FilesUserResourceJpaEntity entity = records.findById(id)
                .orElseGet(() -> FilesUserResourceJpaEntity.create(id));
        entity.observe(resource);
        return records.saveAndFlush(entity).toDomain();
    }
}
