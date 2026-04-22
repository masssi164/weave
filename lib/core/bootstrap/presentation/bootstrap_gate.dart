import 'package:flutter/material.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// Bootstrap surface shown before the router is allowed to build.
class BootstrapGate extends StatelessWidget {
  const BootstrapGate.loading({super.key}) : failure = null, onRetry = null;

  const BootstrapGate.error({
    super.key,
    required this.failure,
    required this.onRetry,
  });

  final AppFailure? failure;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: failure == null
              ? LoadingState(
                  message: l10n.bootstrapLoadingLabel,
                  hint: l10n.bootstrapLoadingHint,
                  icon: Icons.hub_outlined,
                )
              : ErrorState(
                  message: failure!.message,
                  retryLabel: l10n.retryButton,
                  onRetry: onRetry,
                ),
        ),
      ),
    );
  }
}
