package com.massimotter.weave.backend.domainfacade;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Canonical collection skeleton for a Weave domain facade. Provider adapters fill items later.")
public record CanonicalDomainItems(
        CanonicalDomainReadiness readiness,
        List<CanonicalDomainItem> items) {
    public CanonicalDomainItems {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
