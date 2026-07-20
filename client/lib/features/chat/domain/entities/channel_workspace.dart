import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

enum ChannelWorkspaceSurfaceKind {
  chat,
  files,
  documents,
  calendar,
  meetings,
  boards,
  decisions,
  evidence,
}

enum ChannelWorkspaceSurfaceAvailability {
  available,
  disabledByPolicy,
  notConfigured,
  degraded,
  unavailable,
  comingLater,
}

class ChannelWorkspaceSurface {
  const ChannelWorkspaceSurface({
    required this.kind,
    required this.availability,
    required this.providerContractId,
    required this.contextId,
    required this.canonicalObjectRef,
    required this.supportSafeEvidenceRef,
  });

  final ChannelWorkspaceSurfaceKind kind;
  final ChannelWorkspaceSurfaceAvailability availability;
  final String providerContractId;
  final String contextId;
  final String canonicalObjectRef;
  final String supportSafeEvidenceRef;

  bool get isAvailable =>
      availability == ChannelWorkspaceSurfaceAvailability.available;

  bool get isUnavailable => !isAvailable;

  bool get isGated =>
      availability == ChannelWorkspaceSurfaceAvailability.unavailable ||
      availability == ChannelWorkspaceSurfaceAvailability.notConfigured ||
      availability == ChannelWorkspaceSurfaceAvailability.disabledByPolicy ||
      availability == ChannelWorkspaceSurfaceAvailability.degraded ||
      availability == ChannelWorkspaceSurfaceAvailability.comingLater;

  bool get isComingLater =>
      availability == ChannelWorkspaceSurfaceAvailability.comingLater;

  bool get hasSupportSafeEvidence =>
      canonicalObjectRef.isNotEmpty &&
      supportSafeEvidenceRef.isNotEmpty &&
      canonicalObjectRef.startsWith('weave:') &&
      supportSafeEvidenceRef.startsWith('evidence:') &&
      !canonicalObjectRef.contains('!') &&
      !supportSafeEvidenceRef.contains('!') &&
      !canonicalObjectRef.contains('home.internal') &&
      !supportSafeEvidenceRef.contains('home.internal');
}

enum ChannelMeetingContextItemKind {
  agenda,
  files,
  decisions,
  tasks,
  followUpEvidence,
}

enum ChannelMeetingAttachPointKind { channel, calendarEvent, thread }

enum ChannelMeetingEncryptionBoundaryKind {
  matrixSignaling,
  mediaStreams,
  captions,
  transcripts,
  recordings,
  metadata,
}

enum ChannelMeetingUxRequirementKind {
  deviceSelection,
  joinPreview,
  muteState,
  cameraState,
  participantList,
  errorRecovery,
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

class ChannelMeetingAttachPoint {
  const ChannelMeetingAttachPoint({
    required this.kind,
    required this.contextId,
    required this.label,
    required this.enabled,
  });

  final ChannelMeetingAttachPointKind kind;
  final String contextId;
  final String label;
  final bool enabled;
}

class ChannelMeetingEncryptionBoundary {
  const ChannelMeetingEncryptionBoundary({
    required this.kind,
    required this.claim,
    required this.evidenceLabel,
  });

  final ChannelMeetingEncryptionBoundaryKind kind;
  final String claim;
  final String evidenceLabel;

  bool get hasEvidence => evidenceLabel.isNotEmpty;
}

class ChannelMeetingUxRequirement {
  const ChannelMeetingUxRequirement({
    required this.kind,
    required this.required,
    required this.evidenceLabel,
  });

  final ChannelMeetingUxRequirementKind kind;
  final bool required;
  final String evidenceLabel;

