package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.migration.MigrationApplyGateRequest;
import com.massimotter.weave.backend.model.migration.MigrationApplyGateResponse;
import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.model.migration.MigrationDryRunResponse;
import com.massimotter.weave.backend.service.migration.MigrationApplyGateService;
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
    private final MigrationApplyGateService migrationApplyGateService;

    public MigrationController(
            MigrationDryRunService migrationDryRunService,
            MigrationApplyGateService migrationApplyGateService) {
        this.migrationDryRunService = migrationDryRunService;
        this.migrationApplyGateService = migrationApplyGateService;
    }

    @PostMapping("/api/migration/dry-runs")
    @Operation(summary = "Create a replay-safe migration inventory dry-run")
    public MigrationDryRunResponse dryRun(@Valid @RequestBody MigrationDryRunRequest request) {
        return migrationDryRunService.dryRun(request);
    }

    @PostMapping("/api/migration/apply-gates")
    @Operation(summary = "Validate generic provider migration apply gates without mutating providers")
    public MigrationApplyGateResponse applyGate(@Valid @RequestBody MigrationApplyGateRequest request) {
        return migrationApplyGateService.evaluate(request);
    }
}
