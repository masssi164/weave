enum ProviderState {
  disabled,
  notConfigured,
  configured,
  ready,
  degraded,
  unsupported,
  unknown,
}

class ProviderStackSnapshot {
  const ProviderStackSnapshot({
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
  final List<ProviderStatusSnapshot> providers;

  bool get failClosed =>
      backendOwnedFacades && !flutterDirectProviderCallsAllowed && supportSafe;

  Iterable<ProviderStatusSnapshot> get unavailableProviders => providers.where(
    (provider) =>
        provider.failClosed &&
        (provider.state == ProviderState.disabled ||
            provider.state == ProviderState.notConfigured ||
            provider.state == ProviderState.unsupported),
  );
}

class ProviderStatusSnapshot {
  const ProviderStatusSnapshot({
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

  bool get available => enabled && configured && state == ProviderState.ready;
}

class DevopsProviderSummarySnapshot {
  const DevopsProviderSummarySnapshot({
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
  final List<ProviderStatusSnapshot> providerReadiness;
}

class OfficeCapabilitiesSnapshot {
  const OfficeCapabilitiesSnapshot({
    required this.releaseStatus,
    required this.enabled,
    required this.configured,
    required this.supportSafe,
    required this.launchMode,
    required this.defaultProvider,
    required this.providerReadiness,
    required this.supportedFileTypes,
  });

  final String releaseStatus;
  final bool enabled;
  final bool configured;
  final bool supportSafe;
  final String launchMode;
  final String defaultProvider;
  final List<ProviderStatusSnapshot> providerReadiness;
  final List<String> supportedFileTypes;

  bool get launchAvailable => enabled && configured && launchMode != 'disabled';
}

class OfficeLaunchSnapshot {
  const OfficeLaunchSnapshot({
    required this.sessionId,
    required this.launchMode,
    required this.providerKey,
    required this.grantedPermissions,
  });

  final String sessionId;
  final String launchMode;
  final String providerKey;
  final List<String> grantedPermissions;
}