  bool get isDocumented => required && evidenceLabel.isNotEmpty;
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
    required this.attachPoints,
    required this.contextItems,
    required this.encryptionBoundaries,
    required this.uxRequirements,
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
      attachPoints: [
        ChannelMeetingAttachPoint(
          kind: ChannelMeetingAttachPointKind.channel,
          contextId: contextId,
          label: conversation.title,
          enabled: true,
        ),
        const ChannelMeetingAttachPoint(
          kind: ChannelMeetingAttachPointKind.calendarEvent,
          contextId: 'calendar-event:pending',
          label: 'Calendar event link pending backend capability',
          enabled: false,
        ),
        const ChannelMeetingAttachPoint(
          kind: ChannelMeetingAttachPointKind.thread,
          contextId: 'thread:pending',
          label: 'Thread link pending backend capability',
          enabled: false,
        ),
      ],
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
      encryptionBoundaries: const [
        ChannelMeetingEncryptionBoundary(
          kind: ChannelMeetingEncryptionBoundaryKind.matrixSignaling,
          claim: 'Matrix signaling follows the room encryption posture.',
          evidenceLabel: 'Matrix E2EE diagnostic and room readiness evidence',
        ),
        ChannelMeetingEncryptionBoundary(
          kind: ChannelMeetingEncryptionBoundaryKind.mediaStreams,
          claim:
              'Media requires documented SFU/E2EE capability before enablement.',
          evidenceLabel: 'Media transport readiness and E2EE trade-off record',
        ),
        ChannelMeetingEncryptionBoundary(
          kind: ChannelMeetingEncryptionBoundaryKind.captions,
          claim:
              'Captions are off until consent, retention, and encryption are known.',
          evidenceLabel: 'Caption consent and storage policy evidence',
        ),
        ChannelMeetingEncryptionBoundary(
          kind: ChannelMeetingEncryptionBoundaryKind.transcripts,
          claim:
              'Transcripts are off until consent, retention, and encryption are known.',
          evidenceLabel: 'Transcript consent and storage policy evidence',
        ),
        ChannelMeetingEncryptionBoundary(
          kind: ChannelMeetingEncryptionBoundaryKind.recordings,
          claim:
              'Recordings are off until consent, retention, and encryption are known.',
          evidenceLabel: 'Recording consent and storage policy evidence',
        ),
        ChannelMeetingEncryptionBoundary(
          kind: ChannelMeetingEncryptionBoundaryKind.metadata,
          claim:
              'Metadata is minimized and never described as end-to-end encrypted.',
          evidenceLabel: 'Support-safe metadata boundary documentation',
        ),
      ],
      uxRequirements: const [
        ChannelMeetingUxRequirement(
          kind: ChannelMeetingUxRequirementKind.deviceSelection,
          required: true,
          evidenceLabel: 'Device picker contract',
        ),
        ChannelMeetingUxRequirement(
          kind: ChannelMeetingUxRequirementKind.joinPreview,
          required: true,
          evidenceLabel: 'Pre-join preview contract',
        ),
        ChannelMeetingUxRequirement(
          kind: ChannelMeetingUxRequirementKind.muteState,
          required: true,
          evidenceLabel: 'Audio mute state contract',
        ),
        ChannelMeetingUxRequirement(
          kind: ChannelMeetingUxRequirementKind.cameraState,
          required: true,
          evidenceLabel: 'Camera state contract',
        ),
        ChannelMeetingUxRequirement(
          kind: ChannelMeetingUxRequirementKind.participantList,
          required: true,
          evidenceLabel: 'Participant list accessibility contract',
        ),
        ChannelMeetingUxRequirement(
          kind: ChannelMeetingUxRequirementKind.errorRecovery,
          required: true,
          evidenceLabel: 'Join failure and recovery contract',
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
  final List<ChannelMeetingAttachPoint> attachPoints;
  final List<ChannelMeetingContextItem> contextItems;
  final List<ChannelMeetingEncryptionBoundary> encryptionBoundaries;
  final List<ChannelMeetingUxRequirement> uxRequirements;
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

  bool get canLinkFromChannelOrCalendar =>
      attachPoints.any(
        (point) => point.kind == ChannelMeetingAttachPointKind.channel,
      ) &&
      attachPoints.any(
        (point) => point.kind == ChannelMeetingAttachPointKind.calendarEvent,
      );

  bool get hasDocumentedEncryptionBoundaries {
    final kinds = encryptionBoundaries.map((boundary) => boundary.kind).toSet();
    return ChannelMeetingEncryptionBoundaryKind.values.every(kinds.contains) &&
        encryptionBoundaries.every((boundary) => boundary.hasEvidence);
  }

  bool get hasAccessibleJoinContract {
    final kinds = uxRequirements.map((requirement) => requirement.kind).toSet();
    return ChannelMeetingUxRequirementKind.values.every(kinds.contains) &&
        uxRequirements.every((requirement) => requirement.isDocumented);
  }

  bool get preventsVagueSecurityClaims =>
      hasDocumentedEncryptionBoundaries &&
      !recordingEnabled &&
      !transcriptionEnabled &&
      !backgroundRoomReadingEnabled;
}

class ChannelWorkspacePreview {
  const ChannelWorkspacePreview({
    required this.channelId,
    required this.channelTitle,
    required this.contextId,
    required this.routePath,
    required this.spaceEvidenceRef,
    required this.finalDecisionEvidenceRef,
    required this.surfaces,
    required this.meetingPreview,
    required this.explicitContextOnly,
    required this.backgroundRoomReadingEnabled,
    required this.adminSetupExposedToMembers,
  });

  factory ChannelWorkspacePreview.forConversation(
    ChatConversation conversation,
  ) {
    final contextId = _weaveSpaceContextId(conversation);
    final spaceSlug = _weaveSpaceSlug(contextId);
    return ChannelWorkspacePreview(
      channelId: conversation.id,
      channelTitle: conversation.title,
      contextId: contextId,
      routePath: '/spaces/$spaceSlug/control-room',
      spaceEvidenceRef: 'evidence:$spaceSlug:space-identity',
      finalDecisionEvidenceRef: 'evidence:$spaceSlug:decision-final-state',
      surfaces: [
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.chat,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'weave-chat-conversation',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:chat-thread',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:chat-context-seen',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.files,
          availability: ChannelWorkspaceSurfaceAvailability.notConfigured,
          providerContractId: 'weave-files-channel-link',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:files-folder',
          supportSafeEvidenceRef:
              'evidence:$spaceSlug:files-link-not-configured',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.documents,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'weave-documents-space-collaboration',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:document-cabinet',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:documents-linked',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.calendar,
          availability: ChannelWorkspaceSurfaceAvailability.disabledByPolicy,
          providerContractId: 'weave-calendar-channel-scope',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:calendar-scope',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:calendar-policy-block',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.meetings,
          availability: ChannelWorkspaceSurfaceAvailability.comingLater,
          providerContractId: 'weave-meetings-channel-capability',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:meeting-capsule',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:meeting-coming-later',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.boards,
          availability: ChannelWorkspaceSurfaceAvailability.degraded,
          providerContractId: 'weave-boards-channel-link',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:board-lane',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:board-degraded',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.decisions,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'weave-decision-ledger-channel',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:decision-ledger',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:decision-final-state',
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.evidence,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'weave-evidence-linked-record',
          contextId: contextId,
          canonicalObjectRef: 'weave:$spaceSlug:evidence-ledger',
          supportSafeEvidenceRef: 'evidence:$spaceSlug:cross-domain-evidence',
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
  final String routePath;
  final String spaceEvidenceRef;
  final String finalDecisionEvidenceRef;
  final List<ChannelWorkspaceSurface> surfaces;
  final ChannelMeetingPreview meetingPreview;
  final bool explicitContextOnly;
  final bool backgroundRoomReadingEnabled;
  final bool adminSetupExposedToMembers;

  bool get isChannelWorkspaceGoverned =>
      explicitContextOnly &&
      !backgroundRoomReadingEnabled &&
      !adminSetupExposedToMembers &&
      routePath.startsWith('/spaces/') &&
      spaceEvidenceRef.startsWith('evidence:') &&
      finalDecisionEvidenceRef.startsWith('evidence:') &&
      surfaces.every((surface) => surface.hasSupportSafeEvidence);

  ChannelWorkspaceSurface surface(ChannelWorkspaceSurfaceKind kind) {
    return surfaces.singleWhere((surface) => surface.kind == kind);
  }
}

String _weaveSpaceSlug(String contextId) {
  return contextId.replaceAll(':', '-');
}

String _weaveSpaceContextId(ChatConversation conversation) {
  final input = conversation.id;
  var hash = 0xcbf29ce484222325;
  for (final unit in input.codeUnits) {
    hash ^= unit;
    hash = (hash * 0x100000001b3) & 0xffffffffffffffff;
  }
  return 'space:channel-${hash.toRadixString(16).padLeft(16, '0')}';
}
