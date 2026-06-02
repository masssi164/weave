import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/channel_workspace.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/presentation/providers/channel_workspace_preview_provider.dart';

void main() {
  const channel = ChatConversation(
    id: '!general:home.internal',
    title: 'General',
    previewType: ChatConversationPreviewType.text,
    unreadCount: 0,
    isInvite: false,
    isDirectMessage: false,
  );

  test('models a governed Space control room without preview states', () {
    final preview = ChannelWorkspacePreview.forConversation(channel);

    expect(preview.contextId, startsWith('space:channel-'));
    expect(preview.contextId, isNot(contains('!general')));
    expect(preview.contextId, isNot(contains('home.internal')));
    expect(preview.routePath, startsWith('/spaces/space-channel-'));
    expect(preview.spaceEvidenceRef, startsWith('evidence:space-channel-'));
    expect(
      preview.finalDecisionEvidenceRef,
      startsWith('evidence:space-channel-'),
    );
    expect(preview.isChannelWorkspaceGoverned, isTrue);
    expect(preview.surfaces.map((surface) => surface.kind), [
      ChannelWorkspaceSurfaceKind.chat,
      ChannelWorkspaceSurfaceKind.decisions,
      ChannelWorkspaceSurfaceKind.files,
      ChannelWorkspaceSurfaceKind.boards,
      ChannelWorkspaceSurfaceKind.calendar,
      ChannelWorkspaceSurfaceKind.meetings,
      ChannelWorkspaceSurfaceKind.weaver,
    ]);
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.chat).availability,
      ChannelWorkspaceSurfaceAvailability.available,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.chat).providerContractId,
      'weave-chat-conversation',
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.decisions).availability,
      ChannelWorkspaceSurfaceAvailability.available,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.files).availability,
      ChannelWorkspaceSurfaceAvailability.notConfigured,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.boards).availability,
      ChannelWorkspaceSurfaceAvailability.degraded,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.calendar).availability,
      ChannelWorkspaceSurfaceAvailability.disabledByPolicy,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.meetings).providerContractId,
      'weave-meetings-channel-capability',
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.weaver).providerContractId,
      'weave-weaver-channel-scout',
    );
    expect(
      preview.surfaces.map((surface) => surface.providerContractId),
      everyElement(isNot(contains('preview'))),
    );
    expect(
      preview.surfaces.map((surface) => surface.providerContractId),
      everyElement(isNot(contains('matrix'))),
    );
    expect(
      preview.surfaces.map((surface) => surface.providerContractId),
      everyElement(startsWith('weave-')),
    );
    expect(
      preview.surfaces.map((surface) => surface.canonicalObjectRef),
      everyElement(startsWith('weave:space-channel-')),
    );
    expect(
      preview.surfaces.map((surface) => surface.supportSafeEvidenceRef),
      everyElement(startsWith('evidence:space-channel-')),
    );
    expect(
      preview.surfaces.every((surface) => surface.hasSupportSafeEvidence),
      isTrue,
    );
    expect(
      preview.surfaces.map((surface) => surface.canonicalObjectRef),
      everyElement(isNot(contains('!general'))),
    );
    expect(
      preview.surfaces.map((surface) => surface.supportSafeEvidenceRef),
      everyElement(isNot(contains('home.internal'))),
    );
    expect(preview.meetingPreview.contextId, preview.contextId);
    expect(preview.weaverScoutPreview.contextId, preview.contextId);
    expect(preview.meetingPreview.isFailClosed, isTrue);
    expect(preview.meetingPreview.requiresExplicitConsent, isTrue);
    expect(preview.meetingPreview.backgroundRoomReadingEnabled, isFalse);
    expect(preview.meetingPreview.canLinkFromChannelOrCalendar, isTrue);
    expect(preview.meetingPreview.hasDocumentedEncryptionBoundaries, isTrue);
    expect(preview.meetingPreview.hasAccessibleJoinContract, isTrue);
    expect(preview.meetingPreview.preventsVagueSecurityClaims, isTrue);
    expect(preview.meetingPreview.attachPoints.map((point) => point.kind), [
      ChannelMeetingAttachPointKind.channel,
      ChannelMeetingAttachPointKind.calendarEvent,
      ChannelMeetingAttachPointKind.thread,
    ]);
    expect(preview.weaverScoutPreview.isGovernedReadOnlyScout, isTrue);
    expect(preview.weaverScoutPreview.readOnly, isTrue);
    expect(preview.weaverScoutPreview.proposalOnly, isTrue);
    expect(preview.weaverScoutPreview.backgroundRoomReadingEnabled, isFalse);
    expect(
      preview.weaverScoutPreview.requiresApprovalReceiptsForWrites,
      isTrue,
    );
    expect(preview.weaverScoutPreview.approvalReceipts, isEmpty);
    expect(
      preview.weaverScoutPreview.capabilities.map(
        (capability) => capability.kind,
      ),
      [
        ChannelWeaverScoutCapabilityKind.summarizeAllowedContext,
        ChannelWeaverScoutCapabilityKind.citeSources,
        ChannelWeaverScoutCapabilityKind.proposeOnly,
        ChannelWeaverScoutCapabilityKind.approvalReceiptRequired,
      ],
    );
    expect(
      preview.weaverScoutPreview.allowedSources.map((source) => source.kind),
      [
        ChannelWeaverScoutSourceKind.message,
        ChannelWeaverScoutSourceKind.decision,
        ChannelWeaverScoutSourceKind.file,
        ChannelWeaverScoutSourceKind.task,
        ChannelWeaverScoutSourceKind.meeting,
      ],
    );
    expect(
      preview.weaverScoutPreview.allowedSources.every(
        (source) => source.isCitable,
      ),
      isTrue,
    );
    expect(preview.meetingPreview.contextItems.map((item) => item.kind), [
      ChannelMeetingContextItemKind.agenda,
      ChannelMeetingContextItemKind.files,
      ChannelMeetingContextItemKind.decisions,
      ChannelMeetingContextItemKind.tasks,
      ChannelMeetingContextItemKind.followUpEvidence,
    ]);
    expect(
      preview.meetingPreview.encryptionBoundaries.map(
        (boundary) => boundary.kind,
      ),
      ChannelMeetingEncryptionBoundaryKind.values,
    );
    expect(
      preview.meetingPreview.uxRequirements.map(
        (requirement) => requirement.kind,
      ),
      ChannelMeetingUxRequirementKind.values,
    );
    expect(
      preview.meetingPreview.controls.every((control) => !control.enabled),
      isTrue,
    );
  });

  test('enables workspace tabs only for channel conversations', () {
    const facade = ChannelWorkspacePreviewFacade();
    const directMessage = ChatConversation(
      id: '@alex:home.internal',
      title: 'Alex',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: true,
    );

    expect(facade.supportsWorkspaceTabs(channel), isTrue);
    expect(facade.supportsWorkspaceTabs(directMessage), isFalse);
  });
}
