import 'package:weave/features/app/domain/entities/provider_stack_status.dart';

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
      releaseStatus: _string(json['releaseStatus']),
      backendOwnedFacades: _bool(json['backendOwnedFacades']),
      flutterDirectProviderCallsAllowed: _bool(
        json['flutterDirectProviderCallsAllowed'],
      ),
      supportSafe: _bool(json['supportSafe']),
      generatedAt: DateTime.tryParse(_string(json['generatedAt'])),
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

  ProviderRegistryStatus toEntity() {
    return ProviderRegistryStatus(
      releaseStatus: releaseStatus,
      backendOwnedFacades: backendOwnedFacades,
      flutterDirectProviderCallsAllowed: flutterDirectProviderCallsAllowed,
      supportSafe: supportSafe,
      generatedAt: generatedAt,
      providers: providers
          .map((provider) => provider.toEntity())
          .toList(growable: false),
    );
  }
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
      module: _providerModule(_string(json['module'])),
      providerKey: _string(json['providerKey']),
      state: _providerState(_string(json['state'])),
      readiness: _string(json['readiness']),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      readOnly: _bool(json['readOnly']),
      failClosed: _bool(json['failClosed']),
      supportSafe: _bool(json['supportSafe']),
      paidFeaturesRequired: _bool(json['paidFeaturesRequired']),
      summary: _string(json['summary']),
      supportedCapabilities: _stringSet(json['supportedCapabilities']),
      unsupportedOperations: _stringSet(json['unsupportedOperations']),
      supportSafeErrorCodes: _stringList(json['supportSafeErrorCodes']),
      redactionPolicy: _string(json['redactionPolicy']),
      candidates: _stringList(json['candidates']),
    );
  }

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

  ProviderStatus toEntity() {
    return ProviderStatus(
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
    required this.linkedProjectCount,
    required this.repositoryCount,
    required this.openIssueCount,
    required this.mergeRequestCount,
    required this.pipelineCount,
    required this.releaseCount,
  });

  factory DevopsSummaryResponseDto.fromJson(Map<String, dynamic> json) {
    return DevopsSummaryResponseDto(
      workspaceId: _string(json['workspaceId']),
      channelId: _string(json['channelId']),
      releaseStatus: _string(json['releaseStatus']),
      readOnly: _bool(json['readOnly']),
      paidFeaturesRequired: _bool(json['paidFeaturesRequired']),
      supportSafe: _bool(json['supportSafe']),
      providerReadiness: _listOfMaps(
        json['providerReadiness'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
      linkedProjectCount: _listLength(json['linkedProjects']),
      repositoryCount: _listLength(json['repositories']),
      openIssueCount: _listLength(json['openIssues']),
      mergeRequestCount: _listLength(json['mergeRequests']),
      pipelineCount: _listLength(json['pipelines']),
      releaseCount: _listLength(json['releases']),
    );
  }

  final String workspaceId;
  final String channelId;
  final String releaseStatus;
  final bool readOnly;
  final bool paidFeaturesRequired;
  final bool supportSafe;
  final List<ProviderStatusResponseDto> providerReadiness;
  final int linkedProjectCount;
  final int repositoryCount;
  final int openIssueCount;
  final int mergeRequestCount;
  final int pipelineCount;
  final int releaseCount;

  DevopsSummary toEntity() {
    return DevopsSummary(
      workspaceId: workspaceId,
      channelId: channelId,
      releaseStatus: releaseStatus,
      readOnly: readOnly,
      paidFeaturesRequired: paidFeaturesRequired,
      supportSafe: supportSafe,
      providerReadiness: providerReadiness
          .map((provider) => provider.toEntity())
          .toList(growable: false),
      linkedProjectCount: linkedProjectCount,
      repositoryCount: repositoryCount,
      openIssueCount: openIssueCount,
      mergeRequestCount: mergeRequestCount,
      pipelineCount: pipelineCount,
      releaseCount: releaseCount,
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
    required this.candidates,
    required this.capabilities,
    required this.supportedFileTypes,
    required this.permissions,
    required this.lockSessionReadiness,
  });

  factory OfficeCapabilitiesResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeCapabilitiesResponseDto(
      releaseStatus: _string(json['releaseStatus']),
      enabled: _bool(json['enabled']),
      configured: _bool(json['configured']),
      supportSafe: _bool(json['supportSafe']),
      launchMode: _string(json['launchMode']),
      defaultProvider: _string(json['defaultProvider']),
      providerReadiness: _listOfMaps(
        json['providerReadiness'],
      ).map(ProviderStatusResponseDto.fromJson).toList(growable: false),
      candidates: _listOfMaps(json['candidates'])
          .map(OfficeProviderCandidateResponseDto.fromJson)
          .toList(growable: false),
      capabilities: OfficeCapabilityFlagsResponseDto.fromJson(
        _map(json['capabilities']),
      ),
      supportedFileTypes: _stringList(json['supportedFileTypes']),
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
  final List<OfficeProviderCandidateResponseDto> candidates;
  final OfficeCapabilityFlagsResponseDto capabilities;
  final List<String> supportedFileTypes;
  final OfficePermissionModelResponseDto permissions;
  final OfficeLockSessionReadinessResponseDto lockSessionReadiness;

  OfficeCapabilities toEntity() {
    return OfficeCapabilities(
      releaseStatus: releaseStatus,
      enabled: enabled,
      configured: configured,
      supportSafe: supportSafe,
      launchMode: launchMode,
      defaultProvider: defaultProvider,
      providerReadiness: providerReadiness
          .map((provider) => provider.toEntity())
          .toList(growable: false),
      candidates: candidates
          .map((candidate) => candidate.toEntity())
          .toList(growable: false),
      capabilities: capabilities.toEntity(),
      supportedFileTypes: supportedFileTypes,
      permissions: permissions.toEntity(),
      lockSessionReadiness: lockSessionReadiness.toEntity(),
    );
  }
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

  OfficeCapabilityFlags toEntity() {
    return OfficeCapabilityFlags(
      view: view,
      edit: edit,
      comment: comment,
      review: review,
      formFill: formFill,
    );
  }
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
      reason: _string(json['reason']),
    );
  }

  final bool canView;
  final bool canEdit;
  final bool canComment;
  final bool canReview;
  final bool canFillForms;
  final String reason;

  OfficePermissions toEntity() {
    return OfficePermissions(
      canView: canView,
      canEdit: canEdit,
      canComment: canComment,
      canReview: canReview,
      canFillForms: canFillForms,
      reason: reason,
    );
  }
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
      documentLocks: _string(json['documentLocks']),
      sessionTokens: _string(json['sessionTokens']),
      callbackVerification: _string(json['callbackVerification']),
      supportSafe: _bool(json['supportSafe']),
    );
  }

  final String documentLocks;
  final String sessionTokens;
  final String callbackVerification;
  final bool supportSafe;

  OfficeLockSessionReadiness toEntity() {
    return OfficeLockSessionReadiness(
      documentLocks: documentLocks,
      sessionTokens: sessionTokens,
      callbackVerification: callbackVerification,
      supportSafe: supportSafe,
    );
  }
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
      providerKey: _string(json['providerKey']),
      displayName: _string(json['displayName']),
      defaultCandidate: _bool(json['defaultCandidate']),
      runtimeFit: _string(json['runtimeFit']),
      licensingPosture: _string(json['licensingPosture']),
      integrationPath: _string(json['integrationPath']),
      notes: _stringList(json['notes']),
    );
  }

  final String providerKey;
  final String displayName;
  final bool defaultCandidate;
  final String runtimeFit;
  final String licensingPosture;
  final String integrationPath;
  final List<String> notes;

  OfficeProviderCandidate toEntity() {
    return OfficeProviderCandidate(
      providerKey: providerKey,
      displayName: displayName,
      defaultCandidate: defaultCandidate,
      runtimeFit: runtimeFit,
      licensingPosture: licensingPosture,
      integrationPath: integrationPath,
      notes: notes,
    );
  }
}

