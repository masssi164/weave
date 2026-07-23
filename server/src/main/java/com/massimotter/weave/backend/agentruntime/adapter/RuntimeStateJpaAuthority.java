package com.massimotter.weave.backend.agentruntime.adapter;

import static java.util.Objects.requireNonNull;

import org.springframework.stereotype.Component;

/** Typed repository bundle kept inside the runtime-state adapter boundary. */
@Component
public final class RuntimeStateJpaAuthority {

    private final RuntimeStateHeadJpaRepository heads;
    private final RuntimeStateGenerationJpaRepository generations;
    private final RuntimeStateDeletionJpaRepository deletions;

    RuntimeStateJpaAuthority(
            RuntimeStateHeadJpaRepository heads,
            RuntimeStateGenerationJpaRepository generations,
            RuntimeStateDeletionJpaRepository deletions) {
        this.heads = requireNonNull(heads, "heads");
        this.generations = requireNonNull(generations, "generations");
        this.deletions = requireNonNull(deletions, "deletions");
    }

    RuntimeStateHeadJpaRepository heads() {
        return heads;
    }

    RuntimeStateGenerationJpaRepository generations() {
        return generations;
    }

    RuntimeStateDeletionJpaRepository deletions() {
        return deletions;
    }
}
