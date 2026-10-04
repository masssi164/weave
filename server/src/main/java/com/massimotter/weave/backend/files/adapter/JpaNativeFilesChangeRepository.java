package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.application.NativeFilesChangeRepository;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA read projection for native Files change pages. */
@Repository
@Transactional(readOnly = true)
public class JpaNativeFilesChangeRepository implements NativeFilesChangeRepository {

    private final FilesStreamHeadJpaRepository heads;
    private final FilesChangeJpaRepository changes;

    public JpaNativeFilesChangeRepository(
            FilesStreamHeadJpaRepository heads,
            FilesChangeJpaRepository changes) {
        this.heads = heads;
        this.changes = changes;
    }

    @Override
    public Optional<StreamHead> findHead(String organizationRef, String spaceRef) {
        return heads.findById(new FilesScopeId(organizationRef, spaceRef))
                .map(FilesStreamHeadJpaEntity::toStreamHead);
    }

    @Override
    public List<FileChange> findChanges(
            String organizationRef,
            String spaceRef,
            long afterRevision,
            long capturedHighWater,
            int limit) {
        return changes.findPage(
                        organizationRef,
                        spaceRef,
                        afterRevision,
                        capturedHighWater,
                        PageRequest.of(0, limit))
                .stream()
                .map(FilesChangeJpaEntity::toFileChange)
                .toList();
    }
}
