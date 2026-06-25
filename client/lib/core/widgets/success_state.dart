import 'package:flutter/material.dart';
import 'package:weave/core/widgets/state_panel.dart';

/// A shared success-state placeholder with friendly guidance and recovery.
///
/// Use [message] for a short user-facing confirmation and [guidance] for
/// details or next steps. The optional action keeps follow-up flows consistent
/// with other shared state panels.
class SuccessState extends StatelessWidget {
  const SuccessState({
    super.key,
    required this.message,
    this.guidance,
    this.icon = Icons.check_circle_outline,
    this.actionLabel,
    this.onAction,
    this.semanticLabel,
    this.liveRegion = true,
  });

  /// Localised user-facing success title.
  final String message;

  /// Optional localised details or next-step guidance.
  final String? guidance;

  /// Decorative icon shown above the message.
  final IconData icon;

  /// Optional follow-up action label.
  final String? actionLabel;

  /// Callback for the optional follow-up action.
  final VoidCallback? onAction;

  /// Optional full screen-reader label for the live success-state region.
  final String? semanticLabel;

  /// Whether screen readers should announce this success state as a live
  /// update.
  final bool liveRegion;

  @override
  Widget build(BuildContext context) {
    return StatePanel(
      variant: StatePanelVariant.success,
      message: message,
      guidance: guidance,
      icon: icon,
      actionLabel: actionLabel,
      onAction: onAction,
      semanticLabel: semanticLabel,
      liveRegion: liveRegion,
    );
  }
}
