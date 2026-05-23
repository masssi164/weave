import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';

class ProviderRegistryResponseDto {
  const ProviderRegistryResponseDto({
    required this.releaseStatus,
    required this.backendOwnedFacades,
    required this.flutterDirectProviderCallsAllowed,
    required this.supportSafe,
    required this.generatedAt,
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
      generatedAt: _dateTime(json['generatedAt']),
      providers: _listOfMaps(
        json['providers'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
    );
  }

  final String releaseStatus;
  final bool backendOwnedFacades;
  final bool flutterDirectProviderCallsAllowed;
  final bool supportSafe;
  final DateTime? generatedAt;
  final List<ProviderStatusResponseDto> providers;

  ProviderStackSnapshot toSnapshot() => ProviderStackSnapshot(
    releaseStatus: releaseStatus,
    backendOwnedFacades: backendOwnedFacades,
    flutterDirectProviderCallsAllowed: flutterDirectProviderCallsAllowed,
    supportSafe: supportSafe,
    generatedAt: generatedAt,
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
    required this.diagnostics,
  });

  factory ProviderStatusResponseDto.fromJson(Map<String, dynamic> json) {
    return ProviderStatusResponseDto(
      module: _string(json['module'], fallback: 'unknown'),
      providerKey: _safeProviderKey(json['providerKey']),
      state: _providerState(_string(json['state'], fallback: 'unknown')),
      readiness: _string(json['readiness'], fallback: 'unknown'),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      readOnly: _bool(json['readOnly']),
      failClosed: _bool(json['failClosed']),
      supportSafe: _bool(json['supportSafe']),
      paidFeaturesRequired: _bool(json['paidFeaturesRequired']),
      summary: _safeText(json['summary']),
      supportedCapabilities: _safeStringList(json['supportedCapabilities']),
      unsupportedOperations: _safeStringList(json['unsupportedOperations']),
      supportSafeErrorCodes: _safeStringList(json['supportSafeErrorCodes']),
      redactionPolicy: _safeText(json['redactionPolicy']),
      candidates: _safeStringList(json['candidates']),
      diagnostics: _safeDiagnostics(json['diagnostics']),
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
  final Map<String, Object?> diagnostics;

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
    diagnostics: diagnostics,
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
    required this.linkedProjects,
    required this.repositories,
    required this.openIssues,
    required this.mergeRequests,
    required this.pipelines,
    required this.releases,
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
      linkedProjects: _listOfMaps(
        json['linkedProjects'],
      ).map(LinkedSourceProjectResponseDto.fromJson).toList(growable: false),
      repositories: _listOfMaps(
        json['repositories'],
      ).map(SourceRepositoryResponseDto.fromJson).toList(growable: false),
      openIssues: _listOfMaps(
        json['openIssues'],
      ).map(DevopsIssueSummaryResponseDto.fromJson).toList(growable: false),
      mergeRequests: _listOfMaps(json['mergeRequests'])
          .map(DevopsMergeRequestSummaryResponseDto.fromJson)
          .toList(growable: false),
      pipelines: _listOfMaps(
        json['pipelines'],
      ).map(DevopsPipelineSummaryResponseDto.fromJson).toList(growable: false),
      releases: _listOfMaps(
        json['releases'],
      ).map(DevopsReleaseSummaryResponseDto.fromJson).toList(growable: false),
    );
  }

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderStatusResponseDto> providerReadiness;
  final List<LinkedSourceProjectResponseDto> linkedProjects;
  final List<SourceRepositoryResponseDto> repositories;
  final List<DevopsIssueSummaryResponseDto> openIssues;
  final List<DevopsMergeRequestSummaryResponseDto> mergeRequests;
  final List<DevopsPipelineSummaryResponseDto> pipelines;
  final List<DevopsReleaseSummaryResponseDto> releases;

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
    linkedProjects: linkedProjects
        .map((project) => project.toSnapshot())
        .toList(growable: false),
    repositories: repositories
        .map((repository) => repository.toSnapshot())
        .toList(growable: false),
    openIssues: openIssues
        .map((issue) => issue.toSnapshot())
        .toList(growable: false),
    mergeRequests: mergeRequests
        .map((mergeRequest) => mergeRequest.toSnapshot())
        .toList(growable: false),
    pipelines: pipelines
        .map((pipeline) => pipeline.toSnapshot())
        .toList(growable: false),
    releases: releases
        .map((release) => release.toSnapshot())
        .toList(growable: false),
  );
}

