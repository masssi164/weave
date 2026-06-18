import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/a11y/focus_utils.dart';
import 'package:weave/core/a11y/semantic_button.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/features/server_config/presentation/widgets/provider_category_summary.dart';
import 'package:weave/features/server_config/presentation/widgets/server_configuration_form.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

/// Handoff-first setup presented after the welcome screen.
///
/// Normal members are directed to invite/auth/deep-link handoff. Raw provider
/// endpoint editing is available only after explicitly entering operator
/// recovery mode.
class SetupFlow extends ConsumerStatefulWidget {
  const SetupFlow({super.key});

  @override
  ConsumerState<SetupFlow> createState() => _SetupFlowState();
}

class _SetupFlowState extends ConsumerState<SetupFlow> {
  int _currentStep = 0;
  bool _operatorRecoveryMode = false;
  static const _totalSteps = 2;

  final _memberFocusNode = FocusNode();
  final _step0FocusNode = FocusNode();
  final _step1FocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    FocusUtils.requestFocusAfterFrame(_memberFocusNode);
  }

  @override
  void dispose() {
    _memberFocusNode.dispose();
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
    if (!_operatorRecoveryMode) {
      context.go(AppRoutes.welcome);
    } else if (_currentStep > 0) {
      setState(() => _currentStep--);
      FocusUtils.requestFocusAfterFrame(_memberFocusNode);
    } else {
      setState(() => _operatorRecoveryMode = false);
      FocusUtils.requestFocusAfterFrame(_memberFocusNode);
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
                child: Text(
                  _operatorRecoveryMode
                      ? l10n.setupOperatorRecoveryTitle
                      : l10n.setupTitle,
                  overflow: TextOverflow.ellipsis,
                ),
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
            child: _operatorRecoveryMode
                ? _OperatorRecoverySetup(
                    currentStep: _currentStep,
                    totalSteps: _totalSteps,
                    step0FocusNode: _step0FocusNode,
                    step1FocusNode: _step1FocusNode,
                    onBack: _goBack,
                    onNext: _goNext,
                    onFinish: _finish,
                  )
                : _MemberHandoffSetup(
                    focusNode: _memberFocusNode,
                    onOpenOperatorRecovery: () {
                      setState(() {
                        _operatorRecoveryMode = true;
                        _currentStep = 0;
                      });
                      FocusUtils.requestFocusAfterFrame(_step0FocusNode);
                    },
                  ),
          ),
        ),
      ),
    );
  }
}

class _MemberHandoffSetup extends StatelessWidget {
  const _MemberHandoffSetup({
    required this.focusNode,
    required this.onOpenOperatorRecovery,
  });

  final FocusNode focusNode;
  final VoidCallback onOpenOperatorRecovery;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Focus(
                focusNode: focusNode,
                child: Semantics(
                  header: true,
                  child: Text(
                    l10n.setupMemberHandoffTitle,
                    style: theme.textTheme.headlineSmall,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Text(
                l10n.setupMemberHandoffDescription,
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 24),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Semantics(
                        header: true,
                        child: Text(
                          l10n.setupMemberHandoffPrimaryAction,
                          style: theme.textTheme.titleMedium,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(l10n.setupMemberHandoffPrimaryGuidance),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Semantics(
                        header: true,
                        child: Text(
                          l10n.setupMemberHandoffAdminNoteTitle,
                          style: theme.textTheme.titleMedium,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(l10n.setupMemberHandoffAdminNote),
                      const SizedBox(height: 16),
                      AccessibleButton(
                        outlined: true,
                        onPressed: onOpenOperatorRecovery,
                        semanticLabel: l10n.setupOpenOperatorRecoveryButton,
                        child: Text(l10n.setupOpenOperatorRecoveryButton),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _OperatorRecoverySetup extends StatelessWidget {
  const _OperatorRecoverySetup({
    required this.currentStep,
    required this.totalSteps,
    required this.step0FocusNode,
    required this.step1FocusNode,
    required this.onBack,
    required this.onNext,
    required this.onFinish,
  });

  final int currentStep;
  final int totalSteps;
  final FocusNode step0FocusNode;
  final FocusNode step1FocusNode;
  final VoidCallback onBack;
  final VoidCallback onNext;
  final VoidCallback onFinish;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          label: l10n.setupStepIndicator(currentStep + 1, totalSteps),
          child: ExcludeSemantics(
            child: LinearProgressIndicator(
              value: (currentStep + 1) / totalSteps,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
        ),
        const SizedBox(height: 32),
        Expanded(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 250),
            child: currentStep == 0
                ? _ProviderStep(
                    key: const ValueKey('step_0'),
                    focusNode: step0FocusNode,
                  )
                : _ServicesStep(
                    key: const ValueKey('step_1'),
                    focusNode: step1FocusNode,
                  ),
          ),
        ),
        Row(
          children: [
            if (currentStep > 0)
              Expanded(
                child: AccessibleButton(
                  outlined: true,
                  onPressed: onBack,
                  semanticLabel: l10n.setupBackButton,
                  child: Text(l10n.setupBackButton),
                ),
              ),
            if (currentStep > 0) const SizedBox(width: 16),
            Expanded(
              child: currentStep < totalSteps - 1
                  ? AccessibleButton(
                      onPressed: onNext,
                      semanticLabel: l10n.setupNextButton,
                      child: Text(l10n.setupNextButton),
                    )
                  : AccessibleButton(
                      onPressed: onFinish,
                      semanticLabel: l10n.setupFinishButton,
                      child: Text(l10n.setupFinishButton),
                    ),
            ),
          ],
        ),
      ],
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
          const SizedBox(height: 16),
          const ProviderCategorySummary(compact: true),
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