class OfficeLaunchResponseDto {
  const OfficeLaunchResponseDto({
    required this.sessionId,
    required this.launchMode,
    required this.providerKey,
    required this.expiresAt,
    required this.grantedPermissions,
  });

  factory OfficeLaunchResponseDto.fromJson(Map<String, dynamic> json) {
    return OfficeLaunchResponseDto(
      sessionId: _string(json['sessionId']),
      launchMode: _string(json['launchMode']),
      providerKey: _string(json['providerKey']),
      expiresAt: DateTime.tryParse(_string(json['expiresAt'])),
      grantedPermissions: _stringList(json['grantedPermissions']),
    );
  }

  final String sessionId;
  final String launchMode;
  final String providerKey;
  final DateTime? expiresAt;
  final List<String> grantedPermissions;

  OfficeLaunchSession toEntity() {
    return OfficeLaunchSession(
      sessionId: sessionId,
      launchMode: launchMode,
      providerKey: providerKey,
      expiresAt: expiresAt,
      grantedPermissions: grantedPermissions,
    );
  }
}

ProviderModule _providerModule(String value) {
  return switch (value) {
    'identity-realm' => ProviderModule.identityRealm,
    'files' => ProviderModule.files,
    'office' => ProviderModule.office,
    'calendar' => ProviderModule.calendar,
    'contacts' => ProviderModule.contacts,
    'forms' => ProviderModule.forms,
    'boards' => ProviderModule.boards,
    'source-control' => ProviderModule.sourceControl,
    'ci' => ProviderModule.ci,
    'issue-tracker' => ProviderModule.issueTracker,
    'release' => ProviderModule.release,
    _ => ProviderModule.unknown,
  };
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

Map<String, dynamic> _map(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return value.map((key, value) => MapEntry(key.toString(), value));
  }
  return const <String, dynamic>{};
}

List<Map<String, dynamic>> _listOfMaps(Object? value) {
  if (value is! List) {
    return const <Map<String, dynamic>>[];
  }
  return value
      .whereType<Map>()
      .map((entry) {
        return entry.map((key, value) => MapEntry(key.toString(), value));
      })
      .toList(growable: false);
}

List<String> _stringList(Object? value) {
  if (value is! Iterable) {
    return const <String>[];
  }
  return value
      .map((entry) => entry?.toString().trim() ?? '')
      .where((entry) {
        return entry.isNotEmpty;
      })
      .toList(growable: false);
}

Set<String> _stringSet(Object? value) => _stringList(value).toSet();

String _string(Object? value, {String fallback = 'unknown'}) {
  final text = value?.toString().trim();
  if (text == null || text.isEmpty) {
    return fallback;
  }
  return text;
}

bool _bool(Object? value) => value == true;

int _listLength(Object? value) => value is List ? value.length : 0;
