package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.model.migration.MigrationDryRunResponse;
import com.massimotter.weave.backend.service.migration.MigrationDryRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Migration", description = "Replay-safe migration inventory and dry-run reporting.")
@SecurityRequirement(name = "bearer-jwt")
public class MigrationController {

    private final MigrationDryRunService migrationDryRunService;

    public MigrationController(MigrationDryRunService migrationDryRunService) {
        this.migrationDryRunService = migrationDryRunService;
    }

    @PostMapping("/api/migration/dry-runs")
    @Operation(summary = "Create a replay-safe migration inventory dry-run")
    public MigrationDryRunResponse dryRun(@Valid @RequestBody MigrationDryRunRequest request) {
        return migrationDryRunService.dryRun(request);
    }
}
