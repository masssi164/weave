package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeActionResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeSectionResponse;
import com.massimotter.weave.backend.model.WorkspaceReleaseReadinessResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceHomeService {

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceReleaseReadinessService workspaceReleaseReadinessService;

    public WorkspaceHomeService(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceReleaseReadinessService workspaceReleaseReadinessService) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceReleaseReadinessService = workspaceReleaseReadinessService;
    }

    public WorkspaceHomeResponse snapshot() {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot();
        WorkspaceReleaseReadinessResponse releaseReadiness = workspaceReleaseReadinessService.supportSafeSnapshot();

        List<WorkspaceHomeSectionResponse> sections = List.of(
                section(
                        "recent-channels",
                        "Recent channels",
                        capabilities.chat(),
                        "Project conversations are available through Weave chat.",
                        "weave://home/channels"),
                section(
                        "open-tasks",
                        "Open tasks",
                        capabilities.boards(),
                        capabilities.boards().readiness() == WorkspaceCapabilityReadiness.READY
                                ? "Board tasks are available for accessible non-drag work."
                                : "Board writes stay gated until the backend facade, authorization, and audit path are ready.",
                        "weave://home/tasks"),
                section(
                        "upcoming-meetings",
                        "Upcoming meetings",
                        capabilities.calendar(),
                        capabilities.calendar().readiness() == WorkspaceCapabilityReadiness.READY
                                ? "Calendar-backed meeting capsules are available."
                                : "Meeting capsules stay visible but blocked until calendar and media facades are ready.",
                        "weave://home/meetings"),
                syntheticSection(
                        "recent-decisions",
                        "Recent decisions",
                        decisionReadiness(capabilities),
                        "Decision records stay backend-owned and linkable across channels, meetings, files, and tasks.",
                        "weave://home/decisions"),
                syntheticSection(
                        "workspace-health",
                        "Workspace health",
                        releaseReadiness.readiness(),
                        releaseReadiness.summary(),
                        "weave://settings/workspace"));

        List<WorkspaceHomeActionResponse> actions = actions(sections, releaseReadiness);
        WorkspaceCapabilityReadiness readiness = aggregateReadiness(sections);
        return new WorkspaceHomeResponse(
                1,
                readiness,
                summary(readiness, actions),
                sections,
                actions,
                true);
    }

    private WorkspaceHomeSectionResponse section(
            String key,
            String title,
            WorkspaceCapabilityStatusResponse capability,
            String summary,
            String productRoute) {
        WorkspaceCapabilityReadiness readiness = capability.enabled()
                ? capability.readiness()
                : WorkspaceCapabilityReadiness.UNAVAILABLE;
        return syntheticSection(key, title, readiness, summary, productRoute);
    }

    private WorkspaceHomeSectionResponse syntheticSection(
            String key,
            String title,
            WorkspaceCapabilityReadiness readiness,
            String summary,
            String productRoute) {
        return new WorkspaceHomeSectionResponse(
                key,
                title,
                readiness,
                summary,
                readiness == WorkspaceCapabilityReadiness.READY ? 1 : 0,
                true,
                productRoute);
    }

    private WorkspaceCapabilityReadiness decisionReadiness(WorkspaceCapabilitiesResponse capabilities) {
        if (capabilities.chat().readiness() == WorkspaceCapabilityReadiness.BLOCKED
                || capabilities.files().readiness() == WorkspaceCapabilityReadiness.BLOCKED) {
            return WorkspaceCapabilityReadiness.BLOCKED;
        }
        if (capabilities.chat().readiness() == WorkspaceCapabilityReadiness.READY
                && capabilities.files().readiness() == WorkspaceCapabilityReadiness.READY) {
            return WorkspaceCapabilityReadiness.DEGRADED;
        }
        return WorkspaceCapabilityReadiness.UNAVAILABLE;
    }

    private List<WorkspaceHomeActionResponse> actions(
            List<WorkspaceHomeSectionResponse> sections,
            WorkspaceReleaseReadinessResponse releaseReadiness) {
        List<WorkspaceHomeActionResponse> actions = new ArrayList<>();
        for (WorkspaceHomeSectionResponse section : sections) {
            if (section.readiness() == WorkspaceCapabilityReadiness.READY) {
                continue;
            }
            actions.add(new WorkspaceHomeActionResponse(
                    "review-" + section.key(),
                    "Review " + section.title().toLowerCase(),
                    section.productRoute(),
                    section.summary()));
        }
        for (String action : releaseReadiness.actions()) {
            actions.add(new WorkspaceHomeActionResponse(
                    "operator-action-" + actions.size(),
                    "Resolve workspace setup action",
                    "weave://settings/workspace",
                    action));
        }
        return List.copyOf(actions);
    }

    private WorkspaceCapabilityReadiness aggregateReadiness(List<WorkspaceHomeSectionResponse> sections) {
        if (sections.stream().anyMatch(section -> section.readiness() == WorkspaceCapabilityReadiness.BLOCKED)) {
            return WorkspaceCapabilityReadiness.BLOCKED;
        }
        if (sections.stream().anyMatch(section -> section.readiness() != WorkspaceCapabilityReadiness.READY)) {
            return WorkspaceCapabilityReadiness.DEGRADED;
        }
        return WorkspaceCapabilityReadiness.READY;
    }

    private String summary(WorkspaceCapabilityReadiness readiness, List<WorkspaceHomeActionResponse> actions) {
        return switch (readiness) {
            case READY -> "Weave Home is ready for the daily work loop.";
            case DEGRADED -> "Weave Home is usable, with " + actions.size() + " setup action(s) remaining.";
            case BLOCKED -> "Weave Home is blocked until required workspace setup is completed.";
            case UNAVAILABLE -> "Weave Home is unavailable for this workspace.";
        };
    }
}
