import 'dart:convert';

import 'package:crypto/crypto.dart';

import 'test_config.dart';

enum CollaborationActorRole { author, collaborator, outsider }

enum MultiUserExecutionMode {
  collaboration('collaboration'),
  calendarFailureContainment('calendar-failure-containment');

  const MultiUserExecutionMode(this.environmentValue);

  final String environmentValue;

  static MultiUserExecutionMode parse(String value) {
    final normalized = value.trim();
    return MultiUserExecutionMode.values.firstWhere(
      (mode) => mode.environmentValue == normalized,
      orElse: () => throw StateError(
        'WEAVE_E2E_EXECUTION_MODE must be collaboration or '
        'calendar-failure-containment.',
      ),
    );
  }
}

class CollaborationActorCredentials {
  const CollaborationActorCredentials({
    required this.username,
    required this.password,
  });

  final String username;
  final String password;
}

class MultiUserTestConfig {
  const MultiUserTestConfig({
    required this.common,
    required this.runId,
    required this.runIndex,
    required this.author,
    required this.collaborator,
    required this.outsider,
    required this.missingCapabilityVerified,
    required this.expiredTokenVerified,
    required this.revokedSessionVerified,
  });

  factory MultiUserTestConfig.fromEnvironment() {
    return MultiUserTestConfig(
      common: TestConfig.fromEnvironment(),
      runId: const String.fromEnvironment('WEAVE_E2E_RUN_ID').trim(),
      runIndex:
          int.tryParse(
            const String.fromEnvironment('WEAVE_E2E_RUN_INDEX').trim(),
          ) ??
          0,
      author: const CollaborationActorCredentials(
        username: String.fromEnvironment('WEAVE_E2E_AUTHOR_USERNAME'),
        password: String.fromEnvironment('WEAVE_E2E_AUTHOR_PASSWORD'),
      ),
      collaborator: const CollaborationActorCredentials(
        username: String.fromEnvironment('WEAVE_E2E_COLLABORATOR_USERNAME'),
        password: String.fromEnvironment('WEAVE_E2E_COLLABORATOR_PASSWORD'),
      ),
      outsider: const CollaborationActorCredentials(
        username: String.fromEnvironment('WEAVE_E2E_OUTSIDER_USERNAME'),
        password: String.fromEnvironment('WEAVE_E2E_OUTSIDER_PASSWORD'),
      ),
      missingCapabilityVerified: const bool.fromEnvironment(
        'WEAVE_E2E_MISSING_CAPABILITY_VERIFIED',
      ),
      expiredTokenVerified: const bool.fromEnvironment(
        'WEAVE_E2E_EXPIRED_TOKEN_VERIFIED',
      ),
      revokedSessionVerified: const bool.fromEnvironment(
        'WEAVE_E2E_REVOKED_SESSION_VERIFIED',
      ),
    );
  }

  final TestConfig common;
  final String runId;
  final int runIndex;
  final CollaborationActorCredentials author;
  final CollaborationActorCredentials collaborator;
  final CollaborationActorCredentials outsider;
  final bool missingCapabilityVerified;
  final bool expiredTokenVerified;
  final bool revokedSessionVerified;

  /// Stable evidence binding shared by every pass of the same isolated run.
  ///
  /// [runIndex] remains a separate evidence field so repeated passes can be
  /// distinguished without fragmenting their common run identity.
  String get runHash => _hash('run|$runId');

  void requireReady() {
    final missing = <String>[
      if (runId.isEmpty) 'WEAVE_E2E_RUN_ID',
      if (runIndex < 1) 'WEAVE_E2E_RUN_INDEX',
      if (author.username.trim().isEmpty) 'WEAVE_E2E_AUTHOR_USERNAME',
      if (author.password.trim().isEmpty) 'WEAVE_E2E_AUTHOR_PASSWORD',
      if (collaborator.username.trim().isEmpty)
        'WEAVE_E2E_COLLABORATOR_USERNAME',
      if (collaborator.password.trim().isEmpty)
        'WEAVE_E2E_COLLABORATOR_PASSWORD',
      if (outsider.username.trim().isEmpty) 'WEAVE_E2E_OUTSIDER_USERNAME',
      if (outsider.password.trim().isEmpty) 'WEAVE_E2E_OUTSIDER_PASSWORD',
    ];
    if (missing.isNotEmpty) {
      throw StateError(
        'Missing multi-user E2E dart-define(s): ${missing.join(', ')}.',
      );
    }
    if (!RegExp(r'^[0-9A-Za-z._-]{1,128}$').hasMatch(runId)) {
      throw StateError(
        'WEAVE_E2E_RUN_ID must contain only support-safe identifier characters.',
      );
    }
    final normalizedUsernames = <String>{
      author.username.trim().toLowerCase(),
      collaborator.username.trim().toLowerCase(),
      outsider.username.trim().toLowerCase(),
    };
    if (normalizedUsernames.length != CollaborationActorRole.values.length) {
      throw StateError(
        'Multi-user E2E roles must use three distinct disposable identities.',
      );
    }
  }

  CollaborationActorCredentials credentialsFor(CollaborationActorRole role) {
    return switch (role) {
      CollaborationActorRole.author => author,
      CollaborationActorRole.collaborator => collaborator,
      CollaborationActorRole.outsider => outsider,
    };
  }

  TestConfig actorConfig(CollaborationActorRole role) {
    final credentials = credentialsFor(role);
    return common.copyWith(
      username: credentials.username.trim(),
      password: credentials.password.trim(),
      offlineContractOnly: false,
    );
  }

  String actorHash(CollaborationActorRole role) {
    final username = credentialsFor(role).username.trim().toLowerCase();
    return _hash('$runId|${role.name}|$username');
  }

  String _hash(String value) =>
      sha256.convert(utf8.encode(value)).toString().substring(0, 16);
}
