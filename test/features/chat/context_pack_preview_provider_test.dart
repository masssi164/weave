import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/context_graph.dart';
import 'package:weave/features/chat/presentation/providers/context_pack_preview_provider.dart';

void main() {
  test('room context pack is explicit, explainable, and fail-closed', () {
    const conversation = ChatConversation(
      id: '!project:home.internal',
      title: 'Project channel',
      previewType: ChatConversationPreviewType.text,
      unreadCount: 0,
      isInvite: false,
      isDirectMessage: false,
    );

    final preview = const ContextPackPreviewFacade().previewForRoom(
      conversation,
    );

    expect(preview.id, 'context-pack:${conversation.id}');
    expect(preview.agentUseEnabled, isFalse);
    expect(preview.backgroundRoomReadingEnabled, isFalse);
    expect(preview.isFailClosedForAgentUse, isTrue);
    expect(preview.edges, hasLength(3));
    expect(preview.includedItems, hasLength(1));
    expect(preview.includedItems.single.scope, ContextGraphScope.currentRoom);
    expect(
      preview.availableItems.map((item) => item.scope),
      containsAll(<ContextGraphScope>{
        ContextGraphScope.selectedFiles,
        ContextGraphScope.linkedTasks,
        ContextGraphScope.recentDecisions,
      }),
    );
    expect(
      preview.items.every((item) => item.evidence.length == 1),
      isTrue,
      reason: 'Every preview item must explain why it is or is not included.',
    );
  });
}
