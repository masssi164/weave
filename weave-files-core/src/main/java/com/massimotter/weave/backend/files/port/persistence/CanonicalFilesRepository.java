package com.massimotter.weave.backend.files.port.persistence;

import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;

import com.massimotter.weave.backend.files.domain.CanonicalFile;
import java.util.List;
import java.util.Optional;

/** Persistence port for canonical Files metadata. */
public interface CanonicalFilesRepository {

    Optional<CanonicalFile> find(Scope scope, ObjectId objectId);

    Optional<CanonicalFile> findChild(Scope scope, ObjectId parentId, String name);

    List<CanonicalFile> listChildren(Scope scope, ObjectId parentId);

    Revision nextRevision(Scope scope);

    void insert(Scope scope, CanonicalFile file);

    void replace(Scope scope, Revision expectedRevision, CanonicalFile replacement);
}
