package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Calendar ownership scope exposed by the Weave calendar facade.")
public record CalendarScopeResponse(
        @Schema(description = "Stable scope identifier.", example = "workspace")
        String id,
        @Schema(description = "Scope type.", allowableValues = {"workspace", "team", "channel"}, example = "workspace")
        String type,
        @Schema(description = "Human-readable scope label.", example = "Weave workspace calendar")
        String label,
        @Schema(description = "Workspace identifier that owns this scope.", example = "workspace")
        String workspaceId,
        @Schema(description = "Durable Weave Context/Space identifier backing this calendar scope.", example = "channel-engineering-general")
        String contextId,
        @Schema(description = "Team identifier for team/channel scopes.", example = "engineering")
        String teamId,
        @Schema(description = "Channel identifier for channel scopes.", example = "engineering-general")
        String channelId,
        @Schema(description = "Access model for this scope.", example = "shared-workspace-calendar")
        String accessModel,
        @Schema(description = "Granted user capabilities for this scope.")
        List<String> capabilities) {

    public CalendarScopeResponse(String type, String label) {
        this(defaultId(type, null, null), type, label, "workspace", null, null, null,
                defaultAccessModel(type), defaultCapabilities());
    }

    public CalendarScopeResponse {
        if (id == null || id.isBlank()) {
            id = defaultId(type, teamId, channelId);
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            workspaceId = "workspace";
        }
        if (contextId == null || contextId.isBlank()) {
            contextId = defaultContextId(type, teamId, channelId);
        }
        if (accessModel == null || accessModel.isBlank()) {
            accessModel = defaultAccessModel(type);
        }
        if (capabilities == null || capabilities.isEmpty()) {
            capabilities = defaultCapabilities();
        }
    }

    public static CalendarScopeResponse workspace() {
        return new CalendarScopeResponse(
                "workspace",
                "workspace",
                "Weave workspace calendar",
                "workspace",
                "workspace-default",
                null,
                null,
                "shared-workspace-calendar",
                defaultCapabilities());
    }

    public static CalendarScopeResponse team(String teamId, String label) {
        return new CalendarScopeResponse(
                "team:" + teamId,
                "team",
                label,
                "workspace",
                defaultContextId("team", teamId, null),
                teamId,
                null,
                "shared-team-calendar",
                defaultCapabilities());
    }

    public static CalendarScopeResponse channel(String teamId, String channelId, String label) {
        return new CalendarScopeResponse(
                "channel:" + channelId,
                "channel",
                label,
                "workspace",
                defaultContextId("channel", teamId, channelId),
                teamId,
                channelId,
                "shared-channel-calendar",
                defaultCapabilities());
    }

    private static List<String> defaultCapabilities() {
        return List.of("read", "create", "edit", "delete");
    }

    private static String defaultAccessModel(String type) {
        return switch (type == null ? "workspace" : type) {
            case "team" -> "shared-team-calendar";
            case "channel" -> "shared-channel-calendar";
            default -> "shared-workspace-calendar";
        };
    }

    private static String defaultId(String type, String teamId, String channelId) {
        return switch (type == null ? "workspace" : type) {
            case "team" -> teamId == null || teamId.isBlank() ? "team" : "team:" + teamId;
            case "channel" -> channelId == null || channelId.isBlank() ? "channel" : "channel:" + channelId;
            default -> "workspace";
        };
    }

    private static String defaultContextId(String type, String teamId, String channelId) {
        return switch (type == null ? "workspace" : type) {
            case "team" -> "team-" + (teamId == null || teamId.isBlank() ? "engineering" : teamId);
            case "channel" -> "channel-" + (channelId == null || channelId.isBlank() ? "engineering-general" : channelId);
            default -> "workspace-default";
        };
    }
}
