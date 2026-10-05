import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/generated/user_api/api.dart' as openapi;

extension WorkspaceHomeResponseMapper on openapi.WorkspaceHomeResponse {
  WorkspaceHomeSnapshot toSnapshot() {
    final responseVersion = _requiredInt(version, 'version');
    if (responseVersion != 3) {
      throw const AppFailure.unknown(
        'The backend returned an unsupported Weave Home payload version.',
      );
    }
    if (!_requiredBool(supportSafe, 'supportSafe')) {
      throw const AppFailure.unknown(
        'The backend returned an unsafe Weave Home payload.',
      );
    }
    final activities = _requiredList(
      recentActivity,
      'recentActivity',
    ).map((activity) => activity.toActivity()).toList(growable: false);
    if (activities.map((activity) => activity.activityRef).toSet().length !=
        activities.length) {
      throw const AppFailure.unknown(
        'The backend returned duplicate Weave Home activity references.',
      );
    }

    return WorkspaceHomeSnapshot(
      version: responseVersion,
      readiness: _parseReadiness(_requiredText(readiness?.value, 'readiness')),
      summary: _supportSafeText(_requiredText(summary, 'summary')),
      sections: _requiredList(
        sections,
        'sections',
      ).map((section) => section.toSection()).toList(growable: false),
      actions: _requiredList(
        actions,
        'actions',
      ).map((action) => action.toAction()).toList(growable: false),
      recentActivity: activities,
      supportSafe: true,
    );
  }
}

extension WorkspaceHomeRecentActivityResponseMapper
    on openapi.WorkspaceHomeRecentActivityResponse {
  WorkspaceHomeActivity toActivity() {
    if (!_requiredBool(supportSafe, 'recentActivity.supportSafe')) {
      throw const AppFailure.unknown(
        'The backend returned an unsafe Weave Home activity.',
      );
    }
    return WorkspaceHomeActivity(
      activityRef: _opaqueReference(
        _requiredText(activityRef, 'recentActivity.activityRef'),
        field: 'activityRef',
        pattern: RegExp(r'^activity:sha256:[0-9a-f]{64}$'),
      ),
      domain: _activityDomain(
        _requiredText(domain?.value, 'recentActivity.domain'),
      ),
      action: _activityAction(_requiredText(action, 'recentActivity.action')),
      occurredAt: _activityTimestamp(
        _required(occurredAt, 'recentActivity.occurredAt'),
      ),
      visibility: _activityVisibility(
        _requiredText(visibility?.value, 'recentActivity.visibility'),
      ),
      actorRefHash: _opaqueReference(
        _requiredText(actorRefHash, 'recentActivity.actorRefHash'),
        field: 'actorRefHash',
        pattern: RegExp(r'^sha256:[0-9a-f]{64}$'),
      ),
      actorIsCurrentUser: _requiredBool(
        actorIsCurrentUser,
        'recentActivity.actorIsCurrentUser',
      ),
      supportSafe: true,
    );
  }
}

extension WorkspaceHomeSectionResponseMapper
    on openapi.WorkspaceHomeSectionResponse {
  WorkspaceHomeSection toSection() {
    return WorkspaceHomeSection(
      key: _supportSafeText(_requiredText(key, 'section.key')),
      title: _supportSafeText(_requiredText(title, 'section.title')),
      readiness: _parseReadiness(
        _requiredText(readiness?.value, 'section.readiness'),
      ),
      summary: _supportSafeText(_requiredText(summary, 'section.summary')),
      itemCount: _knownItemCount(itemCount),
      accessible: _requiredBool(accessible, 'section.accessible'),
      productRoute: _productRoute(
        _requiredText(productRoute, 'section.productRoute'),
      ),
    );
  }
}

