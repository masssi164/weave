package com.massimotter.weave.backend.model.migration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MigrationDryRunRequest(
        @NotBlank @Size(max = 32) String sourceProvider,
        @Valid @NotNull SourceInventory inventory) {

    public record SourceInventory(
            @Min(0) int workspaces,
            @Min(0) int channels,
            @Min(0) int users,
            @Min(0) int files,
            @Min(0) int messages,
            List<@Size(max = 128) String> scopes) {
    }
}
