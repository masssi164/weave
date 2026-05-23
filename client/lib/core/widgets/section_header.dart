import 'package:flutter/material.dart';

/// A section heading that is announced as a header by screen readers,
/// using `Semantics(header: true)`.
///
/// Uses [titleMedium] from the theme's text scale so it respects
/// [TextScaler].
class SectionHeader extends StatelessWidget {
  const SectionHeader({super.key, required this.title});

  /// The heading text — should come from [AppLocalizations].
  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Semantics(
        header: true,
        child: Text(title, style: Theme.of(context).textTheme.titleMedium),
      ),
    );
  }
}
