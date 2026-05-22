import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/agent_chat_preview.dart';
import 'package:weave/features/chat/presentation/providers/agent_chat_preview_provider.dart';

void main() {
  test(
    'agent chat previews remain gated before consent/audit runtime exists',
    () {
      final container = ProviderContainer.test();
      addTearDown(container.dispose);

      final previews = container.read(agentChatPreviewProvider);

      expect(previews, hasLength(2));
      expect(
        previews.map((preview) => preview.kind),
        containsAll(<AgentChatPreviewKind>{
          AgentChatPreviewKind.personalAssistant,
          AgentChatPreviewKind.channelAgent,
        }),
      );
      expect(
        previews.every((preview) => preview.canStart == false),
        isTrue,
        reason:
            'The first agent surface is preview-only until backend policy, consent, and audit gates are connected.',
      );
      expect(
        previews.map((preview) => preview.availability),
        containsAll(<AgentChatAvailability>{
          AgentChatAvailability.previewOnly,
          AgentChatAvailability.adminSetupRequired,
        }),
      );
    },
  );
}
