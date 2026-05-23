import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

enum ChannelWorkspaceSurfaceKind { chat, files, boards, calendar, meetings }

enum ChannelWorkspaceSurfaceAvailability {
  available,
  adminSetupRequired,
  disabledByPolicy,
  degraded,
  gated,
}

class ChannelWorkspaceSurface {
  const ChannelWorkspaceSurface({
    required this.kind,
    required this.availability,
    required this.providerContractId,
    required this.contextId,
  });

  final ChannelWorkspaceSurfaceKind kind;
  final ChannelWorkspaceSurfaceAvailability availability;
  final String providerContractId;
  final String contextId;

  bool get isAvailable =>
      availability == ChannelWorkspaceSurfaceAvailability.available;

  bool get isUnavailable => !isAvailable;

  bool get isGated =>
      availability == ChannelWorkspaceSurfaceAvailability.gated ||
      availability == ChannelWorkspaceSurfaceAvailability.adminSetupRequired ||
      availability == ChannelWorkspaceSurfaceAvailability.disabledByPolicy ||
      availability == ChannelWorkspaceSurfaceAvailability.degraded;
}

enum ChannelMeetingContextItemKind {
  agenda,
  files,
  decisions,
  tasks,
  followUpEvidence,
}

enum ChannelMeetingControlKind { join, start }

class ChannelMeetingContextItem {
  const ChannelMeetingContextItem({
    required this.kind,
    required this.includedInPreview,
  });

  final ChannelMeetingContextItemKind kind;
  final bool includedInPreview;
}

class ChannelMeetingControl {
  const ChannelMeetingControl({
    required this.kind,
    required this.enabled,
    required this.disabledReason,
  });

  final ChannelMeetingControlKind kind;
  final bool enabled;
  final String disabledReason;
}

class ChannelMeetingPreview {
  const ChannelMeetingPreview({
    required this.channelId,
    required this.channelTitle,
    required this.contextId,
    required this.providerContractId,
    required this.contextItems,
    required this.controls,
    required this.hasVideoBackendCapability,
    required this.e2eeEvidenceAvailable,
    required this.recordingEnabled,
    required this.transcriptionEnabled,
    required this.backgroundRoomReadingEnabled,
  });

  factory ChannelMeetingPreview.forConversation(
    ChatConversation conversation, {
    required String contextId,
  }) {
    const backendUnavailableReason = 'meeting-backend-capability-unavailable';

    return ChannelMeetingPreview(
      channelId: conversation.id,
      channelTitle: conversation.title,
      contextId: contextId,
      providerContractId: 'livekit-meetings-channel-gate',
      contextItems: const [
        ChannelMeetingContextItem(
          kind: ChannelMeetingContextItemKind.agenda,
          includedInPreview: true,
        ),
        ChannelMeetingContextItem(
          kind: ChannelMeetingContextItemKind.files,
          includedInPreview: true,
        ),
        ChannelMeetingContextItem(
          kind: ChannelMeetingContextItemKind.decisions,
          includedInPreview: true,
        ),
        ChannelMeetingContextItem(
          kind: ChannelMeetingContextItemKind.tasks,
          includedInPreview: true,
        ),
        ChannelMeetingContextItem(
          kind: ChannelMeetingContextItemKind.followUpEvidence,
          includedInPreview: true,
        ),
      ],
      controls: const [
        ChannelMeetingControl(
          kind: ChannelMeetingControlKind.join,
          enabled: false,
          disabledReason: backendUnavailableReason,
        ),
        ChannelMeetingControl(
          kind: ChannelMeetingControlKind.start,
          enabled: false,
          disabledReason: backendUnavailableReason,
        ),
      ],
      hasVideoBackendCapability: false,
      e2eeEvidenceAvailable: false,
      recordingEnabled: false,
      transcriptionEnabled: false,
      backgroundRoomReadingEnabled: false,
    );
  }

  final String channelId;
  final String channelTitle;
  final String contextId;
  final String providerContractId;
  final List<ChannelMeetingContextItem> contextItems;
  final List<ChannelMeetingControl> controls;
  final bool hasVideoBackendCapability;
  final bool e2eeEvidenceAvailable;
  final bool recordingEnabled;
  final bool transcriptionEnabled;
  final bool backgroundRoomReadingEnabled;

  bool get isFailClosed =>
      !hasVideoBackendCapability &&
      !e2eeEvidenceAvailable &&
      controls.every((control) => !control.enabled);

  bool get requiresExplicitConsent =>
      !recordingEnabled && !transcriptionEnabled;
}

class ChannelWorkspacePreview {
  const ChannelWorkspacePreview({
    required this.channelId,
    required this.channelTitle,
    required this.contextId,
    required this.surfaces,
    required this.meetingPreview,
    required this.explicitContextOnly,
    required this.backgroundRoomReadingEnabled,
    required this.adminSetupExposedToMembers,
  });

  factory ChannelWorkspacePreview.forConversation(
    ChatConversation conversation,
  ) {
    final contextId = 'channel:${conversation.id}';
    return ChannelWorkspacePreview(
      channelId: conversation.id,
      channelTitle: conversation.title,
      contextId: contextId,
      surfaces: [
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.chat,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'matrix-room',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.files,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'weave-files-channel-link',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.boards,
          availability: ChannelWorkspaceSurfaceAvailability.adminSetupRequired,
          providerContractId: 'weave-boards-channel-link',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.calendar,
          availability: ChannelWorkspaceSurfaceAvailability.gated,
          providerContractId: 'weave-calendar-channel-scope',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.meetings,
          availability: ChannelWorkspaceSurfaceAvailability.gated,
          providerContractId: 'livekit-meetings-channel-gate',
          contextId: contextId,
        ),
      ],
      meetingPreview: ChannelMeetingPreview.forConversation(
        conversation,
        contextId: contextId,
      ),
      explicitContextOnly: true,
      backgroundRoomReadingEnabled: false,
      adminSetupExposedToMembers: false,
    );
  }

  final String channelId;
  final String channelTitle;
  final String contextId;
  final List<ChannelWorkspaceSurface> surfaces;
  final ChannelMeetingPreview meetingPreview;
  final bool explicitContextOnly;
  final bool backgroundRoomReadingEnabled;
  final bool adminSetupExposedToMembers;

  bool get isChannelWorkspaceGoverned =>
      explicitContextOnly &&
      !backgroundRoomReadingEnabled &&
      !adminSetupExposedToMembers;

  ChannelWorkspaceSurface surface(ChannelWorkspaceSurfaceKind kind) {
    return surfaces.singleWhere((surface) => surface.kind == kind);
  }
}
