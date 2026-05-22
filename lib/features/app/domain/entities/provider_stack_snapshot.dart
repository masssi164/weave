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
    this.generatedAt,
  });

  final String releaseStatus;
  final bool backendOwnedFacades;
  final bool flutterDirectProviderCallsAllowed;
  final bool supportSafe;
  final DateTime? generatedAt;
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
    this.diagnostics = const <String, Object?>{},
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
  final Map<String, Object?> diagnostics;

  bool get available => enabled && configured && state == ProviderState.ready;

  bool get disabled => !enabled || state == ProviderState.disabled;

  bool get unconfigured => !configured || state == ProviderState.notConfigured;
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
    this.linkedProjects = const <LinkedSourceProjectSnapshot>[],
    this.repositories = const <SourceRepositorySnapshot>[],
    this.openIssues = const <DevopsIssueSummarySnapshot>[],
    this.mergeRequests = const <DevopsMergeRequestSummarySnapshot>[],
    this.pipelines = const <DevopsPipelineSummarySnapshot>[],
    this.releases = const <DevopsReleaseSummarySnapshot>[],
  });

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderStatusSnapshot> providerReadiness;
  final List<LinkedSourceProjectSnapshot> linkedProjects;
  final List<SourceRepositorySnapshot> repositories;
  final List<DevopsIssueSummarySnapshot> openIssues;
  final List<DevopsMergeRequestSummarySnapshot> mergeRequests;
  final List<DevopsPipelineSummarySnapshot> pipelines;
  final List<DevopsReleaseSummarySnapshot> releases;

  bool get failClosed =>
      readOnly || providerReadiness.any((provider) => provider.failClosed);
}

class LinkedSourceProjectSnapshot {
  const LinkedSourceProjectSnapshot({
    required this.id,
    required this.displayName,
    required this.providerKey,
    required this.visibility,
    required this.repositoryIds,
  });

  final String id;
  final String displayName;
  final String providerKey;
  final String visibility;
  final List<String> repositoryIds;
}

class SourceRepositorySnapshot {
  const SourceRepositorySnapshot({
    required this.id,
    required this.projectId,
    required this.displayName,
    required this.defaultBranch,
    required this.providerKey,
    required this.archived,
  });

  final String id;
  final String projectId;
  final String displayName;
  final String defaultBranch;
  final String providerKey;
  final bool archived;
}

class DevopsIssueSummarySnapshot {
  const DevopsIssueSummarySnapshot({
    required this.id,
    required this.projectId,
    required this.title,
    required this.state,
    required this.providerKey,
    required this.updatedAt,
    required this.labels,
  });

  final String id;
  final String projectId;
  final String title;
  final String state;
  final String providerKey;
  final DateTime? updatedAt;
  final List<String> labels;
}

class DevopsMergeRequestSummarySnapshot {
  const DevopsMergeRequestSummarySnapshot({
    required this.id,
    required this.repositoryId,
    required this.title,
    required this.sourceBranch,
    required this.targetBranch,
    required this.state,
    required this.providerKey,
    required this.updatedAt,
  });

  final String id;
  final String repositoryId;
  final String title;
  final String sourceBranch;
  final String targetBranch;
  final String state;
  final String providerKey;
  final DateTime? updatedAt;
}

class DevopsPipelineSummarySnapshot {
  const DevopsPipelineSummarySnapshot({
    required this.id,
    required this.repositoryId,
    required this.ref,
    required this.state,
    required this.providerKey,
    required this.updatedAt,
    required this.jobs,
  });

  final String id;
  final String repositoryId;
  final String ref;
  final String state;
  final String providerKey;
  final DateTime? updatedAt;
  final List<DevopsJobSummarySnapshot> jobs;
}

class DevopsJobSummarySnapshot {
  const DevopsJobSummarySnapshot({
    required this.id,
    required this.name,
    required this.state,
    required this.startedAt,
    required this.finishedAt,
  });

  final String id;
  final String name;
  final String state;
  final DateTime? startedAt;
  final DateTime? finishedAt;
}

class DevopsReleaseSummarySnapshot {
  const DevopsReleaseSummarySnapshot({
    required this.id,
    required this.repositoryId,
    required this.name,
    required this.tagName,
    required this.providerKey,
    required this.releasedAt,
  });

  final String id;
  final String repositoryId;
  final String name;
  final String tagName;
  final String providerKey;
  final DateTime? releasedAt;
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
    required this.candidates,
    required this.capabilities,
    required this.permissions,
    required this.lockSessionReadiness,
  });

  final String releaseStatus;
  final bool enabled;
  final bool configured;
  final bool supportSafe;
  final String launchMode;
  final String defaultProvider;
  final List<ProviderStatusSnapshot> providerReadiness;
  final List<String> supportedFileTypes;
  final List<OfficeProviderCandidateSnapshot> candidates;
  final OfficeCapabilityFlagsSnapshot capabilities;
  final OfficePermissionModelSnapshot permissions;
  final OfficeLockSessionReadinessSnapshot lockSessionReadiness;

  bool get launchAvailable =>
      enabled &&
      configured &&
      launchMode != 'disabled' &&
      launchMode != 'unavailable';

  bool get launchFailClosed =>
      !launchAvailable ||
      providerReadiness.any((provider) => provider.failClosed);
}

class OfficeProviderCandidateSnapshot {
  const OfficeProviderCandidateSnapshot({
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

class OfficeCapabilityFlagsSnapshot {
  const OfficeCapabilityFlagsSnapshot({
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

  List<String> get enabledModes => <String>[
    if (view) 'view',
    if (edit) 'edit',
    if (comment) 'comment',
    if (review) 'review',
    if (formFill) 'form-fill',
  ];
}

class OfficePermissionModelSnapshot {
  const OfficePermissionModelSnapshot({
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

class OfficeLockSessionReadinessSnapshot {
  const OfficeLockSessionReadinessSnapshot({
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

class OfficeLaunchSnapshot {
  const OfficeLaunchSnapshot._({
    required this.launched,
    required this.failClosed,
    this.sessionId,
    this.launchMode,
    this.providerKey,
    this.expiresAt,
    this.grantedPermissions = const <String>[],
    this.errorCode,
    this.message,
    this.requestId,
  });

  const OfficeLaunchSnapshot.launched({
    required String sessionId,
    required String launchMode,
    required String providerKey,
    required List<String> grantedPermissions,
    DateTime? expiresAt,
  }) : this._(
         launched: true,
         failClosed: false,
         sessionId: sessionId,
         launchMode: launchMode,
         providerKey: providerKey,
         expiresAt: expiresAt,
         grantedPermissions: grantedPermissions,
       );

  const OfficeLaunchSnapshot.failClosed({
    required String errorCode,
    required String message,
    String? requestId,
  }) : this._(
         launched: false,
         failClosed: true,
         errorCode: errorCode,
         message: message,
         requestId: requestId,
       );

  final bool launched;
  final bool failClosed;
  final String? sessionId;
  final String? launchMode;
  final String? providerKey;
  final DateTime? expiresAt;
  final List<String> grantedPermissions;
  final String? errorCode;
  final String? message;
  final String? requestId;
}
