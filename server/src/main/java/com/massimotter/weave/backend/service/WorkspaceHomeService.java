package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeActionResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeSectionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
public class WorkspaceHomeService {

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceHomeRecentActivityService recentActivityService;

    public WorkspaceHomeService(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceHomeRecentActivityService recentActivityService) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.recentActivityService = recentActivityService;
    }

    public WorkspaceHomeResponse snapshot(Jwt jwt) {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot(jwt);
        WorkspaceCapabilityReadiness readiness = memberReadiness(capabilities);
        List<WorkspaceHomeSectionResponse> sections = List.of(
                section("recent-channels", "Recent channels", "Chat", capabilityReadiness(capabilities.chat()),
                        "weave://home/channels"),
                section("open-tasks", "Open tasks", "Tasks", capabilityReadiness(capabilities.boards()),
                        "weave://home/tasks"),
                section("upcoming-meetings", "Upcoming meetings", "Calendar", capabilityReadiness(capabilities.calendar()),
                        "weave://home/meetings"),
                section("recent-decisions", "Recent decisions", "Decisions", capabilityReadiness(capabilities.decisionsEvidence()),
                        "weave://home/decisions"),
                section("workspace-health", "Workspace health", "Your workspace", readiness,
                        "weave://settings/workspace"));

        return new WorkspaceHomeResponse(
                3,
                readiness,
                memberSummary("Weave Home", readiness),
                sections,
                sections.stream()
                        .filter(section -> section.readiness() != WorkspaceCapabilityReadiness.READY)
                        .map(section -> new WorkspaceHomeActionResponse(
                                "review-" + section.key(),
                                "Review availability",
                                section.productRoute(),
                                section.summary()))
                        .toList(),
                recentActivityService.recentActivity(jwt),
                true);
    }

    private WorkspaceHomeSectionResponse section(
            String key, String title, String capability, WorkspaceCapabilityReadiness readiness, String productRoute) {
        // This projection measures capability availability, not domain item counts.
        return new WorkspaceHomeSectionResponse(
                key, title, readiness, memberSummary(capability, readiness), null, true, productRoute);
    }

    private WorkspaceCapabilityReadiness capabilityReadiness(WorkspaceCapabilityStatusResponse capability) {
        return capability.enabled() ? capability.readiness() : WorkspaceCapabilityReadiness.UNAVAILABLE;
    }

    private WorkspaceCapabilityReadiness memberReadiness(WorkspaceCapabilitiesResponse capabilities) {
        WorkspaceCapabilityReadiness shell = capabilityReadiness(capabilities.shellAccess());
        if (shell != WorkspaceCapabilityReadiness.READY) {
            return shell;
        }
        // A restricted or unavailable capability does not block the authenticated shell.
        return List.of(capabilities.chat(), capabilities.files(), capabilities.calendar(), capabilities.boards(),
                        capabilities.decisionsEvidence())
                .stream().allMatch(capability -> capabilityReadiness(capability) == WorkspaceCapabilityReadiness.READY)
                ? WorkspaceCapabilityReadiness.READY : WorkspaceCapabilityReadiness.DEGRADED;
    }

    private String memberSummary(String capability, WorkspaceCapabilityReadiness readiness) {
        return switch (readiness) {
            case READY -> capability + " is available.";
            case DEGRADED -> capability + " is partially available. Try again later or contact your organization administrator.";
            case BLOCKED -> capability + " is not available for your account. Contact your organization administrator.";
            case UNAVAILABLE -> capability + " is unavailable. Contact your organization administrator if you need access.";
        };
    }
}