class LinkedSourceProjectResponseDto {
  const LinkedSourceProjectResponseDto({
    required this.id,
    required this.displayName,
    required this.providerKey,
    required this.visibility,
    required this.repositoryIds,
  });

  factory LinkedSourceProjectResponseDto.fromJson(Map<String, dynamic> json) {
    return LinkedSourceProjectResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      displayName: _safeText(json['displayName']),
      providerKey: _safeProviderKey(json['providerKey']),
      visibility: _string(json['visibility'], fallback: 'unknown'),
      repositoryIds: _safeStringList(json['repositoryIds']),
    );
  }

  final String id;
  final String displayName;
  final String providerKey;
  final String visibility;
  final List<String> repositoryIds;

  LinkedSourceProjectSnapshot toSnapshot() => LinkedSourceProjectSnapshot(
    id: id,
    displayName: displayName,
    providerKey: providerKey,
    visibility: visibility,
    repositoryIds: repositoryIds,
  );
}

class SourceRepositoryResponseDto {
  const SourceRepositoryResponseDto({
    required this.id,
    required this.projectId,
    required this.displayName,
    required this.defaultBranch,
    required this.providerKey,
    required this.archived,
  });

  factory SourceRepositoryResponseDto.fromJson(Map<String, dynamic> json) {
    return SourceRepositoryResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      projectId: _string(json['projectId'], fallback: 'unknown'),
      displayName: _safeText(json['displayName']),
      defaultBranch: _string(json['defaultBranch'], fallback: 'unknown'),
      providerKey: _safeProviderKey(json['providerKey']),
      archived: _bool(json['archived']),
    );
  }

  final String id;
  final String projectId;
  final String displayName;
  final String defaultBranch;
  final String providerKey;
  final bool archived;

  SourceRepositorySnapshot toSnapshot() => SourceRepositorySnapshot(
    id: id,
    projectId: projectId,
    displayName: displayName,
    defaultBranch: defaultBranch,
    providerKey: providerKey,
    archived: archived,
  );
}

class DevopsIssueSummaryResponseDto {
  const DevopsIssueSummaryResponseDto({
    required this.id,
    required this.projectId,
    required this.title,
    required this.state,
    required this.providerKey,
    required this.updatedAt,
    required this.labels,
  });

  factory DevopsIssueSummaryResponseDto.fromJson(Map<String, dynamic> json) {
    return DevopsIssueSummaryResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      projectId: _string(json['projectId'], fallback: 'unknown'),
      title: _safeText(json['title']),
      state: _string(json['state'], fallback: 'unknown'),
      providerKey: _safeProviderKey(json['providerKey']),
      updatedAt: _dateTime(json['updatedAt']),
      labels: _safeStringList(json['labels']),
    );
  }

  final String id;
  final String projectId;
  final String title;
  final String state;
  final String providerKey;
  final DateTime? updatedAt;
  final List<String> labels;

  DevopsIssueSummarySnapshot toSnapshot() => DevopsIssueSummarySnapshot(
    id: id,
    projectId: projectId,
    title: title,
    state: state,
    providerKey: providerKey,
    updatedAt: updatedAt,
    labels: labels,
  );
}

