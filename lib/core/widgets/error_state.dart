import 'package:flutter/material.dart';
import 'package:weave/core/a11y/semantic_button.dart';

/// An error-state placeholder with an icon, localised message,
/// and an optional retry button.
class ErrorState extends StatelessWidget {
  const ErrorState({
    super.key,
    required this.message,
    this.retryLabel,
    this.onRetry,
  });

  /// Localised error message.
  final String message;

  /// Label for the retry button, from [AppLocalizations].
  final String? retryLabel;

  /// Callback when the user taps the retry button.
  final VoidCallback? onRetry;

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
                Icons.error_outline,
                size: 64,
                color: theme.colorScheme.error,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              message,
              style: theme.textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
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
    );
  }
}
