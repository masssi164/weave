import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';

class ProviderRegistryResponseDto {
  const ProviderRegistryResponseDto({
    required this.releaseStatus,
    required this.backendOwnedFacades,
    required this.flutterDirectProviderCallsAllowed,
    required this.supportSafe,
    required this.providers,
  });

  factory ProviderRegistryResponseDto.fromJson(Map<String, dynamic> json) {
    return ProviderRegistryResponseDto(
      releaseStatus: _string(json['releaseStatus'], fallback: 'unknown'),
      backendOwnedFacades: _bool(json['backendOwnedFacades']),
      flutterDirectProviderCallsAllowed: _bool(
        json['flutterDirectProviderCallsAllowed'],
      ),
      supportSafe: _bool(json['supportSafe']),
      providers: _listOfMaps(
        json['providers'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
    );
  }

  final String releaseStatus;
  final bool backendOwnedFacades;
  final bool flutterDirectProviderCallsAllowed;
  final bool supportSafe;
  final List<ProviderStatusResponseDto> providers;

  ProviderStackSnapshot toSnapshot() => ProviderStackSnapshot(
    releaseStatus: releaseStatus,
    backendOwnedFacades: backendOwnedFacades,
    flutterDirectProviderCallsAllowed: flutterDirectProviderCallsAllowed,
    supportSafe: supportSafe,
    providers: providers.map((provider) => provider.toSnapshot()).toList(),
  );
}

class ProviderStatusResponseDto {
  const ProviderStatusResponseDto({
    required this.module,
    required this.providerKey,
    required this.state,
    required this.readiness,
    required this.enabled,
    required this.configured,
    required this.readOnly,
    required this.failClosed,
    required this.supportSafe,
    required this.paidFeaturesRequired,
    required this.summary,
    required this.supportedCapabilities,
    required this.unsupportedOperations,
    required this.supportSafeErrorCodes,
    required this.redactionPolicy,
    required this.candidates,
  });

  factory ProviderStatusResponseDto.fromJson(Map<String, dynamic> json) {
    return ProviderStatusResponseDto(
      module: _string(json['module'], fallback: 'unknown'),
      providerKey: _string(json['providerKey'], fallback: 'unknown'),
      state: _providerState(_string(json['state'], fallback: 'unknown')),
      readiness: _string(json['readiness'], fallback: 'unknown'),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      readOnly: _bool(json['readOnly']),
      failClosed: _bool(json['failClosed']),
      supportSafe: _bool(json['supportSafe']),
      paidFeaturesRequired: _bool(json['paidFeaturesRequired']),
      summary: _safeText(json['summary']),
      supportedCapabilities: _stringList(json['supportedCapabilities']),
      unsupportedOperations: _stringList(json['unsupportedOperations']),
      supportSafeErrorCodes: _stringList(json['supportSafeErrorCodes']),
      redactionPolicy: _safeText(json['redactionPolicy']),
      candidates: _stringList(json['candidates']),
    );
  }

  final String module;
  final String providerKey;
  final ProviderState state;
  final String readiness;
  final bool enabled;
  final bool configured;
  final bool readOnly;
  final bool failClosed;
  final bool supportSafe;
  final bool paidFeaturesRequired;
  final String summary;
  final List<String> supportedCapabilities;
  final List<String> unsupportedOperations;
  final List<String> supportSafeErrorCodes;
  final String redactionPolicy;
  final List<String> candidates;

  ProviderStatusSnapshot toSnapshot() => ProviderStatusSnapshot(
    module: module,
    providerKey: providerKey,
    state: state,
    readiness: readiness,
    enabled: enabled,
    configured: configured,
    readOnly: readOnly,
    failClosed: failClosed,
    supportSafe: supportSafe,
    paidFeaturesRequired: paidFeaturesRequired,
    summary: summary,
    supportedCapabilities: supportedCapabilities,
    unsupportedOperations: unsupportedOperations,
    supportSafeErrorCodes: supportSafeErrorCodes,
    redactionPolicy: redactionPolicy,
    candidates: candidates,
  );
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
      workspaceId: _string(json['workspaceId'], fallback: 'unknown'),
      channelId: _string(json['channelId'], fallback: 'unknown'),
      releaseStatus: _string(json['releaseStatus'], fallback: 'unknown'),
      readOnly: _bool(json['readOnly']),
      paidFeaturesRequired: _bool(json['paidFeaturesRequired']),
      supportSafe: _bool(json['supportSafe']),
      providerReadiness: _listOfMaps(
        json['providerReadiness'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
    );
  }

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderStatusResponseDto> providerReadiness;

  DevopsProviderSummarySnapshot toSnapshot() => DevopsProviderSummarySnapshot(
    workspaceId: workspaceId,
    channelId: channelId,
    releaseStatus: releaseStatus,
    readOnly: readOnly,
    paidFeaturesRequired: paidFeaturesRequired,
    supportSafe: supportSafe,
    providerReadiness: providerReadiness
        .map((provider) => provider.toSnapshot())
        .toList(growable: false),
  );
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
  });