class DevopsMergeRequestSummaryResponseDto {
  const DevopsMergeRequestSummaryResponseDto({
    required this.id,
    required this.repositoryId,
    required this.title,
    required this.sourceBranch,
    required this.targetBranch,
    required this.state,
    required this.providerKey,
    required this.updatedAt,
  });

  factory DevopsMergeRequestSummaryResponseDto.fromJson(
    Map<String, dynamic> json,
  ) {
    return DevopsMergeRequestSummaryResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      repositoryId: _string(json['repositoryId'], fallback: 'unknown'),
      title: _safeText(json['title']),
      sourceBranch: _string(json['sourceBranch'], fallback: 'unknown'),
      targetBranch: _string(json['targetBranch'], fallback: 'unknown'),
      state: _string(json['state'], fallback: 'unknown'),
      providerKey: _safeProviderKey(json['providerKey']),
      updatedAt: _dateTime(json['updatedAt']),
    );
  }

  final String id;
  final String repositoryId;
  final String title;
  final String sourceBranch;
  final String targetBranch;
  final String state;
  final String providerKey;
  final DateTime? updatedAt;

  DevopsMergeRequestSummarySnapshot toSnapshot() =>
      DevopsMergeRequestSummarySnapshot(
        id: id,
        repositoryId: repositoryId,
        title: title,
        sourceBranch: sourceBranch,
        targetBranch: targetBranch,
        state: state,
        providerKey: providerKey,
        updatedAt: updatedAt,
      );
}

class DevopsPipelineSummaryResponseDto {
  const DevopsPipelineSummaryResponseDto({
    required this.id,
    required this.repositoryId,
    required this.ref,
    required this.state,
    required this.providerKey,
    required this.updatedAt,
    required this.jobs,
  });

  factory DevopsPipelineSummaryResponseDto.fromJson(Map<String, dynamic> json) {
    return DevopsPipelineSummaryResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      repositoryId: _string(json['repositoryId'], fallback: 'unknown'),
      ref: _string(json['ref'], fallback: 'unknown'),
      state: _string(json['state'], fallback: 'unknown'),
      providerKey: _safeProviderKey(json['providerKey']),
      updatedAt: _dateTime(json['updatedAt']),
      jobs: _listOfMaps(
        json['jobs'],
      ).map(DevopsJobSummaryResponseDto.fromJson).toList(growable: false),
    );
  }

  final String id;
  final String repositoryId;
  final String ref;
  final String state;
  final String providerKey;
  final DateTime? updatedAt;
  final List<DevopsJobSummaryResponseDto> jobs;

  DevopsPipelineSummarySnapshot toSnapshot() => DevopsPipelineSummarySnapshot(
    id: id,
    repositoryId: repositoryId,
    ref: ref,
    state: state,
    providerKey: providerKey,
    updatedAt: updatedAt,
    jobs: jobs.map((job) => job.toSnapshot()).toList(growable: false),
  );
}

class DevopsJobSummaryResponseDto {
  const DevopsJobSummaryResponseDto({
    required this.id,
    required this.name,
    required this.state,
    required this.startedAt,
    required this.finishedAt,
  });

  factory DevopsJobSummaryResponseDto.fromJson(Map<String, dynamic> json) {
    return DevopsJobSummaryResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      name: _safeText(json['name']),
      state: _string(json['state'], fallback: 'unknown'),
      startedAt: _dateTime(json['startedAt']),
      finishedAt: _dateTime(json['finishedAt']),
    );
  }

  final String id;
  final String name;
  final String state;
  final DateTime? startedAt;
  final DateTime? finishedAt;

  DevopsJobSummarySnapshot toSnapshot() => DevopsJobSummarySnapshot(
    id: id,
    name: name,
    state: state,
    startedAt: startedAt,
    finishedAt: finishedAt,
  );
}

class DevopsReleaseSummaryResponseDto {
  const DevopsReleaseSummaryResponseDto({
    required this.id,
    required this.repositoryId,
    required this.name,
    required this.tagName,
    required this.providerKey,
    required this.releasedAt,
  });

