import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';

extension WorkspaceHomeResponseMapper on openapi.WorkspaceHomeResponse {
  WorkspaceHomeSnapshot toSnapshot() {
    return WorkspaceHomeSnapshot(
      version: _requiredInt(version, 'version'),
      readiness: _parseReadiness(_requiredText(readiness, 'readiness')),
      summary: _supportSafeText(_requiredText(summary, 'summary')),
      sections: _requiredList(
        sections,
        'sections',
      ).map((section) => section.toSection()).toList(growable: false),
      actions: _requiredList(
        actions,
        'actions',
      ).map((action) => action.toAction()).toList(growable: false),
      supportSafe: _requiredBool(supportSafe, 'supportSafe'),
    );
  }
}

extension WorkspaceHomeSectionResponseMapper
    on openapi.WorkspaceHomeSectionResponse {
  WorkspaceHomeSection toSection() {
    return WorkspaceHomeSection(
      key: _supportSafeText(_requiredText(key, 'section.key')),
      title: _supportSafeText(_requiredText(title, 'section.title')),
      readiness: _parseReadiness(_requiredText(readiness, 'section.readiness')),
      summary: _supportSafeText(_requiredText(summary, 'section.summary')),
      itemCount: (_requiredInt(itemCount, 'section.itemCount')) < 0
          ? 0
          : itemCount!,
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

WorkspaceCapabilityReadiness _parseReadiness(String rawValue) {
  return openapi.WorkspaceCapabilityStatusResponse(
    enabled: true,
    readiness: rawValue,
  ).toCapabilityState(WorkspaceCapability.shellAccess).readiness;
}

String _productRoute(String value) {
  final trimmed = _supportSafeText(value);
  if (!trimmed.startsWith('weave://')) {
    throw AppFailure.unknown(
      'The backend returned an unsafe Weave Home product route.',
      cause: value,
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
    throw AppFailure.unknown(
      'The backend returned an unsafe Weave Home text field.',
      cause: value,
    );
  }
  return trimmed;
}