  factory OfficeCapabilitiesResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeCapabilitiesResponseDto(
      releaseStatus: _string(json['releaseStatus'], fallback: 'unknown'),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      supportSafe: _bool(json['supportSafe']),
      launchMode: _string(json['launchMode'], fallback: 'disabled'),
      defaultProvider: _string(json['defaultProvider'], fallback: 'none'),
      providerReadiness: _listOfMaps(
        json['providerReadiness'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
      supportedFileTypes: _stringList(json['supportedFileTypes']),
    );
  }

  final String releaseStatus;
  final bool enabled;
  final bool configured;
  final bool supportSafe;
  final String launchMode;
  final String defaultProvider;
  final List<ProviderStatusResponseDto> providerReadiness;
  final List<String> supportedFileTypes;

  OfficeCapabilitiesSnapshot toSnapshot() => OfficeCapabilitiesSnapshot(
    releaseStatus: releaseStatus,
    enabled: enabled,
    configured: configured,
    supportSafe: supportSafe,
    launchMode: launchMode,
    defaultProvider: defaultProvider,
    providerReadiness: providerReadiness
        .map((provider) => provider.toSnapshot())
        .toList(growable: false),
    supportedFileTypes: supportedFileTypes,
  );
}

class OfficeLaunchResponseDto {
  const OfficeLaunchResponseDto({
    required this.sessionId,
    required this.launchMode,
    required this.providerKey,
    required this.grantedPermissions,
  });

  factory OfficeLaunchResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeLaunchResponseDto(
      sessionId: _string(json['sessionId'], fallback: ''),
      launchMode: _string(json['launchMode'], fallback: 'disabled'),
      providerKey: _string(json['providerKey'], fallback: 'none'),
      grantedPermissions: _stringList(json['grantedPermissions']),
    );
  }

  final String sessionId;
  final String launchMode;
  final String providerKey;
  final List<String> grantedPermissions;

  OfficeLaunchSnapshot toSnapshot() => OfficeLaunchSnapshot(
    sessionId: sessionId,
    launchMode: launchMode,
    providerKey: providerKey,
    grantedPermissions: grantedPermissions,
  );
}

ProviderState _providerState(String value) {
  return switch (value) {
    'disabled' => ProviderState.disabled,
    'not_configured' => ProviderState.notConfigured,
    'configured' => ProviderState.configured,
    'ready' => ProviderState.ready,
    'degraded' => ProviderState.degraded,
    'unsupported' => ProviderState.unsupported,
    _ => ProviderState.unknown,
  };
}

String _string(Object? value, {required String fallback}) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  return fallback;
}

String _safeText(Object? value) {
  if (value is! String || value.trim().isEmpty) {
    return 'Not reported by the Weave backend.';
  }
  final text = value.trim();
  final lower = text.toLowerCase();
  if (lower.contains('token') ||
      lower.contains('secret') ||
      lower.contains('password') ||
      lower.contains('authorization:') ||
      lower.contains('http://') ||
      lower.contains('https://')) {
    return 'Support-safe details only; raw provider data was redacted.';
  }
  return text;
}

bool _bool(Object? value) => value == true;

List<String> _stringList(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value
      .whereType<String>()
      .map((item) => item.trim())
      .where((item) {
        return item.isNotEmpty;
      })
      .toList(growable: false);
}

List<Map<String, dynamic>> _listOfMaps(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value.whereType<Map<String, dynamic>>().toList(growable: false);
}
