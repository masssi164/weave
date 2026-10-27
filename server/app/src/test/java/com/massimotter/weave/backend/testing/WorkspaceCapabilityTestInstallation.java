package com.massimotter.weave.backend.testing;

import com.massimotter.weave.backend.agentruntime.capability.AgentRuntimeWorkspaceCapabilities;
import com.massimotter.weave.backend.agentruntime.capability.AgentRuntimeWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.agentruntime.capability.AgentRuntimeWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.boards.capability.BoardsWorkspaceCapabilities;
import com.massimotter.weave.backend.boards.capability.BoardsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.boards.capability.BoardsWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilities;
import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.calendar.capability.CalendarWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.chat.capability.ChatWorkspaceCapabilities;
import com.massimotter.weave.backend.chat.capability.ChatWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.chat.capability.ChatWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.decisions.capability.DecisionsWorkspaceCapabilities;
import com.massimotter.weave.backend.decisions.capability.DecisionsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.decisions.capability.DecisionsWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.devops.capability.DevopsWorkspaceCapabilities;
import com.massimotter.weave.backend.devops.capability.DevopsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.devops.capability.DevopsWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.files.capability.FilesWorkspaceCapabilities;
import com.massimotter.weave.backend.files.capability.FilesWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.files.capability.FilesWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.meetings.capability.MeetingsWorkspaceCapabilities;
import com.massimotter.weave.backend.meetings.capability.MeetingsWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.meetings.capability.MeetingsWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.office.capability.OfficeWorkspaceCapabilities;
import com.massimotter.weave.backend.office.capability.OfficeWorkspaceCapabilityConfiguration;
import com.massimotter.weave.backend.office.capability.OfficeWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Explicit full installation for tests that exercise the accepted full release policy and wire shape. */
public final class WorkspaceCapabilityTestInstallation {

    private WorkspaceCapabilityTestInstallation() {}

