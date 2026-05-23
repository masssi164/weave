package com.massimotter.weave.backend.boards.domain;

import java.util.Set;

import static java.util.Objects.requireNonNull;

public record BoardProviderCapabilities(
        ProviderKind provider,
        boolean enabled,
        Set<BoardCapability> supported,
        Set<BoardCapability> unsupported,
        String supportSafeSummary) {

    public BoardProviderCapabilities {
        provider = requireNonNull(provider, "provider must not be null");
        supported = BoardsContract.immutableSet(supported, "supported");
        unsupported = BoardsContract.immutableSet(unsupported, "unsupported");
        if (!supported.stream().filter(unsupported::contains).toList().isEmpty()) {
            throw new IllegalArgumentException("supported and unsupported capabilities must not overlap");
        }
        supportSafeSummary = BoardsContract.requireText(supportSafeSummary, "supportSafeSummary");
    }

    public boolean supports(BoardCapability capability) {
        return supported.contains(capability);
    }
}
