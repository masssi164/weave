import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

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

  @override
  void initState() {
    super.initState();
    Future.microtask(_consume);
  }

  Future<void> _consume() async {
    try {
      await ref.read(consumeMemberHandoffProvider).call(widget.uri);
      ref.invalidate(appBootstrapProvider);
      if (mounted) {
        context.go(AppRoutes.signIn);
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
      final errorCodeText = 'Fehlercode: $errorCode';
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
}
