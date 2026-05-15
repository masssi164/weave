import 'package:flutter/material.dart';
import 'package:weave/features/guests/domain/entities/guest_preview.dart';

class GuestAccessPreviewCard extends StatelessWidget {
  const GuestAccessPreviewCard({
    super.key,
    required this.guests,
    this.title = 'Guest access preview',
    this.description =
        'Invite and restricted-access states are preview-only. Guest identities stay visibly separate from full members, and missing access is explained without exposing internal policy details.',
  });

  final List<GuestPreviewProfile> guests;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      container: true,
      label:
          '$title. Hidden by default for Release 1. Guests are distinct from members and only see explicitly granted capabilities.',
      child: Card(
        elevation: 0,
        color: theme.colorScheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.badge_outlined,
                    color: theme.colorScheme.primary,
                    semanticLabel: 'Guest preview icon',
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: theme.textTheme.titleLarge),
                        const SizedBox(height: 8),
                        Text(
                          description,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              for (final guest in guests) ...[
                _GuestPreviewTile(guest: guest),
                if (guest != guests.last) const Divider(height: 24),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _GuestPreviewTile extends StatelessWidget {
  const _GuestPreviewTile({required this.guest});

  final GuestPreviewProfile guest;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final statusLabel = _statusLabel(guest.status);
    final capabilityText = _capabilityText(guest.allowedCapabilities);

    return Semantics(
      container: true,
      label:
          'Guest identity ${guest.displayName}, $statusLabel. $capabilityText. ${guest.missingAccessMessages.join(' ')}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 8,
            children: [
              Text(
                guest.displayName,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              Chip(
                label: Text(statusLabel),
                avatar: Icon(_statusIcon(guest.status), size: 18),
                side: BorderSide(color: theme.colorScheme.outlineVariant),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            'Guest identity · ${guest.email}',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 10),
          Text(capabilityText, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          for (final message in guest.missingAccessMessages)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const ExcludeSemantics(
                    child: Icon(Icons.lock_outline, size: 18),
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: Text(message)),
                ],
              ),
            ),
          if (guest.canSeeMemberOnlyAffordances)
            const Text('Member/admin affordances allowed by policy.')
          else
            Text(
              'Owner, admin, and member-only affordances are hidden for this guest.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
        ],
      ),
    );
  }

  static String _statusLabel(GuestPreviewStatus status) {
    return switch (status) {
      GuestPreviewStatus.pending => 'Pending invitation',
      GuestPreviewStatus.active => 'Active guest',
      GuestPreviewStatus.disabled => 'Disabled guest',
      GuestPreviewStatus.expired => 'Expired invitation',
    };
  }

  static IconData _statusIcon(GuestPreviewStatus status) {
    return switch (status) {
      GuestPreviewStatus.pending => Icons.schedule_outlined,
      GuestPreviewStatus.active => Icons.verified_user_outlined,
      GuestPreviewStatus.disabled => Icons.block_outlined,
      GuestPreviewStatus.expired => Icons.event_busy_outlined,
    };
  }

  static String _capabilityText(Set<GuestAccessCapability> capabilities) {
    if (capabilities.isEmpty) {
      return 'Allowed access: none yet.';
    }

    final labels = capabilities
        .map(
          (capability) => switch (capability) {
            GuestAccessCapability.chat => 'chat',
            GuestAccessCapability.files => 'files',
            GuestAccessCapability.calendar => 'calendar',
            GuestAccessCapability.memberDirectory => 'member directory',
            GuestAccessCapability.admin => 'admin controls',
          },
        )
        .join(', ');
    return 'Allowed access: $labels.';
  }
}
