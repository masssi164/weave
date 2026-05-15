import 'package:flutter/material.dart';
import 'package:weave/core/widgets/state_panel.dart';

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
    this.semanticLabel,
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

  /// Optional full screen-reader label for the live empty-state region.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return StatePanel(
      variant: StatePanelVariant.empty,
      message: message,
      guidance: guidance,
      icon: icon,
      actionLabel: actionLabel,
      onAction: onAction,
      semanticLabel: semanticLabel,
      outlinedAction: true,
    );
  }
}
