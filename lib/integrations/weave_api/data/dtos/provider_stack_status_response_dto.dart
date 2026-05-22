import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/provider_stack_status.dart';

class ProviderStackStatusResponseDto {
  const ProviderStackStatusResponseDto({
    required this.releaseStatus,
    required this.backendOwnedFacades,
    required this.flutterDirectProviderCallsAllowed,
    required this.supportSafe,
    required this.providers,
  });

  factory ProviderStackStatusResponseDto.fromJson(Map<String, dynamic> json) {
    final providers = json['providers'];
    if (providers is! List) {
      throw const AppFailure.unknown(
        'The backend returned an invalid provider readiness response.',
      );
    }

    return ProviderStackStatusResponseDto(
      releaseStatus: _string(json['releaseStatus']),
      backendOwnedFacades: _bool(json['backendOwnedFacades']),
      flutterDirectProviderCallsAllowed: _bool(
        json['flutterDirectProviderCallsAllowed'],
      ),
      supportSafe: _bool(json['supportSafe']),
      providers: providers.map(_providerReadinessDto).toList(growable: false),
    );
  }

  final String releaseStatus;
  final bool backendOwnedFacades;
  final bool flutterDirectProviderCallsAllowed;
  final bool supportSafe;
  final List<ProviderReadinessDto> providers;

  ProviderStackStatus toEntity() {
    return ProviderStackStatus(
      releaseStatus: releaseStatus,
      backendOwnedFacades: backendOwnedFacades,
      flutterDirectProviderCallsAllowed: flutterDirectProviderCallsAllowed,
      supportSafe: supportSafe,
      providers: providers.map((provider) => provider.toEntity()).toList(),
    );
  }
}

class ProviderReadinessDto {
  const ProviderReadinessDto({
    required this.module,
    required this.providerKey,
    required this.state,
    required this.readiness,
    required this.enabled,
    required this.configured,
    required this.readOnly,
    required this.failClosed,
    required this.supportSafe,
    required this.summary,
    required this.supportedCapabilities,
    required this.unsupportedOperations,
    required this.paidFeaturesRequired,
    required this.supportSafeErrorCodes,
    required this.redactionPolicy,
    required this.candidates,
  });

  factory ProviderReadinessDto.fromJson(Map<String, dynamic> json) {
    return ProviderReadinessDto(
      module: _string(json['module']),
      providerKey: _string(json['providerKey']),
      state: _string(json['state']),
      readiness: _string(json['readiness']),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      readOnly: _bool(json['readOnly']),
      failClosed: _bool(json['failClosed']),
      supportSafe: _bool(json['supportSafe']),
      summary: _string(json['summary']),
      supportedCapabilities: _stringSet(json['supportedCapabilities']),
      unsupportedOperations: _stringSet(json['unsupportedOperations']),
      paidFeaturesRequired: _optionalBool(
        json['paidFeaturesRequired'],
        defaultValue: false,
      ),
      supportSafeErrorCodes: _optionalStringSet(json['supportSafeErrorCodes']),
      redactionPolicy: _optionalString(
        json['redactionPolicy'],
        defaultValue:
            'support-safe: no tokens, passwords, credentials, authorization headers, or raw provider errors',
      ),
      candidates: _optionalStringSet(json['candidates']),
    );
  }

  final String module;
  final String providerKey;
  final String state;
  final String readiness;
  final bool enabled;
  final bool configured;
  final bool readOnly;
  final bool failClosed;
  final bool supportSafe;
  final String summary;
  final Set<String> supportedCapabilities;
  final Set<String> unsupportedOperations;
  final bool paidFeaturesRequired;
  final Set<String> supportSafeErrorCodes;
  final String redactionPolicy;
  final Set<String> candidates;

  ProviderReadiness toEntity() {
    return ProviderReadiness(
      module: module,
      providerKey: providerKey,
      state: state,
      readiness: readiness,
      enabled: enabled,
      configured: configured,
      readOnly: readOnly,
      failClosed: failClosed,
      supportSafe: supportSafe,
      summary: summary,
      supportedCapabilities: supportedCapabilities,
      unsupportedOperations: unsupportedOperations,
      paidFeaturesRequired: paidFeaturesRequired,
      supportSafeErrorCodes: supportSafeErrorCodes,
      redactionPolicy: redactionPolicy,
      candidates: candidates,
    );
  }
}

class DevopsSummaryResponseDto {
  const DevopsSummaryResponseDto({
    required this.workspaceId,
    required this.channelId,
    required this.releaseStatus,
    required this.readOnly,
    required this.paidFeaturesRequired,
    required this.supportSafe,
    required this.providerReadiness,
  });

