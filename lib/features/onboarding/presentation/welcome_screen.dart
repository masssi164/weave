import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// The first screen users see on a fresh install.
///
/// It displays a welcome heading and a single CTA that navigates
/// to the setup flow. No backend calls are made.
class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Decorative icon — excluded from semantics.
                ExcludeSemantics(
                  child: Icon(
                    Icons.hub_outlined,
                    size: 80,
                    color: theme.colorScheme.primary,
                  ),
                ),
                const SizedBox(height: 32),
                // Main heading announced as header by screen readers.
                Semantics(
                  header: true,
                  child: Text(
                    l10n.welcomeTitle,
                    style: theme.textTheme.headlineMedium,
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  l10n.welcomeSubtitle,
                  style: theme.textTheme.bodyLarge?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 48),
                SizedBox(
                  width: double.infinity,
                  child: AccessibleButton(
                    onPressed: () => context.go(AppRoutes.setup),
                    semanticLabel: l10n.continueButton,
                    child: Text(l10n.continueButton),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
