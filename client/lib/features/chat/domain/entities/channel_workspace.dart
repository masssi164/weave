import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

enum ChannelWorkspaceSurfaceKind {
  chat,
  decisions,
  files,
  boards,
  calendar,
  meetings,
  weaver,
}

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
      providerContractId: 'weave-meetings-channel-capability',
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

enum ChannelWeaverScoutCapabilityKind {
  summarizeAllowedContext,
  citeSources,
  proposeOnly,
  approvalReceiptRequired,
}

enum ChannelWeaverScoutSourceKind { message, decision, file, task, meeting }

enum ChannelWeaverApprovalResultCategory { proposed, approved, denied, blocked }

class ChannelWeaverScoutCapability {
  const ChannelWeaverScoutCapability({
    required this.kind,
    required this.enabled,
    required this.description,
  });

  final ChannelWeaverScoutCapabilityKind kind;
  final bool enabled;
  final String description;
}

class ChannelWeaverScoutSource {
  const ChannelWeaverScoutSource({
    required this.kind,
    required this.sourceId,
    required this.label,
    required this.supportSafeExcerpt,
  });

  final ChannelWeaverScoutSourceKind kind;
  final String sourceId;
  final String label;
  final String supportSafeExcerpt;

  bool get isCitable => sourceId.isNotEmpty && label.isNotEmpty;
}

class ChannelWeaverApprovalReceipt {
  const ChannelWeaverApprovalReceipt({
    required this.id,
    required this.actorRef,
    required this.requestedAction,
    required this.approvedAction,
    required this.targetRef,
    required this.timestamp,
    required this.resultCategory,
  });

  final String id;
  final String actorRef;
  final String requestedAction;
  final String approvedAction;
  final String targetRef;
  final DateTime timestamp;
  final ChannelWeaverApprovalResultCategory resultCategory;

  bool get isComplete =>
      actorRef.isNotEmpty &&
      requestedAction.isNotEmpty &&
      approvedAction.isNotEmpty &&
      targetRef.isNotEmpty;
}

class ChannelWeaverScoutPreview {
  const ChannelWeaverScoutPreview({
    required this.channelId,
    required this.channelTitle,
    required this.contextId,
    required this.providerContractId,
    required this.capabilities,
    required this.allowedSources,
    required this.approvalReceipts,
    required this.readOnly,
    required this.proposalOnly,
    required this.backgroundRoomReadingEnabled,
    required this.supportSafeFailureMode,
  });

  factory ChannelWeaverScoutPreview.forConversation(
    ChatConversation conversation, {
    required String contextId,
  }) {
    return ChannelWeaverScoutPreview(
      channelId: conversation.id,
      channelTitle: conversation.title,
      contextId: contextId,
      providerContractId: 'weave-weaver-channel-scout',
      capabilities: const [
        ChannelWeaverScoutCapability(
          kind: ChannelWeaverScoutCapabilityKind.summarizeAllowedContext,
          enabled: true,
          description: 'Summarize only explicitly allowed channel context.',
        ),
        ChannelWeaverScoutCapability(
          kind: ChannelWeaverScoutCapabilityKind.citeSources,
          enabled: true,
          description: 'Cite messages, files, tasks, meetings, and decisions.',
        ),
        ChannelWeaverScoutCapability(
          kind: ChannelWeaverScoutCapabilityKind.proposeOnly,
          enabled: true,
          description: 'Draft or propose actions without mutating team data.',
        ),
        ChannelWeaverScoutCapability(
          kind: ChannelWeaverScoutCapabilityKind.approvalReceiptRequired,
          enabled: true,
          description: 'Require approval receipts for any future write path.',
        ),
      ],
      allowedSources: const [
        ChannelWeaverScoutSource(
          kind: ChannelWeaverScoutSourceKind.message,
          sourceId: 'channel-messages:explicit',
          label: 'Explicit channel messages',
          supportSafeExcerpt:
              'Messages selected or allowed by the member context policy.',
        ),
        ChannelWeaverScoutSource(
          kind: ChannelWeaverScoutSourceKind.decision,
          sourceId: 'decision-ledger:channel',
          label: 'Decision ledger',
          supportSafeExcerpt: 'Captured decisions with source references.',
        ),
        ChannelWeaverScoutSource(
          kind: ChannelWeaverScoutSourceKind.task,
          sourceId: 'boards:channel-open-tasks',
          label: 'Open tasks',
          supportSafeExcerpt:
              'Task status and follow-up links visible to the member.',
        ),
        ChannelWeaverScoutSource(
          kind: ChannelWeaverScoutSourceKind.meeting,
          sourceId: 'meeting-capsules:channel',
          label: 'Meeting capsules',
          supportSafeExcerpt:
              'Agenda and follow-up references, not recordings or transcripts.',
        ),
      ],
      approvalReceipts: const <ChannelWeaverApprovalReceipt>[],
      readOnly: true,
      proposalOnly: true,
      backgroundRoomReadingEnabled: false,
      supportSafeFailureMode: true,
    );
  }

  final String channelId;
  final String channelTitle;
  final String contextId;
  final String providerContractId;
  final List<ChannelWeaverScoutCapability> capabilities;
  final List<ChannelWeaverScoutSource> allowedSources;
  final List<ChannelWeaverApprovalReceipt> approvalReceipts;
  final bool readOnly;
  final bool proposalOnly;
  final bool backgroundRoomReadingEnabled;
  final bool supportSafeFailureMode;

  bool get isGovernedReadOnlyScout =>
      readOnly &&
      proposalOnly &&
      !backgroundRoomReadingEnabled &&
      supportSafeFailureMode &&
      capabilities.every((capability) => capability.enabled) &&
      allowedSources.every((source) => source.isCitable) &&
      approvalReceipts.every((receipt) => receipt.isComplete);

  bool get requiresApprovalReceiptsForWrites => capabilities.any(
    (capability) =>
        capability.kind ==
            ChannelWeaverScoutCapabilityKind.approvalReceiptRequired &&
        capability.enabled,
  );
}

class ChannelWorkspacePreview {
  const ChannelWorkspacePreview({
    required this.channelId,
    required this.channelTitle,
    required this.contextId,
    required this.surfaces,
    required this.meetingPreview,
    required this.weaverScoutPreview,
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
          kind: ChannelWorkspaceSurfaceKind.decisions,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'weave-decision-ledger-channel',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.files,
          availability: ChannelWorkspaceSurfaceAvailability.adminSetupRequired,
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
          providerContractId: 'weave-meetings-channel-capability',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.weaver,
          availability: ChannelWorkspaceSurfaceAvailability.gated,
          providerContractId: 'weave-weaver-channel-scout',
          contextId: contextId,
        ),
      ],
      meetingPreview: ChannelMeetingPreview.forConversation(
        conversation,
        contextId: contextId,
      ),
      weaverScoutPreview: ChannelWeaverScoutPreview.forConversation(
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
  final ChannelWeaverScoutPreview weaverScoutPreview;
  final bool explicitContextOnly;
  final bool backgroundRoomReadingEnabled;
  final bool adminSetupExposedToMembers;

  bool get isChannelWorkspaceGoverned =>
      explicitContextOnly &&
      !backgroundRoomReadingEnabled &&
      !adminSetupExposedToMembers &&
      weaverScoutPreview.isGovernedReadOnlyScout;

  ChannelWorkspaceSurface surface(ChannelWorkspaceSurfaceKind kind) {
    return surfaces.singleWhere((surface) => surface.kind == kind);
  }
}
