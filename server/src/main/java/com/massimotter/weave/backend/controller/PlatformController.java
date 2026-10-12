package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.RequestIdFilter;
import com.massimotter.weave.backend.model.PlatformConfigResponse;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import com.massimotter.weave.backend.service.PlatformContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Platform", description = "Public platform configuration and module status endpoints.")
public class PlatformController {

    private final PlatformContractService platformContractService;

    public PlatformController(PlatformContractService platformContractService) {
        this.platformContractService = platformContractService;
    }

    @GetMapping("/api/platform/config")
    @Operation(operationId = "config", summary = "Get public platform configuration")
    @ApiResponse(responseCode = "200", description = "Public platform configuration.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PlatformConfigResponse.class)))
    public PlatformConfigResponse config() {
        return platformContractService.config();
    }

    @GetMapping("/api/platform/status")
    @Operation(operationId = "getPlatformStatus", summary = "Get platform module status")
    @ApiResponse(responseCode = "200", description = "Public platform module status.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PlatformStatusResponse.class)))
    public PlatformStatusResponse status(HttpServletRequest request) {
        return platformContractService.status(RequestIdFilter.requestId(request));
    }
}
