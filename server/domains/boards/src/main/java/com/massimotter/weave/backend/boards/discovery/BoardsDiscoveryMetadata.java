package com.massimotter.weave.backend.boards.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategorySelectionPolicy;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryContract;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Concrete discovery declarations owned by boards; shared policy contains no feature catalog. */
public final class BoardsDiscoveryMetadata {
    private BoardsDiscoveryMetadata() {}

    public static ProviderCategoryFragment boardsTasksCategory() {
        return new ProviderCategoryFragment("boards",
                3,
                "boards-tasks",
                "boards/tasks",
                "boards",
                true,
                null,
                ProviderRealityLevel.RELEASE_READY,
                Set.of(ProviderModule.BOARDS),
                ProviderCategorySelectionPolicy.metadataOnly(),
                new ProviderCategoryContract(
                    List.of("boards.read", "boards.update_task", "boards.sync_workspace"),
                    List.of("openproject-primary"),
                    List.of("placeholder-boards", "jira", "microsoft-planner", "nextcloud-deck", "vikunja"),
                    List.of("Board", "List", "Task", "Status", "Assignee", "Comment", "AttachmentRef", "Dependency", "CustomField"),
                    "selected work-management provider owns workflow/status semantics unless Weave-owned task service is explicitly selected",
                    List.of("custom fields", "multi-assignee", "workflow transitions", "dependencies", "comments", "attachments", "optimistic locking"),
                    "export tasks/comments/attachments/dependencies where provider allows; archive/delete follows project retention",
                    "boards replacement requires dry-run loss report for workflow, custom fields, assignees, comments, attachments, and dependencies"));
    }

    public static CanonicalDomainContribution boardsDomain() {
        return new CanonicalDomainContribution("boards", 6, CanonicalDomainRegistry.entry(
                "boards",
                "Boards & Tasks",
                "Provider-neutral boards/tasks workflow contract for boards, lists, tasks, statuses, assignments, and workflow rules.",
                List.of("Board", "List", "Task", "Status", "Assignee", "Comment", "AttachmentRef", "Dependency", "CustomField"),
                List.of("boards-tasks"),
                CanonicalDomainRegistry.standardCapabilities("boards"),
                Map.of(
                    "openproject-primary", "release_ready",
                    "placeholder-boards", "contract_only",
                    "jira", "contract_only",
                    "microsoft-planner", "contract_only",
                    "nextcloud-deck", "live_read",
                    "vikunja", "contract_only")));
    }
}
