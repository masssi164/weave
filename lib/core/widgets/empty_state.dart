import 'package:flutter/material.dart';

/// A shared empty-state placeholder with calm, screen-reader-friendly chrome.
///
/// Use [message] for the short state title and [guidance] for next-step copy.
/// The decorative icon is excluded from semantics while the text and optional
/// action remain available to assistive technologies.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.message,
    this.guidance,
    this.icon = Icons.inbox_outlined,
    this.actionLabel,
    this.onAction,
  });

  /// Localised empty-state title, e.g. `No conversations yet`.
  final String message;

  /// Optional localised supporting copy that tells the user what happens next.
  final String? guidance;

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
    return LayoutBuilder(
      builder: (context, constraints) {
        final minHeight = constraints.maxHeight.isFinite
            ? (constraints.maxHeight - 32)
                  .clamp(0.0, double.infinity)
                  .toDouble()
            : 0.0;

        return SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: minHeight),
            child: Center(
              child: Semantics(
                container: true,
                liveRegion: true,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 360),
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          DecoratedBox(
                            decoration: BoxDecoration(
                              color: theme.colorScheme.surfaceContainerHighest,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: ExcludeSemantics(
                                child: Icon(
                                  icon,
                                  size: 28,
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 20),
                          Semantics(
                            header: true,
                            child: Text(
                              message,
                              style: theme.textTheme.titleMedium,
                              textAlign: TextAlign.center,
                            ),
                          ),
                          if (guidance != null) ...[
                            const SizedBox(height: 8),
                            Text(
                              guidance!,
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                              textAlign: TextAlign.center,
                            ),
                          ],
                          if (actionLabel != null && onAction != null) ...[
                            const SizedBox(height: 24),
                            FilledButton.tonal(
                              onPressed: onAction,
                              style: FilledButton.styleFrom(
                                minimumSize: const Size(48, 48),
                              ),
                              child: Text(actionLabel!),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
