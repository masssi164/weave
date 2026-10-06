package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.migration.MigrationApplyGateRequest;
import com.massimotter.weave.backend.model.migration.MigrationApplyGateResponse;
import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.model.migration.MigrationDryRunResponse;
import com.massimotter.weave.backend.service.migration.MigrationApplyGateService;
import com.massimotter.weave.backend.service.migration.MigrationDryRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Migration", description = "Replay-safe migration inventory and dry-run reporting.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid Admin bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Admin token lacks organization or administration authority.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
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
    @Operation(operationId = "dryRun", summary = "Create a replay-safe migration inventory dry-run")
    @ApiResponse(responseCode = "200", description = "Replay-safe migration inventory dry-run.",
            content = @Content(schema = @Schema(implementation = MigrationDryRunResponse.class)))
    public MigrationDryRunResponse dryRun(@Valid @RequestBody MigrationDryRunRequest request) {
        return migrationDryRunService.dryRun(request);
    }

    @PostMapping("/api/migration/apply-gates")
    @Operation(operationId = "applyGate", summary = "Validate generic provider migration apply gates without mutating providers")
    @ApiResponse(responseCode = "200", description = "Provider migration apply-gate evaluation.",
            content = @Content(schema = @Schema(implementation = MigrationApplyGateResponse.class)))
    public MigrationApplyGateResponse applyGate(@Valid @RequestBody MigrationApplyGateRequest request) {
        return migrationApplyGateService.evaluate(request);
    }
}
