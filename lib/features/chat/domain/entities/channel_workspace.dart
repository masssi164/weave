import 'package:weave/features/app/domain/entities/provider_stack_status.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

enum ChannelWorkspaceSurfaceKind {
  chat,
  files,
  boards,
  calendar,
  meetings,
  devops,
  office,
}

enum ChannelWorkspaceSurfaceAvailability { available, preview, gated }

class ChannelWorkspaceSurface {
  const ChannelWorkspaceSurface({
    required this.kind,
    required this.availability,
    required this.providerContractId,
    required this.contextId,
    this.statusSummary,
    this.failClosed = false,
    this.supportSafe = true,
  });

  final ChannelWorkspaceSurfaceKind kind;
  final ChannelWorkspaceSurfaceAvailability availability;
  final String providerContractId;
  final String contextId;
  final String? statusSummary;
  final bool failClosed;
  final bool supportSafe;

  bool get isAvailable =>
      availability == ChannelWorkspaceSurfaceAvailability.available;

  bool get isGated => availability == ChannelWorkspaceSurfaceAvailability.gated;

  bool get isUnavailable => failClosed || isGated || !supportSafe;
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
      providerContractId: 'weave-meetings-channel-preview',
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
    ChatConversation conversation, {
    DevopsSummary? devopsSummary,
    OfficeCapabilities? officeCapabilities,
    bool providerReadinessLoading = false,
    bool providerReadinessUnavailable = false,
  }) {
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
          availability: ChannelWorkspaceSurfaceAvailability.preview,
          providerContractId: 'weave-files-channel-link',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.boards,
          availability: ChannelWorkspaceSurfaceAvailability.preview,
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
          providerContractId: 'weave-meetings-channel-preview',
          contextId: contextId,
          statusSummary: 'Video backend capability is unavailable.',
          failClosed: true,
        ),
        _devopsSurface(
          contextId: contextId,
          summary: devopsSummary,
          loading: providerReadinessLoading,
          unavailable: providerReadinessUnavailable,
        ),
        _officeSurface(
          contextId: contextId,
          capabilities: officeCapabilities,
          loading: providerReadinessLoading,
          unavailable: providerReadinessUnavailable,
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

ChannelWorkspaceSurface _devopsSurface({
  required String contextId,
  required DevopsSummary? summary,
  required bool loading,
  required bool unavailable,
}) {
  if (summary == null) {
    return ChannelWorkspaceSurface(
      kind: ChannelWorkspaceSurfaceKind.devops,
      availability: ChannelWorkspaceSurfaceAvailability.gated,
      providerContractId: 'weave-devops-channel-summary',
      contextId: contextId,
      statusSummary: loading
          ? 'Checking backend-owned DevOps provider readiness.'
          : unavailable
          ? 'DevOps readiness is unavailable from the backend.'
          : 'DevOps providers are not configured for this channel.',
      failClosed: !loading,
      supportSafe: true,
    );
  }

  return ChannelWorkspaceSurface(
    kind: ChannelWorkspaceSurfaceKind.devops,
    availability: summary.isAvailable
        ? ChannelWorkspaceSurfaceAvailability.available
        : ChannelWorkspaceSurfaceAvailability.gated,
    providerContractId: 'weave-devops-channel-summary',
    contextId: contextId,
    statusSummary: summary.isAvailable
        ? 'Read-only DevOps summary available via backend facade.'
        : 'DevOps providers are disabled, unconfigured, or not support-safe.',
    failClosed: summary.shouldFailClosed,
    supportSafe: summary.supportSafe,
  );
}

ChannelWorkspaceSurface _officeSurface({
  required String contextId,
  required OfficeCapabilities? capabilities,
  required bool loading,
  required bool unavailable,
}) {
  if (capabilities == null) {
    return ChannelWorkspaceSurface(
      kind: ChannelWorkspaceSurfaceKind.office,
      availability: ChannelWorkspaceSurfaceAvailability.gated,
      providerContractId: 'weave-office-facade',
      contextId: contextId,
      statusSummary: loading
          ? 'Checking backend-owned Office provider readiness.'
          : unavailable
          ? 'Office readiness is unavailable from the backend.'
          : 'Office providers are not configured.',
      failClosed: !loading,
      supportSafe: true,
    );
  }

  return ChannelWorkspaceSurface(
    kind: ChannelWorkspaceSurfaceKind.office,
    availability: capabilities.isAvailable
        ? ChannelWorkspaceSurfaceAvailability.available
        : ChannelWorkspaceSurfaceAvailability.gated,
    providerContractId: 'weave-office-facade',
    contextId: contextId,
    statusSummary: capabilities.isAvailable
        ? 'Office document launch is available via backend facade.'
        : 'Office launch is disabled until a support-safe provider is configured.',
    failClosed: capabilities.shouldFailClosed,
    supportSafe: capabilities.supportSafe,
  );
}
