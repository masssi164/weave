import 'dart:convert';

import 'package:weave/core/persistence/preferences_store.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';

const dogfoodAuthStateStorageKey = 'dogfood_auth_state_v1';
const dogfoodAuthStateHistoryStorageKey = 'dogfood_auth_state_history_v1';

enum MemberAuthOnboardingStage {
  handoffReceived('handoff_received'),
  platformConfigLoaded('platform_config_loaded'),
  readyForSso('ready_for_sso'),
  ssoInProgress('sso_in_progress'),
  authenticated('authenticated'),
  workspaceBootstrapLoading('workspace_bootstrap_loading'),
  workspaceReady('workspace_ready'),
  recoverableError('recoverable_error'),
  terminalSetupError('terminal_setup_error');

  const MemberAuthOnboardingStage(this.serialized);

  final String serialized;
}

class MemberAuthOnboardingSnapshot {
  const MemberAuthOnboardingSnapshot({
    required this.stage,
    this.handoff,
    this.errorCode,
  });

  final MemberAuthOnboardingStage stage;
  final MemberHandoff? handoff;
  final String? errorCode;

  Map<String, Object> toSupportSafeJson() {
    final handoff = this.handoff;
    return <String, Object>{
      'schemaVersion': 'weave.client.dogfood_auth_state.v1',
      'recordedAt': DateTime.now().toUtc().toIso8601String(),
      'state': stage.serialized,
      if (handoff != null) ...{
        'handoffRef': handoff.handoffRef,
        'runId': handoff.runId,
        'organizationSlug': handoff.organizationSlug,
        'workspaceSlug': handoff.workspaceSlug,
        'profile': handoff.profile,
      },
      if (errorCode != null) 'errorCode': errorCode!,
      'supportSafe': true,
    };
  }
}

class MemberAuthOnboardingStateRecorder {
  const MemberAuthOnboardingStateRecorder({required PreferencesStore store})
    : _store = store;

  final PreferencesStore _store;

  Future<void> record(
    MemberAuthOnboardingStage stage, {
    MemberHandoff? handoff,
    String? errorCode,
  }) async {
    final snapshot = MemberAuthOnboardingSnapshot(
      stage: stage,
      handoff: handoff,
      errorCode: errorCode,
    ).toSupportSafeJson();
    await _store.setString(dogfoodAuthStateStorageKey, jsonEncode(snapshot));
    await _appendHistory(snapshot);
  }

  Future<void> _appendHistory(Map<String, Object> snapshot) async {
    final rawHistory = await _store.getString(
      dogfoodAuthStateHistoryStorageKey,
    );
    final history = <Object>[];
    if (rawHistory != null && rawHistory.isNotEmpty) {
      final decoded = jsonDecode(rawHistory);
      if (decoded is List) {
        history.addAll(decoded.whereType<Map<String, Object?>>());
      }
    }
    history.add(snapshot);
    await _store.setString(
      dogfoodAuthStateHistoryStorageKey,
      jsonEncode(history),
    );
  }

  Future<void> recordSupportSafeHandoffEvidence(
    MemberAuthOnboardingStage stage, {
    required Map<String, Object?> handoffEvidence,
    String? errorCode,
  }) async {
    final snapshot = <String, Object>{
      'schemaVersion': 'weave.client.dogfood_auth_state.v1',
      'recordedAt': DateTime.now().toUtc().toIso8601String(),
      'state': stage.serialized,
      for (final key in const [
        'handoffRef',
        'runId',
        'organizationSlug',
        'workspaceSlug',
        'profile',
      ])
        if (handoffEvidence[key] is String) key: handoffEvidence[key] as String,
      if (errorCode != null) 'errorCode': errorCode,
      'supportSafe': true,
    };
    await _store.setString(dogfoodAuthStateStorageKey, jsonEncode(snapshot));
    await _appendHistory(snapshot);
  }

  Future<void> recordAuthFailure(
    AuthFailure failure, {
    MemberHandoff? handoff,
  }) {
    return record(
      _failureStage(failure),
      handoff: handoff,
      errorCode: supportSafeAuthOnboardingErrorCode(failure),
    );
  }

  Future<void> recordAuthFailureFromHandoffEvidence(
    AuthFailure failure, {
    required Map<String, Object?> handoffEvidence,
  }) {
    return recordSupportSafeHandoffEvidence(
      _failureStage(failure),
      handoffEvidence: handoffEvidence,
      errorCode: supportSafeAuthOnboardingErrorCode(failure),
    );
  }

  MemberAuthOnboardingStage _failureStage(AuthFailure failure) {
    return switch (failure.type) {
      AuthFailureType.configuration ||
      AuthFailureType.storage ||
      AuthFailureType.unsupportedPlatform =>
        MemberAuthOnboardingStage.terminalSetupError,
      AuthFailureType.cancelled ||
      AuthFailureType.protocol ||
      AuthFailureType.unknown => MemberAuthOnboardingStage.recoverableError,
    };
  }
}

String supportSafeAuthOnboardingErrorCode(AuthFailure failure) {
  if (_isOfflineSessionDenied(failure.message)) {
    return 'WEAVE-MOBILE-OFFLINE-SESSION-DENIED';
  }
  return switch (failure.type) {
    AuthFailureType.cancelled => 'WEAVE-SSO-CANCELLED',
    AuthFailureType.configuration => 'WEAVE-SSO-CONFIGURATION-MISSING',
    AuthFailureType.protocol => 'WEAVE-SSO-NOT-COMPLETE',
    AuthFailureType.storage => 'WEAVE-SESSION-STORAGE-FAILED',
    AuthFailureType.unsupportedPlatform => 'WEAVE-SSO-UNSUPPORTED-DEVICE',
    AuthFailureType.unknown => 'WEAVE-SSO-UNKNOWN',
  };
}

bool _isOfflineSessionDenied(String message) {
  final normalized = message.toLowerCase();
  return normalized.contains('offline') &&
      (normalized.contains('not allowed') ||
          normalized.contains('invalid_scope') ||
          normalized.contains('scope'));
}
