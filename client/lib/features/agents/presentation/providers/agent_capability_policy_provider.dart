import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

final agentCapabilityPolicyProvider =
    Provider<AsyncValue<AgentCapabilityPolicy>>((ref) {
      final profile = ref.watch(userProfileProvider);
      final capabilities = ref.watch(
        weaveApiWorkspaceCapabilitySnapshotProvider,
      );

      final canManageCapabilities = profile.maybeWhen(
        data: (user) => user?.canAdministerWorkspace ?? false,
        orElse: () => false,
      );

      return switch (capabilities) {
        AsyncData(value: final snapshot?) => AsyncData(
          AgentCapabilityPolicy.fromWorkspaceCapabilities(
            canManageCapabilities: canManageCapabilities,
            workspaceCapabilities: snapshot,
          ),
        ),
        AsyncData() => AsyncData(
          AgentCapabilityPolicy.failClosed(
            canManageCapabilities: canManageCapabilities,
          ),
        ),
        AsyncError() => AsyncData(
          AgentCapabilityPolicy.failClosed(
            canManageCapabilities: canManageCapabilities,
          ),
        ),
        _ => AsyncData(
          AgentCapabilityPolicy.failClosed(
            canManageCapabilities: canManageCapabilities,
          ),
        ),
      };
    });
