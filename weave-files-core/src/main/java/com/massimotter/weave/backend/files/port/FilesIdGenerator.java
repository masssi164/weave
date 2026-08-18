package com.massimotter.weave.backend.files.port;

import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;

/** Generates provider-independent canonical Files object identities. */
public interface FilesIdGenerator {

    ObjectId nextId();
}