    @ConfigurationProperties(prefix = "weave.workspace")
    public record Settings(
            Capability shellAccess,
            Capability chat,
            Capability files,
            Capability calendar,
            Capability boards,
            Capability meetingsCalls,
            Capability documentsCollaboration,
            Capability decisionsEvidence,
            Capability manualsHelp,
            Capability releaseEvidence,
            Capability adminControlPlane,
            Capability agentRuntimeControl) {

        public Settings(
                Capability shellAccess,
                Capability chat,
                Capability files,
                Capability calendar,
                Capability boards,
                Capability agentRuntimeControl) {
            this(
                    shellAccess,
                    chat,
                    files,
                    calendar,
                    boards,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    agentRuntimeControl);
        }


        @ConstructorBinding
        public Settings(
                Capability shellAccess,
                Capability chat,
                Capability files,
                Capability calendar,
                Capability boards,
                Capability meetingsCalls,
                Capability documentsCollaboration,
                Capability decisionsEvidence,
                Capability manualsHelp,
                Capability releaseEvidence,
                Capability adminControlPlane,
                Capability agentRuntimeControl) {
            this.shellAccess = defaultCapability(shellAccess, true, null, null);
            this.chat = defaultCapability(chat, true, null, null);
            this.files = defaultCapability(files, true, null, null);
            this.calendar = defaultCapability(calendar, false, null, null);
            this.boards = defaultCapability(boards, false, null, null);
            this.meetingsCalls = defaultCapability(meetingsCalls, false, null, null);
            this.documentsCollaboration = defaultCapability(documentsCollaboration, false, null, null);
            this.decisionsEvidence = defaultCapability(decisionsEvidence, true, null, WorkspaceCapabilityReadiness.READY);
            this.manualsHelp = defaultCapability(manualsHelp, true, null, WorkspaceCapabilityReadiness.READY);
            this.releaseEvidence = defaultCapability(releaseEvidence, true, null, WorkspaceCapabilityReadiness.READY);
            this.adminControlPlane = defaultCapability(adminControlPlane, true, null, WorkspaceCapabilityReadiness.READY);
            this.agentRuntimeControl = defaultCapability(agentRuntimeControl, false, null, null);
        }

        private static Capability defaultCapability(
                Capability capability,
                boolean enabled,
                String dependencyUrl,
                WorkspaceCapabilityReadiness readiness) {
            if (capability == null) {
                return new Capability(enabled, dependencyUrl, readiness);
            }
            return new Capability(capability.enabled(), capability.dependencyUrl(), capability.readiness());
        }

        public record Capability(boolean enabled, String dependencyUrl, WorkspaceCapabilityReadiness readiness) {
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(WorkspaceCapabilityProperties.class)
    @Import({
            ChatWorkspaceCapabilityConfiguration.class,
            FilesWorkspaceCapabilityConfiguration.class,
            CalendarWorkspaceCapabilityConfiguration.class,
            BoardsWorkspaceCapabilityConfiguration.class,
            MeetingsWorkspaceCapabilityConfiguration.class,
            OfficeWorkspaceCapabilityConfiguration.class,
            DecisionsWorkspaceCapabilityConfiguration.class,
            DevopsWorkspaceCapabilityConfiguration.class,
            AgentRuntimeWorkspaceCapabilityConfiguration.class
    })
    public static class Configuration {
        @Bean
        ProviderCapabilityHealthService capabilityHealth() {
            return emptyHealth();
        }
    }

    public static WorkspaceCapabilityService service(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            Settings settings) {
        return service(resource, security, settings,
                WorkloadEntitlementProperties.disabled(), null, OrganizationIdentityContextResolver.defaults());
    }

    public static WorkspaceCapabilityService service(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            Settings settings,
            ProviderCapabilityHealthService health) {
        return service(resource, security, settings,
                WorkloadEntitlementProperties.disabled(), health, OrganizationIdentityContextResolver.defaults());
    }

    public static WorkspaceCapabilityService service(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            Settings settings,
            WorkloadEntitlementProperties entitlement) {
        return service(resource, security, settings,
                entitlement, null, OrganizationIdentityContextResolver.defaults());
    }

    public static WorkspaceCapabilityService service(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            Settings settings,
            WorkloadEntitlementProperties entitlement,
            ProviderCapabilityHealthService health) {
        return service(resource, security, settings,
                entitlement, health, OrganizationIdentityContextResolver.defaults());
    }

    public static WorkspaceCapabilityService service(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            Settings settings,
            WorkloadEntitlementProperties entitlement,
            ProviderCapabilityHealthService health,
            OrganizationIdentityContextResolver identities) {
        health = health == null ? emptyHealth() : health;
        List<WorkspaceCapabilityContribution> contributions = new ArrayList<>();
        contributions.add(ChatWorkspaceCapabilities.contribution(
                new ChatWorkspaceCapabilityProperties(convert(settings.chat())), health));
        contributions.add(FilesWorkspaceCapabilities.contribution(
                new FilesWorkspaceCapabilityProperties(convert(settings.files())), health));
        contributions.add(CalendarWorkspaceCapabilities.contribution(
                new CalendarWorkspaceCapabilityProperties(convert(settings.calendar())), health));
        contributions.add(BoardsWorkspaceCapabilities.contribution(
                new BoardsWorkspaceCapabilityProperties(convert(settings.boards()))));
        contributions.add(MeetingsWorkspaceCapabilities.contribution(
                new MeetingsWorkspaceCapabilityProperties(convert(settings.meetingsCalls()))));
        contributions.add(OfficeWorkspaceCapabilities.contribution(
                new OfficeWorkspaceCapabilityProperties(convert(settings.documentsCollaboration()))));
        contributions.add(DecisionsWorkspaceCapabilities.contribution(
                new DecisionsWorkspaceCapabilityProperties(convert(settings.decisionsEvidence()))));
        contributions.add(DevopsWorkspaceCapabilities.contribution(
                new DevopsWorkspaceCapabilityProperties(convert(settings.releaseEvidence()))));
        contributions.add(AgentRuntimeWorkspaceCapabilities.contribution(
                new AgentRuntimeWorkspaceCapabilityProperties(convert(settings.agentRuntimeControl())), entitlement));
        return new WorkspaceCapabilityService(resource, security,
                new WorkspaceCapabilityProperties(convert(settings.shellAccess()), convert(settings.adminControlPlane())),
                identities, contributions);
    }

    private static ProviderCapabilityHealthService emptyHealth() {
        var health = org.mockito.Mockito.mock(ProviderCapabilityHealthService.class);
        org.mockito.Mockito.when(health.cached(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        return health;
    }

    public static CapabilitySettings convert(Settings.Capability value) {
        return new CapabilitySettings(value.enabled(), value.dependencyUrl(), value.readiness());
    }

    public static WorkspaceCapabilitiesResponse snapshot(
            WorkspaceCapabilityStatusResponse shellAccess,
            WorkspaceCapabilityStatusResponse chat,
            WorkspaceCapabilityStatusResponse files,
            WorkspaceCapabilityStatusResponse calendar,
            WorkspaceCapabilityStatusResponse boards,
            WorkspaceCapabilityStatusResponse agentRuntimeControl) {
        return snapshot(shellAccess, chat, files, calendar, boards,
                disabled("meetings/calls"), disabled("documents/collaboration"),
                ready("decisions/evidence"), null, ready("release evidence"),
                ready("admin control plane"), agentRuntimeControl);
    }

    private static WorkspaceCapabilityStatusResponse disabled(String category) {
        return new WorkspaceCapabilityStatusResponse(
                false,
                WorkspaceCapabilityReadiness.UNAVAILABLE,
                WorkspaceCapabilityPolicyState.DISABLED,
                null,
                category + " is disabled or not member-facing in this workspace.",
                java.util.List.of());
    }

    private static WorkspaceCapabilityStatusResponse ready(String category) {
        return new WorkspaceCapabilityStatusResponse(
                true,
                WorkspaceCapabilityReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED,
                "system-internal-readiness",
                category + " is represented by backend-owned Weave domain evidence.",
                java.util.List.of());
    }

    public static WorkspaceCapabilitiesResponse snapshot(
            WorkspaceCapabilityStatusResponse shellAccess,
            WorkspaceCapabilityStatusResponse chat,
            WorkspaceCapabilityStatusResponse files,
            WorkspaceCapabilityStatusResponse calendar,
            WorkspaceCapabilityStatusResponse boards,
            WorkspaceCapabilityStatusResponse meetingsCalls,
            WorkspaceCapabilityStatusResponse documentsCollaboration,
            WorkspaceCapabilityStatusResponse decisionsEvidence,
            WorkspaceCapabilityStatusResponse manualsHelp,
            WorkspaceCapabilityStatusResponse releaseEvidence,
            WorkspaceCapabilityStatusResponse adminControlPlane,
            WorkspaceCapabilityStatusResponse agentRuntimeControl) {
        var states = new java.util.LinkedHashMap<String, WorkspaceCapabilityStatusResponse>();
        states.put("chat", chat); states.put("files", files); states.put("calendar", calendar);
        states.put("boards", boards); states.put("meetings", meetingsCalls);
        states.put("office", documentsCollaboration); states.put("decisions", decisionsEvidence);
        states.put("devops", releaseEvidence); states.put("agent-runtime", agentRuntimeControl);
        states.values().removeIf(java.util.Objects::isNull);
        return new WorkspaceCapabilitiesResponse(shellAccess, adminControlPlane, states);
    }
}
