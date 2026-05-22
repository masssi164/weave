import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/provider_stack_status.dart';
import 'package:weave/features/chat/domain/entities/channel_workspace.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

final channelWorkspacePreviewFacadeProvider =
    Provider<ChannelWorkspacePreviewFacade>(
      (ref) => const ChannelWorkspacePreviewFacade(),
    );

class ChannelWorkspacePreviewFacade {
  const ChannelWorkspacePreviewFacade();

  bool supportsWorkspaceTabs(ChatConversation conversation) {
    return !conversation.isDirectMessage && !conversation.isAiChat;
  }

  ChannelWorkspacePreview previewForChannel(
    ChatConversation conversation, {
    ProviderStackStatus? providerStack,
  }) {
    return ChannelWorkspacePreview.forConversation(
      conversation,
      providerStack: providerStack,
    );
  }
}
