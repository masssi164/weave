package com.massimotter.weave.backend.model.office;

public record OfficePermissionModelResponse(
        boolean canView,
        boolean canEdit,
        boolean canComment,
        boolean canReview,
        boolean canFillForms,
        String reason) {
}
