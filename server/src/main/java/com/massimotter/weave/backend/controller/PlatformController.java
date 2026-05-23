package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.RequestIdFilter;
import com.massimotter.weave.backend.model.PlatformConfigResponse;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import com.massimotter.weave.backend.service.PlatformContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    @Operation(summary = "Get public platform configuration")
    public PlatformConfigResponse config() {
        return platformContractService.config();
    }

    @GetMapping("/api/platform/status")
    @Operation(summary = "Get platform module status")
    public PlatformStatusResponse status(HttpServletRequest request) {
        return platformContractService.status(RequestIdFilter.requestId(request));
    }
}
