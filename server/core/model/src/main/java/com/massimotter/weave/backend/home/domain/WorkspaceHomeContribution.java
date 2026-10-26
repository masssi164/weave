package com.massimotter.weave.backend.home.domain;

import java.util.List;
import java.util.Objects;

/** Installed domain participation in the daily-work overview, including readiness without a section. */
public record WorkspaceHomeContribution(String domainId, List<Section> sections) {
    public WorkspaceHomeContribution {
        if (domainId == null || !domainId.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Home contribution requires a stable domain identity");
        }
        sections = List.copyOf(sections);
    }

    public record Section(String key, String title, String label, String productRoute, int order) {
        public Section {
            Objects.requireNonNull(key);
            Objects.requireNonNull(title);
            Objects.requireNonNull(label);
            if (productRoute == null || !productRoute.startsWith("weave://")) {
                throw new IllegalArgumentException("Home sections require a product route");
            }
        }
    }
}
