import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';

final agentCapabilityPolicyProvider =
    Provider<AsyncValue<AgentCapabilityPolicy>>((ref) {
      final profile = ref.watch(userProfileProvider);

      return switch (profile) {
        AsyncData(value: final user) => AsyncData(
          AgentCapabilityPolicy.preview(
            canManageCapabilities: user?.canAdministerWorkspace ?? false,
          ),
        ),
        AsyncError() => AsyncData(
          AgentCapabilityPolicy.failClosed(canManageCapabilities: false),
        ),
        _ => AsyncData(
          AgentCapabilityPolicy.failClosed(canManageCapabilities: false),
        ),
      };
    });
