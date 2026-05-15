import 'package:flutter/material.dart';
import 'package:weave/core/widgets/state_panel.dart';

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
    this.semanticLabel,
  });

  /// Localised user-facing error title.
  final String message;

  /// Optional localised details or next-step guidance.
  final String? guidance;

  /// Label for the retry button, from [AppLocalizations].
  final String? retryLabel;

  /// Callback when the user taps the retry button.
  final VoidCallback? onRetry;

  /// Optional full screen-reader label for the live error-state region.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return StatePanel(
      variant: StatePanelVariant.error,
      message: message,
      guidance: guidance,
      actionLabel: retryLabel,
      onAction: onRetry,
      semanticLabel: semanticLabel,
    );
  }
}
