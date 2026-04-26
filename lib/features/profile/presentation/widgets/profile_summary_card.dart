import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ProfileSummaryCard extends ConsumerWidget {
  const ProfileSummaryCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final profile = ref.watch(userProfileProvider);

    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: profile.when(
          loading: () => LoadingState(message: l10n.loadingLabel),
          error: (error, _) => ErrorState(
            message: l10n.profileLoadFailure,
            retryLabel: l10n.retryButton,
            onRetry: () => ref.invalidate(userProfileProvider),
          ),
          data: (profile) => profile == null
              ? Text(
                  l10n.profileSignedOutMessage,
                  style: theme.textTheme.bodyMedium,
                )
              : _ProfileDetails(profile: profile),
        ),
      ),
    );
  }
}

class _ProfileDetails extends StatelessWidget {
  const _ProfileDetails({required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final roles = profile.roles.isEmpty ? '—' : profile.roles.join(', ');
    final groups = profile.groups.isEmpty ? '—' : profile.groups.join(', ');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.profileSectionTitle, style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        Text(
          l10n.profileSectionDescription,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 20),
        MergeSemantics(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _ProfileRow(
                label: l10n.profileDisplayNameLabel,
                value: profile.displayName,
              ),
              _ProfileRow(
                label: l10n.profileUsernameLabel,
                value: profile.username,
              ),
              _ProfileRow(
                label: l10n.profileEmailLabel,
                value: profile.email ?? '—',
              ),
              _ProfileRow(
                label: l10n.profileEmailVerifiedLabel,
                value: profile.emailVerified
                    ? l10n.profileEmailVerifiedYes
                    : l10n.profileEmailVerifiedNo,
              ),
              _ProfileRow(
                label: l10n.profileLocaleLabel,
                value: profile.locale,
              ),
              _ProfileRow(
                label: l10n.profileTimezoneLabel,
                value: profile.timezone,
              ),
              _ProfileRow(label: l10n.profileRolesLabel, value: roles),
              _ProfileRow(label: l10n.profileGroupsLabel, value: groups),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Semantics(
          liveRegion: true,
          child: Text(
            l10n.profileEditingBlockedMessage,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
    );
  }
}

class _ProfileRow extends StatelessWidget {
  const _ProfileRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: theme.textTheme.labelLarge),
          ),
          const SizedBox(width: 12),
          Expanded(child: Text(value, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}
