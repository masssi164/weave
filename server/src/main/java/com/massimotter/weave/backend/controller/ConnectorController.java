package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.connector.ConnectorBoundaryResponse;
import com.massimotter.weave.backend.model.connector.ConnectorManifestValidationRequest;
import com.massimotter.weave.backend.model.connector.ConnectorManifestValidationResponse;
import com.massimotter.weave.backend.service.connector.ConnectorRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Connectors", description = "Internal connector runtime boundary; public SDK remains deferred.")
@SecurityRequirement(name = "bearer-jwt")
public class ConnectorController {

    private final ConnectorRuntimeService connectorRuntimeService;

    public ConnectorController(ConnectorRuntimeService connectorRuntimeService) {
        this.connectorRuntimeService = connectorRuntimeService;
    }

    @GetMapping("/api/connectors/boundary")
    @Operation(summary = "Get internal connector runtime boundary")
    public ConnectorBoundaryResponse boundary() {
        return connectorRuntimeService.boundary();
    }

    @PostMapping("/api/connectors/manifest/validate")
    @Operation(summary = "Validate an internal connector manifest without accepting secret values")
    public ConnectorManifestValidationResponse validate(@Valid @RequestBody ConnectorManifestValidationRequest request) {
        return connectorRuntimeService.validate(request);
    }
}
