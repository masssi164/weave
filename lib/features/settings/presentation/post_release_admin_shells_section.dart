import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/settings/presentation/post_release_feature_flags.dart';

class PostReleaseAdminShellsSection extends ConsumerWidget {
  const PostReleaseAdminShellsSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final flags = ref.watch(postReleaseFeatureFlagsProvider);
    if (!flags.hasEnabledShell) {
      return const SizedBox.shrink();
    }

    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Post-release workspace shells',
          style: theme.textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        Text(
          'These shells stay hidden for Release 1 unless a feature flag enables them.',
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 12),
        if (flags.guestPortal) ...[
          const _StatusShellCard(
            icon: Icons.badge_outlined,
            title: 'Guest Portal',
            status: 'Guest access shell',
            statusKind: _StatusKind.pending,
            body:
                'Guest identity is labeled separately from workspace members. Owner, admin, and member-only actions stay hidden until backend guest capabilities allow them.',
            semanticsLabel:
                'Guest Portal shell. Guest access shell. Guest identity is separate and admin affordances are hidden.',
          ),
          const SizedBox(height: 12),
        ],
        if (flags.interopStatus) ...[
          const _StatusShellCard(
            icon: Icons.hub_outlined,
            title: 'External connections',
            status: 'Disabled by default',
            statusKind: _StatusKind.disabled,
            body:
                'Interop providers are listed here only after the backend reports capabilities. Provider secrets are never collected in the Flutter client, and external-provider data movement is not shown as end-to-end encrypted.',
            semanticsLabel:
                'External connections admin shell. Disabled by default. Provider secrets are never collected in the Flutter client.',
          ),
          const SizedBox(height: 12),
        ],
        if (flags.migrationDryRun)
          const _StatusShellCard(
            icon: Icons.fact_check_outlined,
            title: 'Migration dry-run report',
            status: 'Read-only preview',
            statusKind: _StatusKind.degraded,
            body:
                'Admins can review inventory, unmappable content, consent scopes, mappings, duration, and rate-limit budget here once the backend report exists. No import starts from this preview.',
            semanticsLabel:
                'Migration dry-run report shell. Read-only preview. No import starts from this preview.',
          ),
      ],
    );
  }
}

enum _StatusKind { disabled, pending, degraded }

class _StatusShellCard extends StatelessWidget {
  const _StatusShellCard({
    required this.icon,
    required this.title,
    required this.status,
    required this.statusKind,
    required this.body,
    required this.semanticsLabel,
  });

  final IconData icon;
  final String title;
  final String status;
  final _StatusKind statusKind;
  final String body;
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = switch (statusKind) {
      _StatusKind.disabled => (
        theme.colorScheme.surfaceContainerHighest,
        theme.colorScheme.onSurfaceVariant,
      ),
      _StatusKind.pending => (
        theme.colorScheme.secondaryContainer,
        theme.colorScheme.onSecondaryContainer,
      ),
      _StatusKind.degraded => (
        theme.colorScheme.tertiaryContainer,
        theme.colorScheme.onTertiaryContainer,
      ),
    };

    return Semantics(
      container: true,
      liveRegion: true,
      label: semanticsLabel,
      child: ExcludeSemantics(
        child: Card(
          elevation: 0,
          color: theme.colorScheme.surfaceContainerLow,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(24),
            side: BorderSide(color: theme.colorScheme.outlineVariant),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(icon),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(title, style: theme.textTheme.titleMedium),
                    ),
                    DecoratedBox(
                      decoration: BoxDecoration(
                        color: colors.$1,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        child: Text(
                          status,
                          style: theme.textTheme.labelMedium?.copyWith(
                            color: colors.$2,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  body,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