  factory DevopsReleaseSummaryResponseDto.fromJson(Map<String, dynamic> json) {
    return DevopsReleaseSummaryResponseDto(
      id: _string(json['id'], fallback: 'unknown'),
      repositoryId: _string(json['repositoryId'], fallback: 'unknown'),
      name: _safeText(json['name']),
      tagName: _string(json['tagName'], fallback: 'unknown'),
      providerKey: _safeProviderKey(json['providerKey']),
      releasedAt: _dateTime(json['releasedAt']),
    );
  }

  final String id;
  final String repositoryId;
  final String name;
  final String tagName;
  final String providerKey;
  final DateTime? releasedAt;

  DevopsReleaseSummarySnapshot toSnapshot() => DevopsReleaseSummarySnapshot(
    id: id,
    repositoryId: repositoryId,
    name: name,
    tagName: tagName,
    providerKey: providerKey,
    releasedAt: releasedAt,
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
    required this.candidates,
    required this.capabilities,
    required this.permissions,
    required this.lockSessionReadiness,
  });

  factory OfficeCapabilitiesResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeCapabilitiesResponseDto(
      releaseStatus: _string(json['releaseStatus'], fallback: 'unknown'),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      supportSafe: _bool(json['supportSafe']),
      launchMode: _string(json['launchMode'], fallback: 'disabled'),
      defaultProvider: _safeProviderKey(json['defaultProvider']),
      providerReadiness: _listOfMaps(
        json['providerReadiness'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
      supportedFileTypes: _safeStringList(json['supportedFileTypes']),
      candidates: _listOfMaps(json['candidates'])
          .map(OfficeProviderCandidateResponseDto.fromJson)
          .toList(growable: false),
      capabilities: OfficeCapabilityFlagsResponseDto.fromJson(
        _map(json['capabilities']),
      ),
      permissions: OfficePermissionModelResponseDto.fromJson(
        _map(json['permissions']),
      ),
      lockSessionReadiness: OfficeLockSessionReadinessResponseDto.fromJson(
        _map(json['lockSessionReadiness']),
      ),
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
  final List<OfficeProviderCandidateResponseDto> candidates;
  final OfficeCapabilityFlagsResponseDto capabilities;
  final OfficePermissionModelResponseDto permissions;
  final OfficeLockSessionReadinessResponseDto lockSessionReadiness;

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
    candidates: candidates
        .map((candidate) => candidate.toSnapshot())
        .toList(growable: false),
    capabilities: capabilities.toSnapshot(),
    permissions: permissions.toSnapshot(),
    lockSessionReadiness: lockSessionReadiness.toSnapshot(),
  );
}

class OfficeProviderCandidateResponseDto {
  const OfficeProviderCandidateResponseDto({
    required this.providerKey,
    required this.displayName,
    required this.defaultCandidate,
    required this.runtimeFit,
    required this.licensingPosture,
    required this.integrationPath,
    required this.notes,
  });

  factory OfficeProviderCandidateResponseDto.fromJson(
    Map<String, dynamic> json,
  ) {
    return OfficeProviderCandidateResponseDto(
      providerKey: _safeProviderKey(json['providerKey']),
      displayName: _safeText(json['displayName']),
      defaultCandidate: _bool(json['defaultCandidate']),
      runtimeFit: _safeText(json['runtimeFit']),
      licensingPosture: _safeText(json['licensingPosture']),
      integrationPath: _safeText(json['integrationPath']),
      notes: _safeStringList(json['notes']),
    );
  }

  final String providerKey;
  final String displayName;
  final bool defaultCandidate;
  final String runtimeFit;
  final String licensingPosture;
  final String integrationPath;
  final List<String> notes;

  OfficeProviderCandidateSnapshot toSnapshot() =>
      OfficeProviderCandidateSnapshot(
        providerKey: providerKey,
        displayName: displayName,
        defaultCandidate: defaultCandidate,
        runtimeFit: runtimeFit,
        licensingPosture: licensingPosture,
        integrationPath: integrationPath,
        notes: notes,
      );
}

class OfficeCapabilityFlagsResponseDto {
  const OfficeCapabilityFlagsResponseDto({
    required this.view,
    required this.edit,
    required this.comment,
    required this.review,
    required this.formFill,
  });

  factory OfficeCapabilityFlagsResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeCapabilityFlagsResponseDto(
      view: _bool(json['view']),
      edit: _bool(json['edit']),
      comment: _bool(json['comment']),
      review: _bool(json['review']),
      formFill: _bool(json['formFill']),
    );
  }

  final bool view;
  final bool edit;
  final bool comment;
  final bool review;
  final bool formFill;

  OfficeCapabilityFlagsSnapshot toSnapshot() => OfficeCapabilityFlagsSnapshot(
    view: view,
    edit: edit,
    comment: comment,
    review: review,
    formFill: formFill,
  );
}

