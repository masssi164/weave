import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/focus_utils.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/a11y/semantic_list_tile.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/onboarding/providers/setup_state_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// A multi-step setup flow presented after the welcome screen.
///
/// Two steps:
/// 1. Language preference display (read-only — shows device locale)
/// 2. Confirmation — tap "Finish" to complete setup
///
/// Focus is moved to each step's heading when it becomes active.
/// Back navigation works via both the system back gesture and the
/// visible back button.
class SetupFlow extends ConsumerStatefulWidget {
  const SetupFlow({super.key});

  @override
  ConsumerState<SetupFlow> createState() => _SetupFlowState();
}

class _SetupFlowState extends ConsumerState<SetupFlow> {
  int _currentStep = 0;
  static const _totalSteps = 2;

  final _step0FocusNode = FocusNode();
  final _step1FocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    FocusUtils.requestFocusAfterFrame(_step0FocusNode);
  }

  @override
  void dispose() {
    _step0FocusNode.dispose();
    _step1FocusNode.dispose();
    super.dispose();
  }

  void _goNext() {
    if (_currentStep < _totalSteps - 1) {
      setState(() => _currentStep++);
      FocusUtils.requestFocusAfterFrame(_step1FocusNode);
    }
  }

  void _goBack() {
    if (_currentStep > 0) {
      setState(() => _currentStep--);
      FocusUtils.requestFocusAfterFrame(_step0FocusNode);
    } else {
      context.go(AppRoutes.welcome);
    }
  }

  Future<void> _finish() async {
    await ref.read(setupStateProvider.notifier).completeSetup();
    if (mounted) {
      context.go(AppRoutes.chat);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _goBack();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.setupTitle),
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: _goBack,
            tooltip: l10n.semanticBackButton,
          ),
        ),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Step indicator — announced to screen readers.
                Semantics(
                  label: l10n.setupStepIndicator(_currentStep + 1, _totalSteps),
                  child: ExcludeSemantics(
                    child: LinearProgressIndicator(
                      value: (_currentStep + 1) / _totalSteps,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                const SizedBox(height: 32),

                // Step content
                Expanded(
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 250),
                    child: _currentStep == 0
                        ? _LanguageStep(
                            key: const ValueKey('step_0'),
                            focusNode: _step0FocusNode,
                          )
                        : _ConfirmStep(
                            key: const ValueKey('step_1'),
                            focusNode: _step1FocusNode,
                          ),
                  ),
                ),

                // Navigation buttons
                Row(
                  children: [
                    if (_currentStep > 0)
                      Expanded(
                        child: AccessibleButton(
                          outlined: true,
                          onPressed: _goBack,
                          semanticLabel: l10n.setupBackButton,
                          child: Text(l10n.setupBackButton),
                        ),
                      ),
                    if (_currentStep > 0) const SizedBox(width: 16),
                    Expanded(
                      child: _currentStep < _totalSteps - 1
                          ? AccessibleButton(
                              onPressed: _goNext,
                              semanticLabel: l10n.setupNextButton,
                              child: Text(l10n.setupNextButton),
                            )
                          : AccessibleButton(
                              onPressed: _finish,
                              semanticLabel: l10n.setupFinishButton,
                              child: Text(l10n.setupFinishButton),
                            ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Step 1: Language preference display (read-only).
class _LanguageStep extends StatelessWidget {
  const _LanguageStep({super.key, required this.focusNode});

  final FocusNode focusNode;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context);
    final languageName = switch (locale.languageCode) {
      'de' => 'Deutsch',
      _ => 'English',
    };

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Focus(
          focusNode: focusNode,
          child: Semantics(
            header: true,
            child: Text(
              l10n.setupLanguageStepTitle,
              style: theme.textTheme.headlineSmall,
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          l10n.setupLanguageStepDescription,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 24),
        AccessibleListTile(
          leading: const Icon(Icons.language),
          title: Text(l10n.deviceLanguageLabel),
          subtitle: Text(languageName),
        ),
      ],
    );
  }
}

/// Step 2: Confirmation.
class _ConfirmStep extends StatelessWidget {
  const _ConfirmStep({super.key, required this.focusNode});

  final FocusNode focusNode;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Focus(
          focusNode: focusNode,
          child: Semantics(
            header: true,
            child: Text(
              l10n.setupConfirmStepTitle,
              style: theme.textTheme.headlineSmall,
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          l10n.setupConfirmStepDescription,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 24),
        ExcludeSemantics(
          child: Icon(
            Icons.check_circle_outline,
            size: 64,
            color: theme.colorScheme.primary,
          ),
        ),
      ],
    );
  }
}
