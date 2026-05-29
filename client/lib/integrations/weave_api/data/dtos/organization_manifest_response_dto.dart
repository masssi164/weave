import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';

class OrganizationManifestResponseDto {
  const OrganizationManifestResponseDto({
    required this.manifestVersion,
    required this.organizationId,
    required this.displayName,
    required this.organizationAuthUrl,
    required this.generatedAt,
    required this.supportSafe,
    required this.providerConfigurationExposed,
    required this.diagnosticsExposed,
    required this.whitelistingOwner,
    required this.clientResponsibilities,
    required this.adminConsoleResponsibilities,
    required this.memberCapabilityStates,
    required this.capabilities,
  });

  factory OrganizationManifestResponseDto.fromJson(Map<String, dynamic> json) {
    final organizationAuthUrl = Uri.tryParse(
      _string(json['organizationAuthUrl'], fallback: ''),
    );
    if (organizationAuthUrl == null ||
        !organizationAuthUrl.isAbsolute ||
        organizationAuthUrl.host.isEmpty ||
        organizationAuthUrl.userInfo.isNotEmpty ||
        organizationAuthUrl.hasQuery ||
        organizationAuthUrl.hasFragment ||
        (organizationAuthUrl.scheme != 'https' &&
            organizationAuthUrl.scheme != 'http')) {
      throw const AppFailure.unknown(
        'The backend returned an invalid organization auth URL.',
      );
    }
    final supportSafe = _bool(json['supportSafe']);
    final providerConfigurationExposed = _bool(
      json['providerConfigurationExposed'],
    );
    final diagnosticsExposed = _bool(json['diagnosticsExposed']);
    if (!supportSafe || providerConfigurationExposed || diagnosticsExposed) {
      throw const AppFailure.unknown(
        'The backend returned an unsafe organization manifest payload.',
      );
    }

    return OrganizationManifestResponseDto(
      manifestVersion: _string(json['manifestVersion'], fallback: 'unknown'),
      organizationId: _requiredText(json['organizationId'], 'organizationId'),
      displayName: _requiredText(json['displayName'], 'displayName'),
      organizationAuthUrl: organizationAuthUrl,
      generatedAt: _dateTime(json['generatedAt']),
      supportSafe: supportSafe,
      providerConfigurationExposed: providerConfigurationExposed,
      diagnosticsExposed: diagnosticsExposed,
      whitelistingOwner: _string(
        json['whitelistingOwner'],
        fallback: 'unknown',
      ),
      clientResponsibilities: _safeStringList(json['clientResponsibilities']),
      adminConsoleResponsibilities: _safeStringList(
        json['adminConsoleResponsibilities'],
      ),
      memberCapabilityStates: _memberCapabilityStates(
        json['memberCapabilityStates'],
      ),
      capabilities: WorkspaceCapabilitiesResponseDto.fromJson(
        _map(json['capabilities']),
      ),
    );
  }

  final String manifestVersion;
  final String organizationId;
  final String displayName;
  final Uri organizationAuthUrl;
  final DateTime? generatedAt;
  final bool supportSafe;
  final bool providerConfigurationExposed;
  final bool diagnosticsExposed;
  final String whitelistingOwner;
  final List<String> clientResponsibilities;
  final List<String> adminConsoleResponsibilities;
  final Map<String, MemberCapabilityState> memberCapabilityStates;
  final WorkspaceCapabilitiesResponseDto capabilities;

  OrganizationManifestSnapshot toSnapshot() => OrganizationManifestSnapshot(
    manifestVersion: manifestVersion,
    organizationId: organizationId,
    displayName: displayName,
    organizationAuthUrl: organizationAuthUrl,
    generatedAt: generatedAt,
    supportSafe: supportSafe,
    providerConfigurationExposed: providerConfigurationExposed,
    diagnosticsExposed: diagnosticsExposed,
    whitelistingOwner: whitelistingOwner,
    clientResponsibilities: clientResponsibilities,
    adminConsoleResponsibilities: adminConsoleResponsibilities,
    memberCapabilityStates: memberCapabilityStates,
    capabilities: capabilities.toSnapshot(),
  );
}

Map<String, MemberCapabilityState> _memberCapabilityStates(Object? value) {
  final raw = _map(value);
  return raw.map((key, value) {
    if (value is! String) {
      throw const AppFailure.unknown(
        'The backend returned an invalid member capability state.',
      );
    }
    return MapEntry(key, _memberCapabilityState(value));
  });
}

MemberCapabilityState _memberCapabilityState(String rawValue) {
  return switch (rawValue.trim()) {
    'available' => MemberCapabilityState.available,
    'disabled_by_policy' => MemberCapabilityState.disabledByPolicy,
    'not_configured' => MemberCapabilityState.notConfigured,
    'degraded' => MemberCapabilityState.degraded,
    'unavailable' => MemberCapabilityState.unavailable,
    'coming_later' => MemberCapabilityState.comingLater,
    _ => throw AppFailure.unknown(
      'The backend returned an unknown member capability state.',
      cause: rawValue,
    ),
  };
}

Map<String, dynamic> _map(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  throw const AppFailure.unknown(
    'The backend returned an invalid organization manifest payload.',
  );
}

List<String> _safeStringList(Object? value) {
  if (value is! List<dynamic>) {
    return const <String>[];
  }
  return value.whereType<String>().map(_cleanText).toList(growable: false);
}

String _safeText(Object? value) => value is String ? _cleanText(value) : '';

String _requiredText(Object? value, String field) {
  final cleaned = _safeText(value);
  if (cleaned.isEmpty) {
    throw AppFailure.unknown(
      'The backend returned an invalid organization manifest payload.',
      cause: '$field is required',
    );
  }
  return cleaned;
}

String _string(Object? value, {required String fallback}) {
  return value is String && value.trim().isNotEmpty ? value.trim() : fallback;
}

bool _bool(Object? value) => value == true;

DateTime? _dateTime(Object? value) {
  if (value is! String) {
    return null;
  }
  return DateTime.tryParse(value);
}

String _cleanText(String value) => value.replaceAll(RegExp(r'\s+'), ' ').trim();
