package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.RequestIdFilter;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import com.massimotter.weave.backend.service.LocalDependencyReadinessService;
import com.massimotter.weave.backend.service.PlatformContractService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final PlatformContractService platformContractService;
    private final LocalDependencyReadinessService localDependencyReadinessService;

    public HealthController(
            PlatformContractService platformContractService,
            LocalDependencyReadinessService localDependencyReadinessService) {
        this.platformContractService = platformContractService;
        this.localDependencyReadinessService = localDependencyReadinessService;
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
        List<PlatformStatusResponse.DiagnosticCheck> backendChecks = Stream.concat(
                        status.checks().stream()
                                .filter(check -> "backend".equals(check.key()) || "auth".equals(check.key())),
                        localDependencyReadinessService.checks().stream())
                .toList();
        List<String> actions = backendChecks.stream()
                .map(PlatformStatusResponse.DiagnosticCheck::action)
                .filter(action -> action != null && !action.isBlank())
                .distinct()
                .toList();
        boolean ready = backendChecks.stream().allMatch(check -> "ready".equals(check.readiness()));
        if (ready) {
            return ResponseEntity.ok(new HealthResponse(
                    "up",
                    status.requestId(),
                    backendChecks,
                    List.of()));
        }
        boolean blocked = backendChecks.stream().anyMatch(check -> "blocked".equals(check.readiness()));
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(
                        blocked ? "blocked" : "degraded",
                        status.requestId(),
                        backendChecks,
                        actions));
    }

    public record HealthResponse(
            String status,
            String requestId,
            List<PlatformStatusResponse.DiagnosticCheck> checks,
            List<String> actions) {
    }
}
