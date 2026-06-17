package com.massimotter.weave.contract.mcp;

import java.util.List;
import java.util.Set;

public enum MemberMcpDomainDefinition {
    FILES_DOCS("files-docs", "Files and documents", List.of("files.read"), List.of("files.upload", "files.delete"), List.of("drive", "folder", "file", "file_version", "blob_ref", "permission", "share_link", "lock", "trash_entry", "checksum", "document", "editor_session", "space_ref")),
    CALENDAR_MEETINGS("calendar-meetings", "Calendar and meetings", List.of("calendar.read"), List.of("calendar.manage_events"), List.of("calendar", "event", "occurrence", "attendee", "conference_link", "meeting", "space_ref", "agenda_ref")),
    BOARDS_TASKS("boards-tasks", "Boards and tasks", List.of("boards.read"), List.of("boards.update_task", "boards.sync_workspace"), List.of("board", "list", "task", "status", "assignee", "comment", "attachment_ref", "decision_link", "space_ref"));

    public static final String CONTRACT_VERSION = "member-mcp-contract-v1";
    private final String domain;
    private final String label;
    private final List<String> readCapabilities;
    private final List<String> writeCapabilities;
    private final List<String> canonicalObjectKinds;

    MemberMcpDomainDefinition(String domain, String label, List<String> readCapabilities, List<String> writeCapabilities, List<String> canonicalObjectKinds) {
        this.domain = domain;
        this.label = label;
        this.readCapabilities = List.copyOf(readCapabilities);
        this.writeCapabilities = List.copyOf(writeCapabilities);
        this.canonicalObjectKinds = List.copyOf(canonicalObjectKinds);
    }

    public MemberMcpDomainContract contract() { return new MemberMcpDomainContract(CONTRACT_VERSION, domain, label, readCapabilities, writeCapabilities, canonicalObjectKinds, true, true, false); }
    public String domain() { return domain; }
    public List<String> readCapabilities() { return readCapabilities; }
    public List<String> writeCapabilities() { return writeCapabilities; }
    public Set<String> allCapabilities() { return java.util.stream.Stream.concat(readCapabilities.stream(), writeCapabilities.stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
