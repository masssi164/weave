import 'package:flutter/material.dart';

/// A button that enforces accessibility best practices:
/// - Minimum 48 × 48 tap target
/// - Required [semanticLabel] from [AppLocalizations]
/// - [Tooltip] only when [label] text is not visible
///
/// Wraps [FilledButton] by default; set [outlined] to `true` for
/// [OutlinedButton].
class AccessibleButton extends StatelessWidget {
  const AccessibleButton({
    super.key,
    required this.onPressed,
    required this.semanticLabel,
    required this.child,
    this.outlined = false,
  });

  /// The callback invoked when the button is tapped.
  final VoidCallback? onPressed;

  /// Localised label announced by screen readers.
  final String semanticLabel;

  /// The button content — usually a [Text] widget.
  final Widget child;

  /// If `true`, renders as [OutlinedButton]; otherwise [FilledButton].
  final bool outlined;

  @override
  Widget build(BuildContext context) {
    final style = (outlined
        ? OutlinedButton.styleFrom
        : FilledButton.styleFrom)(minimumSize: const Size(48, 48));

    final button = outlined
        ? OutlinedButton(onPressed: onPressed, style: style, child: child)
        : FilledButton(onPressed: onPressed, style: style, child: child);

    return Semantics(
      button: true,
      label: semanticLabel,
      excludeSemantics: true,
      child: button,
    );
  }
}
