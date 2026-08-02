import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/domain/entities/channel_workspace.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

final channelWorkspacePreviewFacadeProvider =
    Provider<ChannelWorkspacePreviewFacade>(
      (ref) => const ChannelWorkspacePreviewFacade(),
    );

class ChannelWorkspacePreviewFacade {
  const ChannelWorkspacePreviewFacade();

  bool supportsWorkspaceTabs(ChatConversation conversation) {
    return !conversation.isDirectMessage;
  }

  ChannelWorkspacePreview previewForChannel(ChatConversation conversation) {
    return ChannelWorkspacePreview.forConversation(conversation);
  }
}
