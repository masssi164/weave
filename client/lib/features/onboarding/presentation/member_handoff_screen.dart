import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/auth_failure_message.dart';
import 'package:weave/features/auth/presentation/providers/auth_flow_controller.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

const dogfoodVisibleStateStorageKey = 'dogfood_visible_state_v1';

final discoverOrganizationAccessProvider = Provider<DiscoverOrganizationAccess>(
  (ref) {
    final httpClient = http.Client();
    ref.onDispose(httpClient.close);
    return DiscoverOrganizationAccess(
      repository: ref.watch(serverConfigurationRepositoryProvider),
      discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
      evidenceStore: ref.watch(preferencesStoreProvider),
    );
  },
);

final memberAuthOnboardingStateRecorderProvider =
    Provider<MemberAuthOnboardingStateRecorder>((ref) {
      return MemberAuthOnboardingStateRecorder(
        store: ref.watch(preferencesStoreProvider),
      );
    });

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
  String? _lastVisibleStateRecorded;
  bool _signInBusy = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(_consume);
  }

  Future<void> _consume() async {
    if (await _redirectIfAuthenticated()) {
      return;
    }
    try {
      final access = await ref
          .read(discoverOrganizationAccessProvider)
          .call(widget.uri);
      if (mounted) {
        setState(() => _access = access);
      }
    } catch (error) {
      final errorCode = supportSafeHandoffErrorCode(error);
      await _recordVisibleFailure(errorCode);
      if (mounted) {
        setState(() => _failure = error);
      }
    }
  }

  Future<bool> _redirectIfAuthenticated() async {
    final bootstrap = ref.read(appBootstrapProvider).asData?.value;
    if (bootstrap?.phase != BootstrapPhase.ready) {
      return false;
    }
    final access = _tryParseAccess();
    if (access != null) {
      await ref
          .read(memberAuthOnboardingStateRecorderProvider)
          .record(MemberAuthOnboardingStage.workspaceReady, access: access);
      _recordVisibleStateOnce('authenticated_redirect', access: access);
    }
    if (!mounted) {
      return true;
    }
    context.go(AppRoutes.home);
    return true;
  }

  OrganizationAccess? _tryParseAccess() {
    try {
      return const OrganizationAccessParser().parse(widget.uri);
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final failure = _failure;
    if (failure != null) {
      final errorCode = supportSafeHandoffErrorCode(failure);
      final errorCodeText = l10n.memberHandoffErrorCode(errorCode);
      final guidance = errorCode == 'WEAVE-APP-START-TLS-FAILED'
          ? l10n.memberHandoffTlsErrorGuidance
          : l10n.memberHandoffErrorGuidance;
      _recordVisibleStateOnce('handoff_error', errorCode: errorCode);
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: ErrorState(
              message: l10n.memberHandoffErrorTitle,
              guidance: '$guidance\n$errorCodeText',
              semanticLabel:
                  '${l10n.memberHandoffErrorTitle}. $guidance. $errorCodeText',
              retryLabel: l10n.retryButton,
              onRetry: () {
                setState(() => _failure = null);
                _consume();
              },
            ),
          ),
        ),
      );
    }
    final access = _access;
    if (access != null) {
      _recordVisibleStateOnce('handoff_ready', access: access);
      final signInFailure = _signInFailure;
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: signInFailure == null
                ? SuccessState(
                    message: l10n.memberHandoffReadyTitle,
                    guidance: l10n.memberHandoffReadyGuidance(
                      access.organizationLabel,
                      access.workspaceLabel,
                    ),
                    actionLabel: _signInBusy
                        ? l10n.signInInProgress
                        : l10n.signInButton,
                    liveRegion: false,
                    onAction: _signInBusy
                        ? null
                        : () {
                            _startSignIn(context);
                          },
                  )
                : ErrorState(
                    message: authFailureMessage(l10n, signInFailure),
                    guidance: l10n.memberHandoffSignInRetryGuidance,
                    retryLabel: l10n.signInButton,
                    onRetry: _signInBusy
                        ? null
                        : () {
                            _startSignIn(context);
                          },
                  ),
          ),
        ),
      );
    }
    _recordVisibleStateOnce('handoff_loading');
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: LoadingState(
            message: l10n.memberHandoffLoadingTitle,
            hint: l10n.memberHandoffLoadingHint,
          ),
        ),
      ),
    );
  }

  Future<void> _startSignIn(BuildContext context) async {
    final access = _access;
    setState(() {
      _signInBusy = true;
      _signInFailure = null;
    });
    try {
      await ref
          .read(memberAuthOnboardingStateRecorderProvider)
          .record(MemberAuthOnboardingStage.ssoInProgress, access: access);
      final signedIn = await ref
          .read(authFlowControllerProvider.notifier)
          .signIn();
      if (!signedIn) {
        final failure =
            ref.read(authFlowControllerProvider).failure ??
            const AuthFailure.unknown('Unable to sign in right now.');
        await ref
            .read(memberAuthOnboardingStateRecorderProvider)
            .recordAuthFailure(failure, access: access);
        if (mounted) {
          setState(() {
            _signInBusy = false;
            _signInFailure = failure;
          });
        }
        return;
      }
      await ref
          .read(memberAuthOnboardingStateRecorderProvider)
          .record(MemberAuthOnboardingStage.authenticated, access: access);
      await ref
          .read(memberAuthOnboardingStateRecorderProvider)
          .record(
            MemberAuthOnboardingStage.workspaceBootstrapLoading,
            access: access,
          );
      final bootstrap = ref.read(appBootstrapProvider).asData?.value;
      final workspaceReady = bootstrap?.phase == BootstrapPhase.ready;
      await ref
          .read(memberAuthOnboardingStateRecorderProvider)
          .record(
            workspaceReady
                ? MemberAuthOnboardingStage.workspaceReady
                : MemberAuthOnboardingStage.recoverableError,
            access: access,
            errorCode: workspaceReady
                ? null
                : 'WEAVE-WORKSPACE-BOOTSTRAP-NOT-READY',
          );
      if (!workspaceReady) {
        const failure = AuthFailure.protocol(
          'The authenticated workspace did not become ready.',
        );
        if (mounted) {
          setState(() {
            _signInBusy = false;
            _signInFailure = failure;
          });
        }
        return;
      }
      if (mounted && context.mounted) {
        setState(() => _signInBusy = false);
        context.go(AppRoutes.home);
      }
    } on AuthFailure catch (failure) {
      if (mounted) {
        await ref
            .read(memberAuthOnboardingStateRecorderProvider)
            .recordAuthFailure(failure, access: access);
        setState(() {
          _signInBusy = false;
          _signInFailure = failure;
        });
      }
    } catch (error) {
      final failure = AuthFailure.unknown(
        'Unable to sign in right now.',
        cause: error,
      );
      await ref
          .read(memberAuthOnboardingStateRecorderProvider)
          .recordAuthFailure(failure, access: access);
      if (mounted) {
        setState(() {
          _signInBusy = false;
          _signInFailure = failure;
        });
      }
    }
  }

  Future<void> _recordVisibleFailure(String errorCode) async {
    await ref
        .read(preferencesStoreProvider)
        .setString(
          lastHandoffConsumedStorageKey,
          jsonEncode(<String, Object>{
            'schemaVersion': 'weave.client.last_handoff_consumed.v1',
            'recordedAt': DateTime.now().toUtc().toIso8601String(),
            'result': 'failed',
            'phase': _failurePhase(errorCode),
            'inviteScheme': widget.uri.scheme,
            'inviteHost': widget.uri.host,
            'invitePath': widget.uri.path,
            if (_queryValue('handoff_ref') != null)
              'handoffRef': _queryValue('handoff_ref')!,
            if (_queryValue('run_id') != null) 'runId': _queryValue('run_id')!,
            if (_queryValue('org') != null)
              'organizationSlug': _queryValue('org')!,
            if (_queryValue('workspace') != null)
              'workspaceSlug': _queryValue('workspace')!,
            'errorCode': errorCode,
            'supportSafe': true,
          }),
        );
  }

  String? _queryValue(String key) {
    final value = widget.uri.queryParameters[key];
    return value == null || value.isEmpty ? null : value;
  }

  String _failurePhase(String errorCode) {
    if (errorCode == 'WEAVE-HANDOFF-INVALID' ||
        errorCode == 'WEAVE-HANDOFF-MISSING-BASE' ||
        errorCode == 'WEAVE-HANDOFF-SECRET-BLOCKED' ||
        errorCode == 'WEAVE-LINK-UNREACHABLE' ||
        errorCode == 'WEAVE-LAN-UNREACHABLE') {
      return 'parse';
    }
    if (errorCode.startsWith('WEAVE-APP-START-')) {
      return 'app_start_discovery';
    }
    return 'save_configuration';
  }

  void _recordVisibleStateOnce(
    String state, {
    OrganizationAccess? access,
    String? errorCode,
  }) {
    final handoff = access?.handoff;
    final key = [
      state,
      access?.organizationOrigin.host ?? '',
      handoff?.handoffRef ?? '',
      handoff?.runId ?? '',
      errorCode ?? '',
    ].join('|');
    if (_lastVisibleStateRecorded == key) {
      return;
    }
    _lastVisibleStateRecorded = key;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await ref
          .read(preferencesStoreProvider)
          .setString(
            dogfoodVisibleStateStorageKey,
            jsonEncode(<String, Object>{
              'schemaVersion': 'weave.client.dogfood_visible_state.v1',
              'recordedAt': DateTime.now().toUtc().toIso8601String(),
              'route': AppRoutes.join,
              'state': state,
              if (access != null)
                'organizationOriginHost': access.organizationOrigin.host,
              if (handoff != null) ...{
                'handoffRef': handoff.handoffRef,
                'runId': handoff.runId,
                'organizationSlug': handoff.organizationSlug,
                'workspaceSlug': handoff.workspaceSlug,
              },
              if (errorCode != null) 'errorCode': errorCode,
              'supportSafe': true,
            }),
          );
    });
  }
}
