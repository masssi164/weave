import 'package:flutter/material.dart';
import 'package:weave/core/widgets/state_panel.dart';

/// A shared loading-state placeholder with a calm, accessible presentation.
class LoadingState extends StatelessWidget {
  const LoadingState({
    super.key,
    required this.message,
    this.hint,
    this.icon = Icons.hourglass_top_rounded,
    this.semanticLabel,
  });

  /// Localised loading message, e.g. `AppLocalizations.of(context).loadingLabel`.
  final String message;

  /// Optional supporting copy that explains what is happening next.
  final String? hint;

  /// Decorative icon shown above the loading copy.
  final IconData icon;

  /// Optional full screen-reader label for the live loading region.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return StatePanel(
      variant: StatePanelVariant.loading,
      message: message,
      guidance: hint,
      icon: icon,
      semanticLabel: semanticLabel,
    );
  }
}