class OfficePermissionModelResponseDto {
  const OfficePermissionModelResponseDto({
    required this.canView,
    required this.canEdit,
    required this.canComment,
    required this.canReview,
    required this.canFillForms,
    required this.reason,
  });

  factory OfficePermissionModelResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficePermissionModelResponseDto(
      canView: _bool(json['canView']),
      canEdit: _bool(json['canEdit']),
      canComment: _bool(json['canComment']),
      canReview: _bool(json['canReview']),
      canFillForms: _bool(json['canFillForms']),
      reason: _safeText(json['reason']),
    );
  }

  final bool canView;
  final bool canEdit;
  final bool canComment;
  final bool canReview;
  final bool canFillForms;
  final String reason;

  OfficePermissionModelSnapshot toSnapshot() => OfficePermissionModelSnapshot(
    canView: canView,
    canEdit: canEdit,
    canComment: canComment,
    canReview: canReview,
    canFillForms: canFillForms,
    reason: reason,
  );
}

class OfficeLockSessionReadinessResponseDto {
  const OfficeLockSessionReadinessResponseDto({
    required this.documentLocks,
    required this.sessionTokens,
    required this.callbackVerification,
    required this.supportSafe,
  });

  factory OfficeLockSessionReadinessResponseDto.fromJson(
    Map<String, dynamic> json,
  ) {
    return OfficeLockSessionReadinessResponseDto(
      documentLocks: _string(json['documentLocks'], fallback: 'unavailable'),
      sessionTokens: _string(json['sessionTokens'], fallback: 'unavailable'),
      callbackVerification: _string(
        json['callbackVerification'],
        fallback: 'unavailable',
      ),
      supportSafe: _bool(json['supportSafe']),
    );
  }

  final String documentLocks;
  final String sessionTokens;
  final String callbackVerification;
  final bool supportSafe;

  OfficeLockSessionReadinessSnapshot toSnapshot() =>
      OfficeLockSessionReadinessSnapshot(
        documentLocks: documentLocks,
        sessionTokens: sessionTokens,
        callbackVerification: callbackVerification,
        supportSafe: supportSafe,
      );
}

class OfficeLaunchResponseDto {
  const OfficeLaunchResponseDto({
    required this.sessionId,
    required this.launchMode,
    required this.providerKey,
    required this.grantedPermissions,
    required this.expiresAt,
  });

  factory OfficeLaunchResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeLaunchResponseDto(
      sessionId: _string(json['sessionId'], fallback: ''),
      launchMode: _string(json['launchMode'], fallback: 'disabled'),
      providerKey: _safeProviderKey(json['providerKey']),
      grantedPermissions: _safeStringList(json['grantedPermissions']),
      expiresAt: _dateTime(json['expiresAt']),
    );
  }

  final String sessionId;
  final String launchMode;
  final String providerKey;
  final List<String> grantedPermissions;
  final DateTime? expiresAt;

  OfficeLaunchSnapshot toSnapshot() => OfficeLaunchSnapshot.launched(
    sessionId: sessionId,
    launchMode: launchMode,
    providerKey: providerKey,
    grantedPermissions: grantedPermissions,
    expiresAt: expiresAt,
  );
}

