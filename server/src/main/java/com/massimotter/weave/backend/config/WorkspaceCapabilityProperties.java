package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "weave.workspace")

public record WorkspaceCapabilityProperties(
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
        Capability weaver) {

    public WorkspaceCapabilityProperties(
            Capability shellAccess,
            Capability chat,
            Capability files,
            Capability calendar,
            Capability boards,
            Capability weaver) {
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
                weaver);
    }

    @ConstructorBinding
    public WorkspaceCapabilityProperties {
        shellAccess = defaultCapability(shellAccess, true, null, null);
        chat = defaultCapability(chat, true, null, null);
        files = defaultCapability(files, true, null, null);
        calendar = defaultCapability(calendar, false, null, null);
        boards = defaultCapability(boards, false, null, null);
        meetingsCalls = defaultCapability(meetingsCalls, false, null, null);
        documentsCollaboration = defaultCapability(documentsCollaboration, false, null, null);
        decisionsEvidence = defaultCapability(decisionsEvidence, true, null, WorkspaceCapabilityReadiness.READY);
        manualsHelp = defaultCapability(manualsHelp, true, null, WorkspaceCapabilityReadiness.READY);
        releaseEvidence = defaultCapability(releaseEvidence, true, null, WorkspaceCapabilityReadiness.READY);
        adminControlPlane = defaultCapability(adminControlPlane, true, null, WorkspaceCapabilityReadiness.READY);
        weaver = defaultCapability(weaver, false, null, null);
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
