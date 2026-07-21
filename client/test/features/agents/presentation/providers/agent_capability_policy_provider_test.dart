import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/agents/presentation/providers/agent_capability_policy_provider.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
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

const _weaverDisabledSnapshot = WorkspaceCapabilitySnapshot(
  shellAccess: WorkspaceCapabilityState(
    capability: WorkspaceCapability.shellAccess,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  chat: WorkspaceCapabilityState(
    capability: WorkspaceCapability.chat,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  files: WorkspaceCapabilityState(
    capability: WorkspaceCapability.files,
    readiness: WorkspaceCapabilityReadiness.ready,
    policyState: WorkspaceCapabilityPolicyState.allowed,
  ),
  calendar: WorkspaceCapabilityState(
    capability: WorkspaceCapability.calendar,
    readiness: WorkspaceCapabilityReadiness.unavailable,
    policyState: WorkspaceCapabilityPolicyState.unavailable,
  ),
  boards: WorkspaceCapabilityState(
    capability: WorkspaceCapability.boards,
    readiness: WorkspaceCapabilityReadiness.unavailable,
    policyState: WorkspaceCapabilityPolicyState.unavailable,
  ),
  agentRuntimeControl: WorkspaceCapabilityState(
    capability: WorkspaceCapability.agentRuntimeControl,
    readiness: WorkspaceCapabilityReadiness.unavailable,
    policyState: WorkspaceCapabilityPolicyState.disabled,
    grantedCapabilities: ['weaver.exec_disabled'],
  ),
);

void main() {
  group('agentCapabilityPolicyProvider', () {
    test('marks owner/admin profiles as capability managers', () async {
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) async => _ownerProfile),
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) async => _weaverDisabledSnapshot,
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(userProfileProvider.future);
      await container.read(weaveApiWorkspaceCapabilitySnapshotProvider.future);
      final policy = container.read(agentCapabilityPolicyProvider).requireValue;

      expect(policy.canManageCapabilities, isTrue);
      expect(policy.canStartAnyCapability, isFalse);
      expect(policy.isFailClosed, isFalse);
      expect(
        policy.stateFor(AgentCapability.personalAssistant).availability,
        AgentCapabilityAvailability.disabledByPolicy,
      );
    });

    test('keeps ordinary members read-only', () async {
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) async => _memberProfile),
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) async => _weaverDisabledSnapshot,
          ),
        ],
      );
      addTearDown(container.dispose);

      await container.read(userProfileProvider.future);
      await container.read(weaveApiWorkspaceCapabilitySnapshotProvider.future);
      final policy = container.read(agentCapabilityPolicyProvider).requireValue;

      expect(policy.canManageCapabilities, isFalse);
      expect(policy.canStartAnyCapability, isFalse);
      expect(policy.isFailClosed, isFalse);
    });

    test('fails closed while profile roles are unresolved', () {
      final unresolvedProfile = Completer<UserProfile?>();
      final unresolvedCapabilities = Completer<WorkspaceCapabilitySnapshot>();
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) => unresolvedProfile.future),
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) => unresolvedCapabilities.future,
          ),
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
