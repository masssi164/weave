import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/agents/presentation/providers/agent_capability_policy_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';

const _ownerProfile = UserProfile(
  userId: 'owner-1',
  username: 'owner',
  displayName: 'Workspace Owner',
  locale: 'en',
  timezone: 'Europe/Berlin',
  emailVerified: true,
  roles: ['owner'],
);

const _memberProfile = UserProfile(
  userId: 'member-1',
  username: 'member',
  displayName: 'Workspace Member',
  locale: 'en',
  timezone: 'Europe/Berlin',
  emailVerified: true,
  roles: ['member'],
);

void main() {
  group('agentCapabilityPolicyProvider', () {
    test('marks owner/admin profiles as capability managers', () async {
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) async => _ownerProfile),
        ],
      );
      addTearDown(container.dispose);

      await container.read(userProfileProvider.future);
      final policy = container.read(agentCapabilityPolicyProvider).requireValue;

      expect(policy.canManageCapabilities, isTrue);
      expect(policy.canStartAnyCapability, isFalse);
      expect(policy.isFailClosed, isFalse);
    });

    test('keeps ordinary members read-only', () async {
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) async => _memberProfile),
        ],
      );
      addTearDown(container.dispose);

      await container.read(userProfileProvider.future);
      final policy = container.read(agentCapabilityPolicyProvider).requireValue;

      expect(policy.canManageCapabilities, isFalse);
      expect(policy.canStartAnyCapability, isFalse);
      expect(policy.isFailClosed, isFalse);
    });

    test('fails closed while profile roles are unresolved', () {
      final unresolvedProfile = Completer<UserProfile?>();
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) => unresolvedProfile.future),
        ],
      );
      addTearDown(container.dispose);

      final policy = container.read(agentCapabilityPolicyProvider).requireValue;

      expect(policy.isFailClosed, isTrue);
      expect(policy.canManageCapabilities, isFalse);
      expect(
        policy.capabilities.map((capability) => capability.availability),
        everyElement(AgentCapabilityAvailability.blocked),
      );
    });
  });
}
