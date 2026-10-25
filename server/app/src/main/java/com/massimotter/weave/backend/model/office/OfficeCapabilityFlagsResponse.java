package com.massimotter.weave.backend.model.office;

public record OfficeCapabilityFlagsResponse(
        boolean view,
        boolean edit,
        boolean comment,
        boolean review,
        boolean formFill) {
}
