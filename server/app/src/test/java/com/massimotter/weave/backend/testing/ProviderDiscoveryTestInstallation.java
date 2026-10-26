package com.massimotter.weave.backend.testing;

import com.massimotter.weave.backend.agentruntime.discovery.AgentRuntimeDiscoveryMetadata;
import com.massimotter.weave.backend.meetings.discovery.MeetingsDiscoveryMetadata;
import com.massimotter.weave.backend.files.discovery.FilesDiscoveryMetadata;
import com.massimotter.weave.backend.boards.discovery.BoardsDiscoveryMetadata;
import com.massimotter.weave.backend.integrations.discovery.IntegrationsDiscoveryMetadata;
import com.massimotter.weave.backend.decisions.discovery.DecisionsDiscoveryMetadata;
import com.massimotter.weave.backend.office.discovery.OfficeDiscoveryMetadata;
import com.massimotter.weave.backend.chat.discovery.ChatDiscoveryMetadata;
import com.massimotter.weave.backend.calendar.discovery.CalendarDiscoveryMetadata;
import com.massimotter.weave.backend.devops.discovery.DevopsDiscoveryMetadata;
import com.massimotter.weave.backend.discovery.catalog.CoreDiscoveryMetadata;
import com.massimotter.weave.backend.manuals.discovery.ManualsDiscoveryMetadata;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.util.List;

/** Explicit full-contract fixture installation using actual owning declarations, never production fallback metadata. */
public final class ProviderDiscoveryTestInstallation {
    private ProviderDiscoveryTestInstallation() {}

    public static ProviderCategoryCatalog categories() {
        return new ProviderCategoryCatalog(List.of(
                AgentRuntimeDiscoveryMetadata.modelCategory(),
                AgentRuntimeDiscoveryMetadata.agentRuntimeControlCategory(),
                MeetingsDiscoveryMetadata.meetingsCallsCategory(),
                FilesDiscoveryMetadata.filesCategory(),
                BoardsDiscoveryMetadata.boardsTasksCategory(),
                IntegrationsDiscoveryMetadata.documentsFragment(),
                DecisionsDiscoveryMetadata.decisionsEvidenceCategory(),
                OfficeDiscoveryMetadata.documentsCollaborationCategory(),
                ChatDiscoveryMetadata.chatCategory(),
                CalendarDiscoveryMetadata.calendarCategory(),
                DevopsDiscoveryMetadata.releaseEvidenceCategory(),
                CoreDiscoveryMetadata.adminControlPlaneCategory(),
                ManualsDiscoveryMetadata.manualsHelpCategory()));
    }

    public static ProviderCapabilityContracts contracts() {
        return new ProviderCapabilityContracts(categories());
    }

    public static CanonicalDomainRegistry canonicalDomains() {
        return new CanonicalDomainRegistry(List.of(
                AgentRuntimeDiscoveryMetadata.agentRuntimeControlDomain(),
                MeetingsDiscoveryMetadata.callsDomain(),
                FilesDiscoveryMetadata.filesDomain(),
                BoardsDiscoveryMetadata.boardsDomain(),
                DecisionsDiscoveryMetadata.decisionsDomain(),
                OfficeDiscoveryMetadata.documentsDomain(),
                ChatDiscoveryMetadata.chatDomain(),
                CalendarDiscoveryMetadata.calendarDomain(),
                CoreDiscoveryMetadata.peopleDomain(),
                CoreDiscoveryMetadata.spacesDomain(),
                CoreDiscoveryMetadata.healthDomain()));
    }

    public static void stubCapabilities(WorkspaceCapabilityService service,
            com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse snapshot) {
        var statuses = new java.util.LinkedHashMap<String, com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse>();
        statuses.put("chat", snapshot.chat());
        statuses.put("files", snapshot.files());
        statuses.put("calendar", snapshot.calendar());
        statuses.put("boards", snapshot.boards());
        statuses.put("meetings", snapshot.meetingsCalls());
        statuses.put("office", snapshot.documentsCollaboration());
        statuses.put("decisions", snapshot.decisionsEvidence());
        statuses.put("manuals", snapshot.manualsHelp());
        statuses.put("devops", snapshot.releaseEvidence());
        statuses.put("agent-runtime", snapshot.agentRuntimeControl());
        statuses.values().removeIf(java.util.Objects::isNull);
        org.mockito.Mockito.when(service.snapshot()).thenReturn(snapshot);
        org.mockito.Mockito.when(service.installedSnapshots(null)).thenReturn(java.util.Map.copyOf(statuses));
        org.mockito.Mockito.when(service.adminControlPlaneSnapshot()).thenReturn(snapshot.adminControlPlane());
    }

    public static ProviderRegistry registry(List<ProviderPort> providers, WorkspaceCapabilityService capabilities,
            ProviderSelectionRepository selections) {
        var catalog = categories();
        return new ProviderRegistry(providers, capabilities, selections, catalog,
                new ProviderCapabilityContracts(catalog), canonicalDomains());
    }

    public static com.massimotter.weave.backend.admin.application.AdminCapabilityCatalog adminCapabilities() {
        return new com.massimotter.weave.backend.admin.application.AdminCapabilityCatalog(List.of(
                com.massimotter.weave.backend.admin.domain.CoreAdminMetadata.capabilities(),
                com.massimotter.weave.backend.chat.discovery.ChatAdminMetadata.capabilities(),
                com.massimotter.weave.backend.files.discovery.FilesAdminMetadata.capabilities(),
                com.massimotter.weave.backend.calendar.discovery.CalendarAdminMetadata.capabilities(),
                com.massimotter.weave.backend.boards.discovery.BoardsAdminMetadata.capabilities(),
                com.massimotter.weave.backend.meetings.discovery.MeetingsAdminMetadata.capabilities(),
                com.massimotter.weave.backend.office.discovery.OfficeAdminMetadata.capabilities(),
                com.massimotter.weave.backend.decisions.discovery.DecisionsAdminMetadata.capabilities(),
                com.massimotter.weave.backend.manuals.discovery.ManualsAdminMetadata.capabilities(),
                com.massimotter.weave.backend.devops.discovery.DevopsAdminMetadata.capabilities()));
    }

    public static com.massimotter.weave.backend.admin.application.SuiteDomainCatalog adminSuites() {
        return new com.massimotter.weave.backend.admin.application.SuiteDomainCatalog(List.of(
                com.massimotter.weave.backend.files.discovery.FilesAdminMetadata.suite(),
                com.massimotter.weave.backend.office.discovery.OfficeAdminMetadata.suite(),
                com.massimotter.weave.backend.boards.discovery.BoardsAdminMetadata.suite(),
                com.massimotter.weave.backend.calendar.discovery.CalendarAdminMetadata.suite(),
                com.massimotter.weave.backend.meetings.discovery.MeetingsAdminMetadata.suite()));
    }

    public static List<com.massimotter.weave.backend.admin.api.McpServerBindingResponse> mcpBindings() {
        return List.of(com.massimotter.weave.backend.agentruntime.discovery.AgentRuntimeAdminMetadata.mcpBinding());
    }

    public static List<com.massimotter.weave.backend.admin.domain.AdminApiLink> adminApiLinks() {
        return List.of(new com.massimotter.weave.backend.admin.domain.AdminApiLink(
                        "agentRuntimes", "/api/admin/agent-runtimes/{personRef}"),
                new com.massimotter.weave.backend.admin.domain.AdminApiLink(
                        "providerReplacementDryRun", "/api/admin/providers/replacements/dry-run"));
    }
}
