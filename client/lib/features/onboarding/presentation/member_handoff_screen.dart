import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/theme/weave_motion_theme.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/auth_failure_message.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

final discoverOrganizationAccessProvider = Provider<DiscoverOrganizationAccess>(
  (ref) {
    final httpClient = http.Client();
    ref.onDispose(httpClient.close);
    return DiscoverOrganizationAccess(
      repository: ref.watch(serverConfigurationRepositoryProvider),
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    );
  },
);

class MemberHandoffScreen extends ConsumerStatefulWidget {
  const MemberHandoffScreen({super.key, required this.uri});

  final Uri uri;

  @override
  ConsumerState<MemberHandoffScreen> createState() =>
      _MemberHandoffScreenState();
}

class _MemberHandoffScreenState extends ConsumerState<MemberHandoffScreen> {
  Object? _failure;
  AuthFailure? _signInFailure;
  OrganizationAccess? _access;
  bool _signInBusy = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(_consume);
  }

  Future<void> _consume() async {
    try {
      if (await _redirectIfAuthenticated() || !mounted) return;
      final access = await ref
          .read(discoverOrganizationAccessProvider)
          .call(widget.uri);
      if (!mounted) return;
      ref.invalidate(savedServerConfigurationProvider);
      setState(() => _access = access);
    } catch (error) {
      if (!mounted) return;
      setState(() => _failure = error);
    }
  }

  Future<bool> _redirectIfAuthenticated() async {
    final bootstrap = await ref.read(appBootstrapProvider.future);
    if (!mounted) return true;
    if (!bootstrap.isResolved) {
      throw bootstrap.failure ??
          const AppFailure.bootstrap(
            'The organization workspace is not ready.',
          );
    }
    if (bootstrap.phase != BootstrapPhase.ready) return false;

    final access = const OrganizationAccessParser().parse(widget.uri);
    final selected = await ref
        .read(serverConfigurationRepositoryProvider)
        .loadConfiguration();
    if (!mounted) return true;
    final currentBootstrap = ref.read(appBootstrapProvider);
    if (currentBootstrap.isLoading ||
        currentBootstrap.hasError ||
        currentBootstrap.value?.phase != BootstrapPhase.ready) {
      throw const AppFailure.bootstrap(
        'The organization workspace is not ready.',
      );
    }

    final origin = selected?.organizationOrigin;
    final incoming = access.organizationOrigin;
    if (origin == null ||
        origin.scheme != 'https' ||
        origin.userInfo.isNotEmpty ||
        !{'', '/'}.contains(origin.path) ||
        origin.hasQuery ||
        origin.hasFragment ||
        origin.host != incoming.host ||
        origin.port != incoming.port) {
      throw const AppFailure.validation(
        'WEAVE-ORGANIZATION-ACCESS-INVALID: This link does not match the signed-in organization.',
      );
    }

    if (mounted) context.go(AppRoutes.home);
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final motion = context.weaveMotion;
    final busy = _signInBusy || ref.watch(authFlowControllerProvider).isBusy;

    final Widget state;
    final failure = _failure;
    final signInFailure = _signInFailure;
    if (failure != null) {
      final errorCode = supportSafeHandoffErrorCode(failure);
      final guidance = errorCode == 'WEAVE-APP-START-TLS-FAILED'
          ? l10n.memberHandoffTlsErrorGuidance
          : l10n.memberHandoffErrorGuidance;
      state = ErrorState(
        key: const ValueKey('handoff-error'),
        message: l10n.memberHandoffErrorTitle,
        guidance: '$guidance\n${l10n.memberHandoffErrorCode(errorCode)}',
        retryLabel: l10n.retryButton,
        onRetry: () {
          setState(() => _failure = null);
          _consume();
        },
      );
    } else if (_access != null && signInFailure != null) {
      state = ErrorState(
        key: const ValueKey('handoff-sign-in-error'),
        message: authFailureMessage(l10n, signInFailure),
        guidance: l10n.memberHandoffSignInRetryGuidance,
        retryLabel: l10n.signInButton,
        onRetry: busy ? null : () => _startSignIn(context),
      );
    } else if (_access != null) {
      state = SuccessState(
        key: const ValueKey('handoff-ready'),
        message: l10n.memberHandoffReadyTitle,
        guidance: l10n.signInDescription,
        actionLabel: busy ? l10n.signInInProgress : l10n.signInButton,
        liveRegion: false,
        onAction: busy ? null : () => _startSignIn(context),
      );
    } else {
      state = LoadingState(
        key: const ValueKey('handoff-loading'),
        message: l10n.memberHandoffLoadingTitle,
        hint: l10n.memberHandoffLoadingHint,
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.setupTitle),
        leading: IconButton(
          tooltip: l10n.signInBackToSetupButton,
          icon: const Icon(Icons.arrow_back),
          onPressed: busy
              ? null
              : () {
                  if (context.canPop()) {
                    context.pop();
                  } else {
                    context.go(AppRoutes.organizationAccess);
                  }
                },
        ),
      ),
      body: SafeArea(
        child: Center(
          child: AnimatedSwitcher(
            duration: motion.standard,
            switchInCurve: motion.standardCurve,
            switchOutCurve: motion.standardCurve,
            child: state,
          ),
        ),
      ),
    );
  }

  Future<void> _startSignIn(BuildContext context) async {
    setState(() {
      _signInBusy = true;
      _signInFailure = null;
    });
    try {
      final signedIn = await ref
          .read(authFlowControllerProvider.notifier)
          .signIn();
      if (!mounted) return;
      if (!signedIn) {
        final failure =
            ref.read(authFlowControllerProvider).failure ??
            const AuthFailure.unknown('Unable to sign in right now.');
        setState(() {
          _signInBusy = false;
          _signInFailure = failure;
        });
        return;
      }

      final bootstrap = ref.read(appBootstrapProvider).asData?.value;
      if (bootstrap?.phase != BootstrapPhase.ready) {
        setState(() {
          _signInBusy = false;
          _signInFailure = const AuthFailure.protocol(
            'The authenticated workspace did not become ready.',
          );
        });
        return;
      }

      setState(() => _signInBusy = false);
      if (context.mounted) context.go(AppRoutes.home);
    } on AuthFailure catch (failure) {
      if (!mounted) return;
      setState(() {
        _signInBusy = false;
        _signInFailure = failure;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _signInBusy = false;
        _signInFailure = AuthFailure.unknown(
          'Unable to sign in right now.',
          cause: error,
        );
      });
    }
  }
}