  factory DevopsSummaryResponseDto.fromJson(Map<String, dynamic> json) {
    return DevopsSummaryResponseDto(
      workspaceId: _string(json['workspaceId']),
      channelId: _string(json['channelId']),
      releaseStatus: _optionalString(
        json['releaseStatus'],
        defaultValue: 'provider-stack-contract-preview',
      ),
      readOnly: _bool(json['readOnly']),
      paidFeaturesRequired: _bool(json['paidFeaturesRequired']),
      supportSafe: _bool(json['supportSafe']),
      providerReadiness: _providerReadinessList(json['providerReadiness']),
    );
  }

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderReadiness> providerReadiness;

  DevopsChannelSummary toEntity() {
    return DevopsChannelSummary(
      workspaceId: workspaceId,
      channelId: channelId,
      releaseStatus: releaseStatus,
      readOnly: readOnly,
      paidFeaturesRequired: paidFeaturesRequired,
      supportSafe: supportSafe,
      providerReadiness: providerReadiness,
    );
  }
}

class OfficeCapabilitiesResponseDto {
  const OfficeCapabilitiesResponseDto({
    required this.releaseStatus,
    required this.enabled,
    required this.configured,
    required this.supportSafe,
    required this.launchMode,
    required this.defaultProvider,
    required this.providerReadiness,
    required this.supportedFileTypes,
    required this.permissions,
  });

  factory OfficeCapabilitiesResponseDto.fromJson(Map<String, dynamic> json) {
    final permissions = json['permissions'];
    if (permissions is! Map) {
      throw const AppFailure.unknown(
        'The backend returned an invalid Office capabilities response.',
      );
    }
    final permissionJson = permissions.cast<String, dynamic>();

    return OfficeCapabilitiesResponseDto(
      releaseStatus: _optionalString(
        json['releaseStatus'],
        defaultValue: 'provider-stack-contract-preview',
      ),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      supportSafe: _bool(json['supportSafe']),
      launchMode: _string(json['launchMode']),
      defaultProvider: _string(json['defaultProvider']),
      providerReadiness: _providerReadinessList(json['providerReadiness']),
      supportedFileTypes: _optionalStringSet(json['supportedFileTypes']),
      permissions: OfficePermissionModel(
        canView: _bool(permissionJson['canView']),
        canEdit: _bool(permissionJson['canEdit']),
        canComment: _bool(permissionJson['canComment']),
        canReview: _bool(permissionJson['canReview']),
        canFillForms: _bool(permissionJson['canFillForms']),
        reason: _string(permissionJson['reason']),
      ),
    );
  }

  final String releaseStatus;
  final bool enabled;
  final bool configured;
  final bool supportSafe;
  final String launchMode;
  final String defaultProvider;
  final List<ProviderReadiness> providerReadiness;
  final Set<String> supportedFileTypes;
  final OfficePermissionModel permissions;

  OfficeCapabilities toEntity() {
    return OfficeCapabilities(
      releaseStatus: releaseStatus,
      enabled: enabled,
      configured: configured,
      supportSafe: supportSafe,
      launchMode: launchMode,
      defaultProvider: defaultProvider,
      providerReadiness: providerReadiness,
      supportedFileTypes: supportedFileTypes,
      permissions: permissions,
    );
  }
}

String _string(Object? value) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw const AppFailure.unknown(
    'The backend returned an invalid provider readiness response.',
  );
}

String _optionalString(Object? value, {required String defaultValue}) {
  if (value == null) {
    return defaultValue;
  }
  return _string(value);
}

bool _bool(Object? value) {
  if (value is bool) {
    return value;
  }
  throw const AppFailure.unknown(
    'The backend returned an invalid provider readiness response.',
  );
}

bool _optionalBool(Object? value, {required bool defaultValue}) {
  if (value == null) {
    return defaultValue;
  }
  return _bool(value);
}

Set<String> _stringSet(Object? value) {
  if (value is! List) {
    throw const AppFailure.unknown(
      'The backend returned an invalid provider readiness response.',
    );
  }
  return value.map((item) => _string(item)).toSet();
}

Set<String> _optionalStringSet(Object? value) {
  if (value == null) {
    return const <String>{};
  }
  return _stringSet(value);
}

List<ProviderReadiness> _providerReadinessList(Object? value) {
  if (value is! List) {
    throw const AppFailure.unknown(
      'The backend returned an invalid provider readiness response.',
    );
  }
  return value
      .map((provider) => _providerReadinessDto(provider).toEntity())
      .toList(growable: false);
}

ProviderReadinessDto _providerReadinessDto(Object? provider) {
  if (provider is! Map) {
    throw const AppFailure.unknown(
      'The backend returned an invalid provider readiness response.',
    );
  }
  return ProviderReadinessDto.fromJson(provider.cast<String, dynamic>());
}
