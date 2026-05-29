import 'package:flutter/material.dart';
import 'package:weave/features/guests/domain/entities/guest_preview.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class GuestAccessPreviewCard extends StatelessWidget {
  const GuestAccessPreviewCard({
    super.key,
    required this.guests,
    this.title,
    this.description,
  });

  final List<GuestPreviewProfile> guests;
  final String? title;
  final String? description;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final title = this.title ?? l10n.guestAccessPreviewTitle;
    final description = this.description ?? l10n.guestAccessPreviewDescription;
    return Semantics(
      container: true,
      label: l10n.guestAccessPreviewCardSemanticLabel(title),
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
                    semanticLabel: l10n.guestPreviewIconSemantic,
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
    final l10n = AppLocalizations.of(context);
    final statusLabel = _statusLabel(l10n, guest.status);
    final capabilityText = _capabilityText(l10n, guest.allowedCapabilities);
    final missingAccessText = guest.missingAccessMessages
        .map(l10n.guestPreviewDemoAccessNote)
        .join(' ');

    return Semantics(
      container: true,
      label: l10n.guestPreviewTileSemanticLabel(
        guest.displayName,
        statusLabel,
        capabilityText,
        missingAccessText,
      ),
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
            l10n.guestIdentityEmail(guest.email),
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
                  Expanded(
                    child: Text(l10n.guestPreviewDemoAccessNote(message)),
                  ),
                ],
              ),
            ),
          if (guest.canSeeMemberOnlyAffordances)
            Text(l10n.guestMemberAffordancesAllowed)
          else
            Text(
              l10n.guestMemberAffordancesHidden,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
        ],
      ),
    );
  }

  static String _statusLabel(AppLocalizations l10n, GuestPreviewStatus status) {
    return switch (status) {
      GuestPreviewStatus.pending => l10n.guestPreviewStatusPending,
      GuestPreviewStatus.active => l10n.guestPreviewStatusActive,
      GuestPreviewStatus.disabled => l10n.guestPreviewStatusDisabled,
      GuestPreviewStatus.expired => l10n.guestPreviewStatusExpired,
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

  static String _capabilityText(
    AppLocalizations l10n,
    Set<GuestAccessCapability> capabilities,
  ) {
    if (capabilities.isEmpty) {
      return l10n.guestAllowedAccessNone;
    }

    final labels = capabilities
        .map(
          (capability) => switch (capability) {
            GuestAccessCapability.chat => l10n.guestCapabilityChat,
            GuestAccessCapability.files => l10n.guestCapabilityFiles,
            GuestAccessCapability.calendar => l10n.guestCapabilityCalendar,
            GuestAccessCapability.memberDirectory =>
              l10n.guestCapabilityMemberDirectory,
            GuestAccessCapability.admin => l10n.guestCapabilityAdminControls,
          },
        )
        .join(', ');
    return l10n.guestAllowedAccessList(labels);
  }
}
