import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';

class WorkspaceHomeResponseDto {
  const WorkspaceHomeResponseDto({
    required this.version,
    required this.readiness,
    required this.summary,
    required this.sections,
    required this.actions,
    required this.supportSafe,
  });

  factory WorkspaceHomeResponseDto.fromJson(Map<String, dynamic> json) {
    final version = json['version'];
    final readiness = json['readiness'];
    final summary = json['summary'];
    final sections = json['sections'];
    final actions = json['actions'];
    final supportSafe = json['supportSafe'];

    if (version is! int ||
        readiness is! String ||
        summary is! String ||
        sections is! List ||
        actions is! List ||
        supportSafe is! bool) {
      throw const AppFailure.unknown(
        'The backend returned an invalid Weave Home payload.',
      );
    }

    return WorkspaceHomeResponseDto(
      version: version,
      readiness: readiness,
      summary: summary,
      sections: sections
          .map(_readMap)
          .map(WorkspaceHomeSectionDto.fromJson)
          .toList(growable: false),
      actions: actions
          .map(_readMap)
          .map(WorkspaceHomeActionDto.fromJson)
          .toList(growable: false),
      supportSafe: supportSafe,
    );
  }

  final int version;
  final String readiness;
  final String summary;
  final List<WorkspaceHomeSectionDto> sections;
  final List<WorkspaceHomeActionDto> actions;
  final bool supportSafe;

  WorkspaceHomeSnapshot toSnapshot() {
    return WorkspaceHomeSnapshot(
      version: version,
      readiness: _parseReadiness(readiness),
      summary: _supportSafeText(summary),
      sections: sections
          .map((section) => section.toSection())
          .toList(growable: false),
      actions: actions
          .map((action) => action.toAction())
          .toList(growable: false),
      supportSafe: supportSafe,
    );
  }
}

class WorkspaceHomeSectionDto {
  const WorkspaceHomeSectionDto({
    required this.key,
    required this.title,
    required this.readiness,
    required this.summary,
    required this.itemCount,
    required this.accessible,
    required this.productRoute,
  });

  factory WorkspaceHomeSectionDto.fromJson(Map<String, dynamic> json) {
    final key = json['key'];
    final title = json['title'];
    final readiness = json['readiness'];
    final summary = json['summary'];
    final itemCount = json['itemCount'];
    final accessible = json['accessible'];
    final productRoute = json['productRoute'];

    if (key is! String ||
        title is! String ||
        readiness is! String ||
        summary is! String ||
        itemCount is! int ||
        accessible is! bool ||
        productRoute is! String) {
      throw const AppFailure.unknown(
        'The backend returned an invalid Weave Home section.',
      );
    }

    return WorkspaceHomeSectionDto(
      key: key,
      title: title,
      readiness: readiness,
      summary: summary,
      itemCount: itemCount,
      accessible: accessible,
      productRoute: productRoute,
    );
  }

  final String key;
  final String title;
  final String readiness;
  final String summary;
  final int itemCount;
  final bool accessible;
  final String productRoute;

  WorkspaceHomeSection toSection() {
    return WorkspaceHomeSection(
      key: _supportSafeText(key),
      title: _supportSafeText(title),
      readiness: _parseReadiness(readiness),
      summary: _supportSafeText(summary),
      itemCount: itemCount < 0 ? 0 : itemCount,
      accessible: accessible,
      productRoute: _productRoute(productRoute),
    );
  }
}

class WorkspaceHomeActionDto {
  const WorkspaceHomeActionDto({
    required this.key,
    required this.label,
    required this.productRoute,
    required this.reason,
  });

  factory WorkspaceHomeActionDto.fromJson(Map<String, dynamic> json) {
    final key = json['key'];
    final label = json['label'];
    final productRoute = json['productRoute'];
    final reason = json['reason'];

    if (key is! String ||
        label is! String ||
        productRoute is! String ||
        reason is! String) {
      throw const AppFailure.unknown(
        'The backend returned an invalid Weave Home action.',
      );
    }

    return WorkspaceHomeActionDto(
      key: key,
      label: label,
      productRoute: productRoute,
      reason: reason,
    );
  }

  final String key;
  final String label;
  final String productRoute;
  final String reason;

  WorkspaceHomeAction toAction() {
    return WorkspaceHomeAction(
      key: _supportSafeText(key),
      label: _supportSafeText(label),
      productRoute: _productRoute(productRoute),
      reason: _supportSafeText(reason),
    );
  }
}

Map<String, dynamic> _readMap(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  throw const AppFailure.unknown(
    'The backend returned an invalid Weave Home list item.',
  );
}

WorkspaceCapabilityReadiness _parseReadiness(String rawValue) {
  return WorkspaceCapabilityStatusDto(
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
