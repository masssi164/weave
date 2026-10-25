package com.massimotter.weave.backend.office.api;

public record OfficeCapabilityFlagsResponse(
        boolean view,
        boolean edit,
        boolean comment,
        boolean review,
        boolean formFill) {
}
