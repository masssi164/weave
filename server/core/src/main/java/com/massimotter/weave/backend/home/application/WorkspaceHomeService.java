package com.massimotter.weave.backend.home.application;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.home.api.WorkspaceHomeActionResponse;
import com.massimotter.weave.backend.home.api.WorkspaceHomeRecentActivityResponse;
import com.massimotter.weave.backend.home.api.WorkspaceHomeResponse;
import com.massimotter.weave.backend.home.api.WorkspaceHomeSectionResponse;
import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import com.massimotter.weave.backend.home.port.WorkspaceHomeActivitySource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** Shared daily-work composition; feature sections and authorized activity belong to installed domains. */
public class WorkspaceHomeService {
    private final WorkspaceCapabilityService capabilities;
    private final List<WorkspaceHomeContribution> contributions;
    private final List<WorkspaceHomeActivitySource> activitySources;

    public WorkspaceHomeService(WorkspaceCapabilityService capabilities,
            List<WorkspaceHomeContribution> contributions, List<WorkspaceHomeActivitySource> activitySources) {
        this.capabilities = java.util.Objects.requireNonNull(capabilities);
        this.contributions = List.copyOf(contributions);
        this.activitySources = List.copyOf(activitySources);
        var owners = new HashSet<String>();
        var sections = new HashSet<>(List.of("workspace-health"));
        for (var contribution : contributions) {
            if (!owners.add(contribution.domainId())) {
                throw new IllegalArgumentException("Duplicate Home owner: " + contribution.domainId());
            }
            for (var section : contribution.sections()) {
                if (!sections.add(section.key())) {
                    throw new IllegalArgumentException("Duplicate Home section: " + section.key());
                }
            }
        }
        var sourceOwners = new HashSet<String>();
        for (var source : activitySources) {
            if (!owners.contains(source.domainId()) || !sourceOwners.add(source.domainId())) {
                throw new IllegalArgumentException("Missing or duplicate Home activity owner: " + source.domainId());
            }
        }
    }

    public WorkspaceHomeResponse snapshot(Jwt jwt) {
        var installed = capabilities.installedSnapshots(jwt);
        var shell = readiness(capabilities.snapshot(jwt).shellAccess());
        var domainStates = contributions.stream().map(contribution -> {
            var state = installed.get(contribution.domainId());
            if (state == null) {
                throw new IllegalStateException("Home owner requires installed capability: " + contribution.domainId());
            }
            return state;
        }).toList();
        var readiness = shell != WorkspaceCapabilityReadiness.READY ? shell
                : domainStates.stream().allMatch(state -> readiness(state) == WorkspaceCapabilityReadiness.READY)
                        ? WorkspaceCapabilityReadiness.READY : WorkspaceCapabilityReadiness.DEGRADED;
        var sections = new ArrayList<>(contributions.stream()
                .flatMap(contribution -> contribution.sections().stream().map(definition ->
                        new OrderedSection(definition, installed.get(contribution.domainId()))))
                .sorted(Comparator.comparingInt((OrderedSection section) -> section.definition().order())
                        .thenComparing(section -> section.definition().key()))
                .map(section -> section(section.definition().key(), section.definition().title(),
                        section.definition().label(), readiness(section.state()), section.definition().productRoute()))
                .toList());
        sections.add(section("workspace-health", "Workspace health", "Your workspace", readiness,
                "weave://settings/workspace"));
        return new WorkspaceHomeResponse(3, readiness, memberSummary("Weave Home", readiness), sections,
                sections.stream().filter(section -> section.readiness() != WorkspaceCapabilityReadiness.READY)
                        .map(section -> new WorkspaceHomeActionResponse("review-" + section.key(),
                                "Review availability", section.productRoute(), section.summary())).toList(),
                activitySources.stream().flatMap(source -> source.recentActivity(jwt).stream())
                        .sorted(Comparator.comparing(WorkspaceHomeRecentActivityResponse::occurredAt).reversed())
                        .limit(20).toList(), true);
    }

    private WorkspaceHomeSectionResponse section(String key, String title, String label,
            WorkspaceCapabilityReadiness readiness, String route) {
        return new WorkspaceHomeSectionResponse(key, title, readiness, memberSummary(label, readiness), null, true, route);
    }

    private WorkspaceCapabilityReadiness readiness(WorkspaceCapabilityStatusResponse capability) {
        return capability.enabled() ? capability.readiness() : WorkspaceCapabilityReadiness.UNAVAILABLE;
    }

    private String memberSummary(String capability, WorkspaceCapabilityReadiness readiness) {
        return switch (readiness) {
            case READY -> capability + " is available.";
            case DEGRADED -> capability + " is partially available. Try again later or contact your organization administrator.";
            case BLOCKED -> capability + " is not available for your account. Contact your organization administrator.";
            case UNAVAILABLE -> capability + " is unavailable. Contact your organization administrator if you need access.";
        };
    }

    private record OrderedSection(WorkspaceHomeContribution.Section definition, WorkspaceCapabilityStatusResponse state) {}
}