extension WorkspaceHomeActionResponseMapper
    on openapi.WorkspaceHomeActionResponse {
  WorkspaceHomeAction toAction() {
    return WorkspaceHomeAction(
      key: _supportSafeText(_requiredText(key, 'action.key')),
      label: _supportSafeText(_requiredText(label, 'action.label')),
      productRoute: _productRoute(
        _requiredText(productRoute, 'action.productRoute'),
      ),
      reason: _supportSafeText(_requiredText(reason, 'action.reason')),
    );
  }
}

T _required<T>(T? value, String field) {
  if (value != null) return value;
  throw AppFailure.unknown(
    'The backend returned an invalid Weave Home payload.',
    cause: '$field is required.',
  );
}

String _requiredText(String? value, String field) => _required(value, field);
int _requiredInt(int? value, String field) => _required(value, field);
bool _requiredBool(bool? value, String field) => _required(value, field);
List<T> _requiredList<T>(List<T>? value, String field) =>
    _required(value, field);

int? _knownItemCount(int? value) {
  if (value != null && value < 0) {
    throw const AppFailure.unknown(
      'The backend returned an invalid Home count.',
    );
  }
  return value;
}

WorkspaceCapabilityReadiness _parseReadiness(String rawValue) =>
    switch (rawValue) {
      'ready' => WorkspaceCapabilityReadiness.ready,
      'degraded' => WorkspaceCapabilityReadiness.degraded,
      'blocked' => WorkspaceCapabilityReadiness.blocked,
      'unavailable' => WorkspaceCapabilityReadiness.unavailable,
      _ => throw const AppFailure.unknown(
        'The backend returned an unknown Home readiness.',
      ),
    };

String _productRoute(String value) {
  final trimmed = _supportSafeText(value);
  if (!trimmed.startsWith('weave://')) {
    throw const AppFailure.unknown(
      'The backend returned an unsafe Weave Home product route.',
      cause: 'Rejected unsafe Home field.',
    );
  }
  return trimmed;
}

String _supportSafeText(String value) {
  final trimmed = value.trim();
  final lower = trimmed.toLowerCase();
  const forbidden = <String>[
    'authorization:',
    'bearer ',
    'token=',
    'secret',
    'password',
    'room_id',
    'event_id',
    'filename',
    'username',
    'display name',
    'https://',
    'http://',
  ];
  if (forbidden.any(lower.contains)) {
    throw const AppFailure.unknown(
      'The backend returned an unsafe Weave Home text field.',
      cause: 'Rejected unsafe Home field.',
    );
  }
  return trimmed;
}

String _opaqueReference(
  String value, {
  required String field,
  required RegExp pattern,
}) {
  final trimmed = value.trim();
  if (!pattern.hasMatch(trimmed)) {
    throw AppFailure.unknown(
      'The backend returned an invalid Weave Home activity reference.',
      cause: '$field did not match the support-safe opaque format.',
    );
  }
  return trimmed;
}

WorkspaceHomeActivityDomain _activityDomain(String value) {
  return switch (value.trim()) {
    'files' => WorkspaceHomeActivityDomain.files,
    _ => throw const AppFailure.unknown(
      'The backend returned an unknown Weave Home activity domain.',
    ),
  };
}

WorkspaceHomeActivityAction _activityAction(String value) {
  return switch (value.trim()) {
    'files.webdav_write.completed' =>
      WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
    _ => throw const AppFailure.unknown(
      'The backend returned an unknown Weave Home activity action.',
    ),
  };
}

WorkspaceHomeActivityVisibility _activityVisibility(String value) {
  return switch (value.trim()) {
    'workspace' => WorkspaceHomeActivityVisibility.workspace,
    _ => throw const AppFailure.unknown(
      'The backend returned an unknown Weave Home activity visibility.',
    ),
  };
}

DateTime _activityTimestamp(DateTime value) {
  if (!value.isUtc) {
    throw const AppFailure.unknown(
      'The backend returned an invalid Weave Home activity timestamp.',
    );
  }
  return value;
}
