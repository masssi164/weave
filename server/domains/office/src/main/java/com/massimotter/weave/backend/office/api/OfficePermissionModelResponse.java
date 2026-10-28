package com.massimotter.weave.backend.office.api;

public record OfficePermissionModelResponse(
        boolean canView,
        boolean canEdit,
        boolean canComment,
        boolean canReview,
        boolean canFillForms,
        String reason) {
}