class OfficeLaunchErrorResponseDto {
  const OfficeLaunchErrorResponseDto({
    required this.code,
    required this.message,
    required this.requestId,
  });

  factory OfficeLaunchErrorResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeLaunchErrorResponseDto(
      code: _string(json['code'], fallback: 'office-launch-fail-closed'),
      message: _safeText(json['message']),
      requestId: _nullableString(json['requestId']),
    );
  }

  final String code;
  final String message;
  final String? requestId;

  OfficeLaunchSnapshot toSnapshot() => OfficeLaunchSnapshot.failClosed(
    errorCode: code,
    message: message,
    requestId: requestId,
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

String _safeProviderKey(Object? value) {
  final key = _string(value, fallback: 'unknown');
  return _containsSensitiveText(key) ? 'unknown' : key;
}

String _safeText(Object? value) {
  if (value is! String || value.trim().isEmpty) {
    return '';
  }
  final text = value.trim();
  return _containsSensitiveText(text) ? '' : text;
}

bool _containsSensitiveText(String text) {
  final lower = text.toLowerCase();
  return lower.contains('token') ||
      lower.contains('secret') ||
      lower.contains('password') ||
      lower.contains('authorization:') ||
      lower.contains('bearer ') ||
      lower.contains('http://') ||
      lower.contains('https://');
}

bool _bool(Object? value) => value == true;

List<String> _safeStringList(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value
      .whereType<String>()
      .map((item) => item.trim())
      .where((item) => item.isNotEmpty && !_containsSensitiveText(item))
      .toList(growable: false);
}

Map<String, Object?> _safeDiagnostics(Object? value) {
  if (value is! Map<String, dynamic>) {
    return const <String, Object?>{};
  }

  final sanitized = <String, Object?>{};
  for (final entry in value.entries) {
    final safeValue = _safeDiagnosticValue(entry.value);
    if (safeValue == null) {
      continue;
    }
    if (_containsSensitiveText(entry.key) &&
        !_isSafeConfigurationBoolean(entry.key, safeValue)) {
      continue;
    }
    sanitized[entry.key] = safeValue;
  }

  return Map<String, Object?>.unmodifiable(sanitized);
}

bool _isSafeConfigurationBoolean(String key, Object value) {
  if (value is! bool) {
    return false;
  }
  return switch (key.toLowerCase()) {
    'livekiturlconfigured' ||
    'apikeyconfigured' ||
    'apisecretconfigured' ||
    'tokenendpointconfigured' ||
    'directcredentialmodeconfigured' ||
    'tokenendpointmodeconfigured' ||
    'secretsreturned' => true,
    _ => false,
  };
}

Object? _safeDiagnosticValue(Object? value) {
  return switch (value) {
    String text when text.trim().isNotEmpty && !_containsSensitiveText(text) =>
      text.trim(),
    bool value => value,
    num value => value,
    List value => [
      for (final item in value)
        if (_safeDiagnosticValue(item) case final safeItem?) safeItem,
    ],
    Map<String, dynamic> value => _safeDiagnostics(value),
    _ => null,
  };
}

List<Map<String, dynamic>> _listOfMaps(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value.whereType<Map<String, dynamic>>().toList(growable: false);
}

Map<String, dynamic> _map(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  return const <String, dynamic>{};
}

DateTime? _dateTime(Object? value) {
  if (value is String && value.trim().isNotEmpty) {
    return DateTime.tryParse(value.trim());
  }
  return null;
}

String? _nullableString(Object? value) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  return null;
}
