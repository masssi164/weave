import 'package:flutter/material.dart';

/// An empty-state placeholder with an icon and a localised message.
///
/// Decorative icon is excluded from semantics; only the [message] text
/// is announced.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.message,
    this.icon = Icons.inbox_outlined,
    this.actionLabel,
    this.onAction,
  });

  /// Localised message, e.g. `AppLocalizations.of(context).emptyStateLabel`.
  final String message;

  /// Decorative icon shown above the message.
  final IconData icon;

  /// Optional CTA label. If provided together with [onAction], a button
  /// is rendered below the message.
  final String? actionLabel;

  /// Callback for the optional CTA.
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ExcludeSemantics(
              child: Icon(
                icon,
                size: 64,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              message,
              style: theme.textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 24),
              FilledButton.tonal(
                onPressed: onAction,
                style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                child: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
