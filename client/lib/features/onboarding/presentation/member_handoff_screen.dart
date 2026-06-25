import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/core/widgets/success_state.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/auth_failure_message.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

const dogfoodVisibleStateStorageKey = 'dogfood_visible_state_v1';

final consumeMemberHandoffProvider = Provider<ConsumeMemberHandoff>((ref) {
  final httpClient = http.Client();
  ref.onDispose(httpClient.close);
  return ConsumeMemberHandoff(
    repository: ref.watch(serverConfigurationRepositoryProvider),
    discoveryClient: AppStartDiscoveryClient(httpClient: httpClient),
    evidenceStore: ref.watch(preferencesStoreProvider),
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
  MemberHandoff? _handoff;
  String? _lastVisibleStateRecorded;
  bool _signInBusy = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(_consume);
  }

  Future<void> _consume() async {
    try {
      final handoff = await ref
          .read(consumeMemberHandoffProvider)
          .call(widget.uri);
      if (mounted) {
        setState(() => _handoff = handoff);
      }
    } catch (error) {
      final errorCode = supportSafeHandoffErrorCode(error);
      await _recordVisibleFailure(errorCode);
      if (mounted) {
        setState(() => _failure = error);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final failure = _failure;
    if (failure != null) {
      final errorCode = supportSafeHandoffErrorCode(failure);
      final errorCodeText = l10n.memberHandoffErrorCode(errorCode);
      _recordVisibleStateOnce('handoff_error', errorCode: errorCode);
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: ErrorState(
              message: l10n.memberHandoffErrorTitle,
              guidance: '${l10n.memberHandoffErrorGuidance}\n$errorCodeText',
              semanticLabel:
                  '${l10n.memberHandoffErrorTitle}. ${l10n.memberHandoffErrorGuidance}. $errorCodeText',
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
    final handoff = _handoff;
    if (handoff != null) {
      _recordVisibleStateOnce('handoff_ready', handoff: handoff);
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SuccessState(
                  message: l10n.memberHandoffReadyTitle,
                  guidance: l10n.memberHandoffReadyGuidance(
                    handoff.organizationSlug,
                    handoff.workspaceSlug,
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
                ),
                if (_signInFailure != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    authFailureMessage(l10n, _signInFailure!),
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.error,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ],
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
    setState(() {
      _signInBusy = true;
      _signInFailure = null;
    });
    try {
      await ref
          .read(signInWithOidcProvider)
          .call(isInteractiveSignInSupported: _isInteractiveSignInSupported);
      ref.invalidate(appBootstrapProvider);
      if (mounted) {
        setState(() => _signInBusy = false);
        if (context.mounted) {
          context.go(AppRoutes.firstRun);
        }
      }
    } on AuthFailure catch (failure) {
      if (mounted) {
        setState(() {
          _signInBusy = false;
          _signInFailure = failure;
        });
      }
    } catch (error) {
      if (mounted) {
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

  bool get _isInteractiveSignInSupported {
    if (kIsWeb) {
      return false;
    }
    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS ||
        defaultTargetPlatform == TargetPlatform.macOS;
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
            if (_queryValue('profile') != null)
              'profile': _queryValue('profile')!,
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
    MemberHandoff? handoff,
    String? errorCode,
  }) {
    final key = [
      state,
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
              if (handoff != null) ...{
                'handoffRef': handoff.handoffRef,
                'runId': handoff.runId,
                'organizationSlug': handoff.organizationSlug,
                'workspaceSlug': handoff.workspaceSlug,
                'profile': handoff.profile,
              },
              if (errorCode != null) 'errorCode': errorCode,
              'supportSafe': true,
            }),
          );
    });
  }
}
