package com.massimotter.weave.backend.domainfacade;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Provider-neutral canonical item skeleton emitted by a Weave domain facade.")
public record CanonicalDomainItem(
        String itemId,
        String kind,
        String title,
        CanonicalMemberState state,
        String memberImpact,
        Instant updatedAt,
        List<String> capabilityHints,
        Map<String, Object> annotations) {

    public CanonicalDomainItem {
        capabilityHints = capabilityHints == null ? List.of() : List.copyOf(capabilityHints);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
