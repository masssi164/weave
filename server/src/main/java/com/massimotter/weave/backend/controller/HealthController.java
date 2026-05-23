package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.RequestIdFilter;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import com.massimotter.weave.backend.service.PlatformContractService;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final PlatformContractService platformContractService;

    public HealthController(
            WorkspaceCapabilityService workspaceCapabilityService,
            PlatformContractService platformContractService) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.platformContractService = platformContractService;
    }

    @GetMapping("/api/health/live")
    public HealthResponse live(HttpServletRequest request) {
        String requestId = RequestIdFilter.requestId(request);
        return new HealthResponse(
                "up",
                requestId,
                List.of(new PlatformStatusResponse.DiagnosticCheck(
                        "backend",
                        "Backend API",
                        "up",
                        "ready",
                        "The Weave backend process is running.",
                        null)),
                List.of());
    }

    @GetMapping("/api/health/ready")
    public ResponseEntity<HealthResponse> ready(HttpServletRequest request) {
        PlatformStatusResponse status = platformContractService.status(RequestIdFilter.requestId(request));
        WorkspaceCapabilityReadiness readiness = workspaceCapabilityService.snapshot().shellAccess().readiness();
        if (readiness == WorkspaceCapabilityReadiness.READY && status.ready()) {
            return ResponseEntity.ok(new HealthResponse(
                    "up",
                    status.requestId(),
                    status.checks(),
                    List.of()));
        }
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(
                        readiness == WorkspaceCapabilityReadiness.BLOCKED ? "blocked" : "degraded",
                        status.requestId(),
                        status.checks(),
                        status.actions()));
    }

    public record HealthResponse(
            String status,
            String requestId,
            List<PlatformStatusResponse.DiagnosticCheck> checks,
            List<String> actions) {
    }
}
