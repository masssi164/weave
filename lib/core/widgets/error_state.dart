import 'package:flutter/material.dart';
import 'package:weave/core/a11y/semantic_button.dart';

/// A shared error-state placeholder with friendly guidance and recovery.
///
/// Use [message] for a short user-facing title and [guidance] for details or
/// next steps. Keep raw technical failures out of [message] so primary UI stays
/// understandable.
class ErrorState extends StatelessWidget {
  const ErrorState({
    super.key,
    required this.message,
    this.guidance,
    this.retryLabel,
    this.onRetry,
  });

  /// Localised user-facing error title.
  final String message;

  /// Optional localised details or next-step guidance.
  final String? guidance;

  /// Label for the retry button, from [AppLocalizations].
  final String? retryLabel;

  /// Callback when the user taps the retry button.
  final VoidCallback? onRetry;

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
                              color: theme.colorScheme.errorContainer,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: ExcludeSemantics(
                                child: Icon(
                                  Icons.error_outline,
                                  size: 28,
                                  color: theme.colorScheme.onErrorContainer,
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
                          if (onRetry != null && retryLabel != null) ...[
                            const SizedBox(height: 24),
                            AccessibleButton(
                              onPressed: onRetry,
                              semanticLabel: retryLabel!,
                              child: Text(retryLabel!),
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
