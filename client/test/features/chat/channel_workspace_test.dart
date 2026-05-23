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

  test('models a governed channel workspace with provider seams', () {
    final preview = ChannelWorkspacePreview.forConversation(channel);

    expect(preview.contextId, 'channel:!general:home.internal');
    expect(preview.isChannelWorkspaceGoverned, isTrue);
    expect(preview.surfaces.map((surface) => surface.kind), [
      ChannelWorkspaceSurfaceKind.chat,
      ChannelWorkspaceSurfaceKind.files,
      ChannelWorkspaceSurfaceKind.boards,
      ChannelWorkspaceSurfaceKind.calendar,
      ChannelWorkspaceSurfaceKind.meetings,
    ]);
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.chat).availability,
      ChannelWorkspaceSurfaceAvailability.available,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.files).providerContractId,
      'weave-files-channel-link',
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.files).availability,
      ChannelWorkspaceSurfaceAvailability.available,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.boards).availability,
      ChannelWorkspaceSurfaceAvailability.adminSetupRequired,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.calendar).availability,
      ChannelWorkspaceSurfaceAvailability.gated,
    );
    expect(
      preview.surface(ChannelWorkspaceSurfaceKind.meetings).providerContractId,
      'livekit-meetings-channel-gate',
    );
    expect(preview.meetingPreview.contextId, preview.contextId);
    expect(preview.meetingPreview.isFailClosed, isTrue);
    expect(preview.meetingPreview.requiresExplicitConsent, isTrue);
    expect(preview.meetingPreview.backgroundRoomReadingEnabled, isFalse);
    expect(preview.meetingPreview.contextItems.map((item) => item.kind), [
      ChannelMeetingContextItemKind.agenda,
      ChannelMeetingContextItemKind.files,
      ChannelMeetingContextItemKind.decisions,
      ChannelMeetingContextItemKind.tasks,
      ChannelMeetingContextItemKind.followUpEvidence,
    ]);
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
