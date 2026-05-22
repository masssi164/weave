enum ProviderModule {
  identityRealm,
  files,
  office,
  calendar,
  contacts,
  forms,
  boards,
  sourceControl,
  ci,
  issueTracker,
  release,
  unknown,
}

enum ProviderState {
  disabled,
  notConfigured,
  configured,
  ready,
  degraded,
  unsupported,
  unknown,
}

class ProviderStatus {
  const ProviderStatus({
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
    this.supportedCapabilities = const <String>{},
    this.unsupportedOperations = const <String>{},
    this.supportSafeErrorCodes = const <String>[],
    this.redactionPolicy =
        'support-safe: no tokens, passwords, app passwords, credentials, authorization headers, or raw provider errors',
    this.candidates = const <String>[],
  });

  final ProviderModule module;
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
  final Set<String> supportedCapabilities;
  final Set<String> unsupportedOperations;
  final List<String> supportSafeErrorCodes;
  final String redactionPolicy;
  final List<String> candidates;

  bool get isReady =>
      enabled && configured && supportSafe && state == ProviderState.ready;

  bool get isUsable =>
      enabled &&
      configured &&
      supportSafe &&
      (state == ProviderState.ready || state == ProviderState.degraded);

  bool get isUnavailable =>
      !enabled ||
      !configured ||
      state == ProviderState.disabled ||
      state == ProviderState.notConfigured ||
      state == ProviderState.unsupported ||
      !supportSafe;

  bool get shouldFailClosed => failClosed && isUnavailable;
}

class ProviderRegistryStatus {
  const ProviderRegistryStatus({
    required this.releaseStatus,
    required this.backendOwnedFacades,
    required this.flutterDirectProviderCallsAllowed,
    required this.supportSafe,
    required this.generatedAt,
    required this.providers,
  });

  final String releaseStatus;
  final bool backendOwnedFacades;
  final bool flutterDirectProviderCallsAllowed;
  final bool supportSafe;
  final DateTime? generatedAt;
  final List<ProviderStatus> providers;

  bool get enforcesBackendFacades =>
      backendOwnedFacades && !flutterDirectProviderCallsAllowed && supportSafe;

  Iterable<ProviderStatus> providersFor(ProviderModule module) {
    return providers.where((provider) => provider.module == module);
  }

  bool moduleReady(ProviderModule module) {
    return providersFor(module).any((provider) => provider.isReady);
  }
}

class DevopsSummary {
  const DevopsSummary({
    required this.workspaceId,
    required this.channelId,
    required this.releaseStatus,
    required this.readOnly,
    required this.paidFeaturesRequired,
    required this.supportSafe,
    required this.providerReadiness,
    this.linkedProjectCount = 0,
    this.repositoryCount = 0,
    this.openIssueCount = 0,
    this.mergeRequestCount = 0,
    this.pipelineCount = 0,
    this.releaseCount = 0,
  });

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderStatus> providerReadiness;
  final int linkedProjectCount;
  final int repositoryCount;
  final int openIssueCount;
  final int mergeRequestCount;
  final int pipelineCount;
  final int releaseCount;

  bool get hasReadyProvider => providerReadiness.any((provider) {
    return switch (provider.module) {
      ProviderModule.sourceControl ||
      ProviderModule.ci ||
      ProviderModule.issueTracker ||
      ProviderModule.release => provider.isUsable,
      _ => false,
    };
  });

  bool get isAvailable => supportSafe && readOnly && hasReadyProvider;

  bool get shouldFailClosed => !isAvailable;
}

class OfficeCapabilityFlags {
  const OfficeCapabilityFlags({
    required this.view,
    required this.edit,
    required this.comment,
    required this.review,
    required this.formFill,
  });

  final bool view;
  final bool edit;
  final bool comment;
  final bool review;
  final bool formFill;
}

class OfficePermissions {
  const OfficePermissions({
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

class OfficeLockSessionReadiness {
  const OfficeLockSessionReadiness({
    required this.documentLocks,
    required this.sessionTokens,
    required this.callbackVerification,
    required this.supportSafe,
  });

  final String documentLocks;
  final String sessionTokens;
  final String callbackVerification;
  final bool supportSafe;
}

class OfficeProviderCandidate {
  const OfficeProviderCandidate({
    required this.providerKey,
    required this.displayName,
    required this.defaultCandidate,
    required this.runtimeFit,
    required this.licensingPosture,
    required this.integrationPath,
    required this.notes,
  });

  final String providerKey;
  final String displayName;
  final bool defaultCandidate;
  final String runtimeFit;
  final String licensingPosture;
  final String integrationPath;
  final List<String> notes;
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
    required this.candidates,
    required this.capabilities,
    required this.supportedFileTypes,
    required this.permissions,
    required this.lockSessionReadiness,
  });

  final String releaseStatus;
  final bool enabled;
  final bool configured;
  final bool supportSafe;
  final String launchMode;
  final String defaultProvider;
  final List<ProviderStatus> providerReadiness;
  final List<OfficeProviderCandidate> candidates;
  final OfficeCapabilityFlags capabilities;
  final List<String> supportedFileTypes;
  final OfficePermissions permissions;
  final OfficeLockSessionReadiness lockSessionReadiness;

  bool get hasReadyProvider => providerReadiness.any(
    (provider) => provider.module == ProviderModule.office && provider.isUsable,
  );

  bool get canLaunchView =>
      enabled &&
      configured &&
      supportSafe &&
      capabilities.view &&
      permissions.canView;

  bool get canLaunchEdit =>
      enabled &&
      configured &&
      supportSafe &&
      capabilities.edit &&
      permissions.canEdit;

  bool get isAvailable => canLaunchView && hasReadyProvider;

  bool get shouldFailClosed =>
      !enabled || !configured || !supportSafe || !hasReadyProvider;
}

class OfficeLaunchSession {
  const OfficeLaunchSession({
    required this.sessionId,
    required this.launchMode,
    required this.providerKey,
    required this.expiresAt,
    required this.grantedPermissions,
  });

  final String sessionId;
  final String launchMode;
  final String providerKey;
  final DateTime? expiresAt;
  final List<String> grantedPermissions;
}
