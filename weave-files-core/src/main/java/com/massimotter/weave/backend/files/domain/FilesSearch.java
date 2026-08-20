package com.massimotter.weave.backend.files.domain;

import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import java.util.List;

/** Canonical, content-free values for bounded Files search enumeration. */
public final class FilesSearch {

    public static final int MAXIMUM_CANDIDATES = 1_000;

    private FilesSearch() {
    }

    public enum ScopeDepth {
        ZERO,
        ONE,
        INFINITY
    }

    public record CandidatePage(List<VersionedFile> candidates, boolean truncated) {
        public CandidatePage {
            candidates = List.copyOf(candidates == null ? List.of() : candidates);
            if (candidates.size() > MAXIMUM_CANDIDATES) {
                throw new IllegalArgumentException("Files search candidate page exceeds the canonical bound");
            }
        }
    }
}
