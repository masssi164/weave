import 'package:flutter/material.dart';
import 'package:weave/features/agents/domain/entities/agent_capability_policy.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class AgentCapabilityPolicyCard extends StatelessWidget {
  const AgentCapabilityPolicyCard({required this.policy, super.key});

  final AgentCapabilityPolicy policy;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.admin_panel_settings_outlined,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Semantics(
                          header: true,
                          child: Text(
                            l10n.agentCapabilityPolicyTitle,
                            style: theme.textTheme.titleLarge,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          policy.canManageCapabilities
                              ? l10n.agentCapabilityPolicyAdminDescription
                              : l10n.agentCapabilityPolicyUserDescription,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              if (policy.isFailClosed) ...[
                const SizedBox(height: 12),
                _PolicyNotice(
                  icon: Icons.lock_outline,
                  text: l10n.agentCapabilityPolicyFailClosedNotice,
                ),
              ],
              const SizedBox(height: 16),
              for (final capability in policy.capabilities) ...[
                _CapabilityPolicyTile(
                  state: capability,
                  canManageCapabilities: policy.canManageCapabilities,
                ),
                const SizedBox(height: 12),
              ],
              if (policy.canManageCapabilities)
                Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: FilledButton.tonalIcon(
                    onPressed: null,
                    icon: const Icon(Icons.lock_outline),
                    label: Text(l10n.agentCapabilityPolicyManageDisabledButton),
                  ),
                )
              else
                _PolicyNotice(
                  icon: Icons.info_outline,
                  text: l10n.agentCapabilityPolicyAskAdminHint,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CapabilityPolicyTile extends StatelessWidget {
  const _CapabilityPolicyTile({
    required this.state,
    required this.canManageCapabilities,
  });

  final AgentCapabilityState state;
  final bool canManageCapabilities;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final title = _capabilityTitle(l10n, state.capability);
    final description = _capabilityDescription(l10n, state.capability);
    final status = _availabilityLabel(l10n, state.availability);

    return MergeSemantics(
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(
                    child: Icon(_capabilityIcon(state.capability)),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: theme.textTheme.titleMedium),
                        const SizedBox(height: 4),
                        Text(description, style: theme.textTheme.bodyMedium),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Chip(
                    avatar: Icon(
                      _availabilityIcon(state.availability),
                      size: 18,
                    ),
                    label: Text(status),
                  ),
                ],
              ),
              if (canManageCapabilities) ...[
                const SizedBox(height: 8),
                Text(
                  l10n.agentCapabilityPolicyAdminStateHint,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _PolicyNotice extends StatelessWidget {
  const _PolicyNotice({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return MergeSemantics(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: theme.colorScheme.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _capabilityTitle(AppLocalizations l10n, AgentCapability capability) {
  return switch (capability) {
    AgentCapability.personalAssistant =>
      l10n.agentCapabilityPersonalAssistantTitle,
    AgentCapability.channelAgent => l10n.agentCapabilityChannelAgentTitle,
  };
}

String _capabilityDescription(
  AppLocalizations l10n,
  AgentCapability capability,
) {
  return switch (capability) {
    AgentCapability.personalAssistant =>
      l10n.agentCapabilityPersonalAssistantDescription,
    AgentCapability.channelAgent => l10n.agentCapabilityChannelAgentDescription,
  };
}

String _availabilityLabel(
  AppLocalizations l10n,
  AgentCapabilityAvailability availability,
) {
  return switch (availability) {
    AgentCapabilityAvailability.previewOnly =>
      l10n.agentCapabilityAvailabilityPreviewOnly,
    AgentCapabilityAvailability.adminSetupRequired =>
      l10n.agentCapabilityAvailabilityAdminSetupRequired,
    AgentCapabilityAvailability.blocked =>
      l10n.agentCapabilityAvailabilityBlocked,
  };
}

IconData _capabilityIcon(AgentCapability capability) {
  return switch (capability) {
    AgentCapability.personalAssistant => Icons.person_search_outlined,
    AgentCapability.channelAgent => Icons.groups_outlined,
  };
}

IconData _availabilityIcon(AgentCapabilityAvailability availability) {
  return switch (availability) {
    AgentCapabilityAvailability.previewOnly => Icons.visibility_outlined,
    AgentCapabilityAvailability.adminSetupRequired =>
      Icons.admin_panel_settings,
    AgentCapabilityAvailability.blocked => Icons.block,
  };
}
