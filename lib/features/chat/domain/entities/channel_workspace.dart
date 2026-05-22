import 'package:weave/features/app/domain/entities/provider_stack_status.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

enum ChannelWorkspaceSurfaceKind { chat, files, boards, calendar, meetings }

enum ChannelWorkspaceSurfaceAvailability { available, preview, gated }

class ChannelWorkspaceSurface {
  const ChannelWorkspaceSurface({
    required this.kind,
    required this.availability,
    required this.providerContractId,
    required this.contextId,
    required this.providerReadinessId,
  });

  final ChannelWorkspaceSurfaceKind kind;
  final ChannelWorkspaceSurfaceAvailability availability;
  final String providerContractId;
  final String contextId;
  final String providerReadinessId;

  bool get isAvailable =>
      availability == ChannelWorkspaceSurfaceAvailability.available;

  bool get isGated => availability == ChannelWorkspaceSurfaceAvailability.gated;
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
    ProviderStackStatus? providerStack,
  }) {
    final contextId = 'channel:${conversation.id}';
    final officeReadiness = _providerReadinessId(
      providerStack,
      modules: const {'office'},
      prefix: 'office',
    );
    final devopsReadiness = _providerReadinessId(
      providerStack,
      modules: const {'source-control', 'issue-tracker', 'ci', 'release'},
      prefix: 'devops',
    );
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
          providerReadinessId: 'matrix-client-ready',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.files,
          availability: ChannelWorkspaceSurfaceAvailability.preview,
          providerContractId: 'weave-files-channel-link',
          contextId: contextId,
          providerReadinessId: officeReadiness,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.boards,
          availability: ChannelWorkspaceSurfaceAvailability.preview,
          providerContractId: 'weave-boards-channel-link',
          contextId: contextId,
          providerReadinessId: devopsReadiness,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.calendar,
          availability: ChannelWorkspaceSurfaceAvailability.gated,
          providerContractId: 'weave-calendar-channel-scope',
          contextId: contextId,
          providerReadinessId: 'calendar-channel-scope-gated',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.meetings,
          availability: ChannelWorkspaceSurfaceAvailability.gated,
          providerContractId: 'weave-meetings-channel-preview',
          contextId: contextId,
          providerReadinessId: 'meetings-provider-fail-closed',
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

String _providerReadinessId(
  ProviderStackStatus? providerStack, {
  required Set<String> modules,
  required String prefix,
}) {
  if (providerStack == null) {
    return '$prefix-provider-registry-pending';
  }

  final providers = providerStack.providers
      .where((provider) => modules.contains(provider.module))
      .toList(growable: false);
  if (providers.isEmpty) {
    return '$prefix-provider-unavailable';
  }

  if (providers.any((provider) => provider.isReady)) {
    return '$prefix-provider-ready';
  }

  if (providers.every((provider) => provider.isFailClosedUnavailable)) {
    return '$prefix-provider-fail-closed';
  }

  return '$prefix-provider-review-required';
}
