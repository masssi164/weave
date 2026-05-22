class ProviderStackStatus {
  const ProviderStackStatus({
    required this.releaseStatus,
    required this.backendOwnedFacades,
    required this.flutterDirectProviderCallsAllowed,
    required this.supportSafe,
    required this.providers,
  });

  final String releaseStatus;
  final bool backendOwnedFacades;
  final bool flutterDirectProviderCallsAllowed;
  final bool supportSafe;
  final List<ProviderReadiness> providers;

  Iterable<ProviderReadiness> byModule(String module) {
    return providers.where((provider) => provider.module == module);
  }

  bool get allOptionalProvidersFailClosed {
    const optionalModules = <String>{
      'office',
      'contacts',
      'forms',
      'source-control',
      'issue-tracker',
      'ci',
      'release',
    };
    final optionalProviders = providers
        .where((provider) => optionalModules.contains(provider.module))
        .toList(growable: false);
    return optionalProviders.isNotEmpty &&
        optionalProviders.every((provider) => provider.isFailClosedUnavailable);
  }

  bool get satisfiesFrontendBoundary =>
      backendOwnedFacades && !flutterDirectProviderCallsAllowed && supportSafe;

  bool moduleFailsClosed(String module) {
    final moduleProviders = byModule(module).toList(growable: false);
    return moduleProviders.isNotEmpty &&
        moduleProviders.every(
          (provider) => provider.failClosed && provider.supportSafe,
        );
  }
}

class ProviderReadiness {
  const ProviderReadiness({
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
    this.paidFeaturesRequired = false,
    this.supportSafeErrorCodes = const <String>{},
    this.redactionPolicy =
        'support-safe: no tokens, passwords, credentials, authorization headers, or raw provider errors',
    this.candidates = const <String>{},
  });

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

  bool get isReady => enabled && configured && readiness == 'ready';

  bool get isFailClosedUnavailable =>
      !enabled && !configured && failClosed && supportSafe;
}

class DevopsChannelSummary {
  const DevopsChannelSummary({
    required this.workspaceId,
    required this.channelId,
    required this.releaseStatus,
    required this.readOnly,
    required this.paidFeaturesRequired,
    required this.supportSafe,
    required this.providerReadiness,
  });

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderReadiness> providerReadiness;

  bool get isUnavailableFailClosed =>
      supportSafe &&
      readOnly &&
      providerReadiness.isNotEmpty &&
      providerReadiness.every(
        (provider) => !provider.configured && provider.failClosed,
      );
}

class OfficeCapabilities {
  const OfficeCapabilities({
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

  final String releaseStatus;
  final bool enabled;
  final bool configured;
  final bool supportSafe;
  final String launchMode;
  final String defaultProvider;
  final List<ProviderReadiness> providerReadiness;
  final Set<String> supportedFileTypes;
  final OfficePermissionModel permissions;

  bool get canLaunch =>
      enabled &&
      configured &&
      launchMode != 'unavailable' &&
      permissions.canView;

  bool get isUnavailableFailClosed =>
      !enabled &&
      !configured &&
      supportSafe &&
      launchMode == 'unavailable' &&
      !permissions.canView &&
      !permissions.canEdit;
}

class OfficePermissionModel {
  const OfficePermissionModel({
    required this.canView,
    required this.canEdit,
    required this.canComment,
    required this.canReview,
    required this.canFillForms,
    required this.reason,
  });

  final bool canView;
  final bool canEdit;
  final bool canComment;
  final bool canReview;
  final bool canFillForms;
  final String reason;
}
