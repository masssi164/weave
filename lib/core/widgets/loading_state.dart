import 'package:flutter/material.dart';

/// A loading indicator with a semantic live-region so screen readers
/// announce it exactly once when it appears.
class LoadingState extends StatelessWidget {
  const LoadingState({super.key, required this.message});

  /// Localised loading message, e.g. `AppLocalizations.of(context).loadingLabel`.
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Semantics(
        liveRegion: true,
        label: message,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(message, style: Theme.of(context).textTheme.bodyMedium),
          ],
        ),
      ),
    );
  }
}
