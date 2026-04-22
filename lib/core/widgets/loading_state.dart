import 'package:flutter/material.dart';

/// A shared loading-state placeholder with a calm, accessible presentation.
class LoadingState extends StatelessWidget {
  const LoadingState({
    super.key,
    required this.message,
    this.hint,
    this.icon = Icons.hourglass_top_rounded,
  });

  /// Localised loading message, e.g. `AppLocalizations.of(context).loadingLabel`.
  final String message;

  /// Optional supporting copy that explains what is happening next.
  final String? hint;

  /// Decorative icon shown above the loading copy.
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final semanticsLabel = [message, if (hint != null) hint!].join('. ');

    return Center(
      child: Semantics(
        liveRegion: true,
        label: semanticsLabel,
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
                      color: theme.colorScheme.secondaryContainer,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: ExcludeSemantics(
                        child: Icon(
                          icon,
                          size: 28,
                          color: theme.colorScheme.onSecondaryContainer,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),
                  const CircularProgressIndicator(),
                  const SizedBox(height: 20),
                  Text(
                    message,
                    style: theme.textTheme.titleMedium,
                    textAlign: TextAlign.center,
                  ),
                  if (hint != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      hint!,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
