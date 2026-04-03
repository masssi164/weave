import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/focus_utils.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/features/server_config/presentation/widgets/server_configuration_form.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// A multi-step setup flow presented after the welcome screen.
///
/// Two steps:
/// 1. Select provider type and issuer URL
/// 2. Review and adjust derived service endpoints
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
      final isValid = ref
          .read(serverConfigurationFormControllerProvider.notifier)
          .validateProviderAndIssuerStep();
      if (!isValid) {
        return;
      }

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
    final result = await ref
        .read(serverConfigurationFormControllerProvider.notifier)
        .save();
    if (result == null) {
      return;
    }

    ref.invalidate(savedServerConfigurationProvider);
    await ref.read(appBootstrapProvider.notifier).retry();
    if (mounted) {
      context.go(AppRoutes.signIn);
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
          title: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const WeaveLogo(
                semanticLabel: 'Weave logo',
                width: 40,
                framed: false,
                excludeFromSemantics: true,
              ),
              const SizedBox(width: 12),
              Flexible(
                child: Text(l10n.setupTitle, overflow: TextOverflow.ellipsis),
              ),
            ],
          ),
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
                        ? _ProviderStep(
                            key: const ValueKey('step_0'),
                            focusNode: _step0FocusNode,
                          )
                        : _ServicesStep(
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

/// Step 1: OIDC provider and issuer collection.
class _ProviderStep extends StatelessWidget {
  const _ProviderStep({super.key, required this.focusNode});

  final FocusNode focusNode;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Focus(
            focusNode: focusNode,
            child: Semantics(
              header: true,
              child: Text(
                l10n.setupProviderStepTitle,
                style: theme.textTheme.headlineSmall,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            l10n.setupProviderStepDescription,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 24),
          const ServerConfigurationForm(
            layout: ServerConfigurationFormLayout.providerAndIssuerOnly,
          ),
        ],
      ),
    );
  }
}

/// Step 2: editable derived services.
class _ServicesStep extends StatelessWidget {
  const _ServicesStep({super.key, required this.focusNode});

  final FocusNode focusNode;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Focus(
            focusNode: focusNode,
            child: Semantics(
              header: true,
              child: Text(
                l10n.setupServicesStepTitle,
                style: theme.textTheme.headlineSmall,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            l10n.setupServicesStepDescription,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 24),
          const ServerConfigurationForm(
            layout: ServerConfigurationFormLayout.serviceEndpointsOnly,
          ),
        ],
      ),
    );
  }
}
