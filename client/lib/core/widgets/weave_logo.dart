import 'package:flutter/material.dart';

/// Shared Weave brand mark used on entry surfaces and light-touch branding.
class WeaveLogo extends StatelessWidget {
  const WeaveLogo({
    super.key,
    required this.semanticLabel,
    this.width = 128,
    this.framed = true,
    this.excludeFromSemantics = false,
    this.padding = const EdgeInsets.all(16),
  });

  static const assetPath = 'assets/images/weave_logo.png';
  static const _aspectRatio = 11 / 6;

  final String semanticLabel;
  final double width;
  final bool framed;
  final bool excludeFromSemantics;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final image = SizedBox(
      width: width,
      child: AspectRatio(
        aspectRatio: _aspectRatio,
        child: Image.asset(
          assetPath,
          fit: BoxFit.contain,
          semanticLabel: excludeFromSemantics ? null : semanticLabel,
          excludeFromSemantics: excludeFromSemantics,
        ),
      ),
    );

    if (!framed) {
      return image;
    }

    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Padding(padding: padding, child: image),
    );
  }
}
