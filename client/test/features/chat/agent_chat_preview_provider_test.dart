import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/agents/presentation/providers/agent_capability_policy_provider.dart';
import 'package:weave/features/chat/domain/entities/agent_chat_preview.dart';
import 'package:weave/features/chat/presentation/providers/agent_chat_preview_provider.dart';

void main() {
  test(
    'agent chat entries remain disabled before consent/audit runtime exists',
    () {
      final container = ProviderContainer.test(
        overrides: [
          agentCapabilityPolicyProvider.overrideWithValue(
            AsyncData(
              AgentCapabilityPolicy.disabled(canManageCapabilities: true),
            ),
          ),
        ],
      );
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
            'The first agent surface is disabled until backend policy, consent, and audit gates are connected.',
      );
      expect(
        previews.map((preview) => preview.availability),
        containsAll(<AgentChatAvailability>{
          AgentChatAvailability.disabledByPolicy,
          AgentChatAvailability.adminSetupRequired,
        }),
      );
    },
  );

  test('agent chat previews fail closed when policy is unresolved', () {
    final container = ProviderContainer.test(
      overrides: [
        agentCapabilityPolicyProvider.overrideWithValue(
          AsyncData(
            AgentCapabilityPolicy.failClosed(canManageCapabilities: false),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);

    final previews = container.read(agentChatPreviewProvider);

    expect(previews, hasLength(2));
    expect(previews.every((preview) => preview.canStart == false), isTrue);
    expect(
      previews.map((preview) => preview.availability),
      everyElement(AgentChatAvailability.blocked),
    );
  });
}
