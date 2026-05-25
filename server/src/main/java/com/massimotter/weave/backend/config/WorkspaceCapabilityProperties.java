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
    public WorkspaceCapabilityProperties(
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
        this.weaver = defaultCapability(weaver, false, null, null);
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
