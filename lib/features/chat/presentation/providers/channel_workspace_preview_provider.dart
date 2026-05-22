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
    DevopsSummary? devopsSummary,
    OfficeCapabilities? officeCapabilities,
    bool providerReadinessLoading = false,
    bool providerReadinessUnavailable = false,
  }) {
    return ChannelWorkspacePreview.forConversation(
      conversation,
      devopsSummary: devopsSummary,
      officeCapabilities: officeCapabilities,
      providerReadinessLoading: providerReadinessLoading,
      providerReadinessUnavailable: providerReadinessUnavailable,
    );
  }
}
