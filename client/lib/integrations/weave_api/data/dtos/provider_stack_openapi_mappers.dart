import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;

extension ProviderRegistryOpenApiMapper on openapi.ProviderRegistryResponse {
  ProviderStackSnapshot toSnapshot() => ProviderStackSnapshot(
    releaseStatus: _string(releaseStatus, fallback: 'unknown'),
    backendOwnedFacades: backendOwnedFacades == true,
    flutterDirectProviderCallsAllowed:
        flutterDirectProviderCallsAllowed == true,
    supportSafe: supportSafe == true,
    providerConfigSource: _string(providerConfigSource, fallback: 'unknown'),
    bootstrapDefaultsAreSuggestionsOnly:
        bootstrapDefaultsAreSuggestionsOnly ?? true,
    adminSelectedMappingsRequired: adminSelectedMappingsRequired ?? true,
    generatedAt: _dateTime(generatedAt),
    categories: categories
        .orEmpty()
        .map((category) => category.toSnapshot())
        .toList(growable: false),
    providers: providers
        .orEmpty()
        .map((provider) => provider.toSnapshot())
        .toList(growable: false),
  );
}

extension ProviderCategoryStatusOpenApiMapper
    on openapi.ProviderCategoryStatusResponse {
  ProviderCategoryStatusSnapshot toSnapshot() {
    final categoryName = _string(category, fallback: 'unknown');
    return ProviderCategoryStatusSnapshot(
      category: categoryName,
      label: _safeText(label),
      contract:
          contract?.toSnapshot(fallbackCategory: categoryName) ??
          openapi.ProviderCategoryContractResponse(
            category: categoryName,
          ).toSnapshot(fallbackCategory: categoryName),
      readiness: _providerCategoryReadiness(
        _string(readiness, fallback: 'unknown'),
      ),
      providerRealityLevel: _providerRealityLevel(
        _string(providerRealityLevel, fallback: 'unknown'),
      ),
      memberCapabilityState: _memberCapabilityState(memberCapabilityState),
      realityLevelRemediation: _safeText(realityLevelRemediation),
      policyState: _string(policyState, fallback: 'unknown'),
      memberImpact: _safeText(memberImpact),
      modules: _safeStringList(modules),
      providerCandidates: _safeStringList(providerCandidates),
      selectedProviderKey: _string(
        selectedProviderKey,
        fallback: 'awaiting_admin_selection',
      ),
      choiceModel: _string(choiceModel, fallback: 'not_selected'),
      selectedByAdmin: selectedByAdmin == true,
      bootstrapSuggestionOnly: bootstrapSuggestionOnly ?? true,
      lossyMappingNotes: _safeStringList(lossyMappingNotes),
      adapterEvidence: adapterEvidence
          .orEmpty()
          .map((evidence) => evidence.toSnapshot())
          .toList(growable: false),
      diagnostics: _safeDiagnostics(diagnostics),
    );
  }
}

extension ProviderAdapterReadinessEvidenceOpenApiMapper
    on openapi.ProviderAdapterReadinessEvidenceResponse {
  ProviderAdapterReadinessEvidenceSnapshot toSnapshot() =>
      ProviderAdapterReadinessEvidenceSnapshot(
        domain: _string(domain, fallback: 'unknown'),
        adapterKey: _safeProviderKey(adapterKey),
        configured: configured == true,
        reachable: reachable == true,
        health: _safeText(health),
        providerRealityLevel: _providerRealityLevel(
          _string(providerRealityLevel, fallback: 'unknown'),
        ),
        failClosed: failClosed == true,
        supportSafeDiagnostics: _safeDiagnostics(supportSafeDiagnostics),
        evidenceTimestamp: _dateTime(evidenceTimestamp),
      );
}

extension ProviderCategoryContractOpenApiMapper
    on openapi.ProviderCategoryContractResponse {
  ProviderCategoryContractSnapshot toSnapshot({
    required String fallbackCategory,
  }) => ProviderCategoryContractSnapshot(
    category: _string(category, fallback: fallbackCategory),
    featureCapabilities: _safeStringList(featureCapabilities),
    defaultAdapters: _safeStringList(defaultAdapters),
    externalAdapters: _safeStringList(externalAdapters),
    choiceModels: choiceModels
        .orEmpty()
        .map((choiceModel) => choiceModel.toSnapshot())
        .toList(growable: false),
    adapterModules: _safeStringList(adapterModules),
    stableMemberImpactStates: _safeStringList(stableMemberImpactStates),
    adminSelectable: adminSelectable ?? true,
    normalMembersConfigureProviders: normalMembersConfigureProviders == true,
  );
}

extension ProviderChoiceModelOpenApiMapper
    on openapi.ProviderChoiceModelResponse {
  ProviderChoiceModelSnapshot toSnapshot() => ProviderChoiceModelSnapshot(
    choiceModel: _string(choiceModel, fallback: 'unknown'),
    adapters: _safeStringList(adapters),
    adminRiskNotes: _safeStringList(adminRiskNotes),
    recommended: recommended == true,
  );
}

extension ProviderStatusOpenApiMapper on openapi.ProviderStatusResponse {
  ProviderStatusSnapshot toSnapshot() => ProviderStatusSnapshot(
    module: _string(module, fallback: 'unknown'),
    providerKey: _safeProviderKey(providerKey),
    state: _providerState(_string(state, fallback: 'unknown')),
    readiness: _string(readiness, fallback: 'unknown'),
    enabled: enabled == true,
    configured: configured == true,
    readOnly: readOnly == true,
    failClosed: failClosed == true,
    supportSafe: supportSafe == true,
    paidFeaturesRequired: paidFeaturesRequired == true,
    summary: _safeText(summary),
    supportedCapabilities: _safeStringList(supportedCapabilities),
    unsupportedOperations: _safeStringList(unsupportedOperations),
    supportSafeErrorCodes: _safeStringList(supportSafeErrorCodes),
    redactionPolicy: _safeText(redactionPolicy),
    candidates: _safeStringList(candidates),
    providerRealityLevel: _providerRealityLevel(
      _string(providerRealityLevel, fallback: 'unknown'),
    ),
    diagnostics: _safeDiagnostics(diagnostics),
  );
}

extension DevopsSummaryOpenApiMapper on openapi.DevopsSummaryResponse {
  DevopsProviderSummarySnapshot toSnapshot() => DevopsProviderSummarySnapshot(
    workspaceId: _string(workspaceId, fallback: 'unknown'),
    channelId: _string(channelId, fallback: 'unknown'),
    releaseStatus: _string(releaseStatus, fallback: 'unknown'),
    readOnly: readOnly == true,
    paidFeaturesRequired: paidFeaturesRequired == true,
    supportSafe: supportSafe == true,
    providerReadiness: providerReadiness
        .orEmpty()
        .map((provider) => provider.toSnapshot())
        .toList(growable: false),
    linkedProjects: linkedProjects
        .orEmpty()
        .map((project) => project.toSnapshot())
        .toList(growable: false),
    repositories: repositories
        .orEmpty()
        .map((repository) => repository.toSnapshot())
        .toList(growable: false),
    openIssues: openIssues
        .orEmpty()
        .map((issue) => issue.toSnapshot())
        .toList(growable: false),
    mergeRequests: mergeRequests
        .orEmpty()
        .map((mergeRequest) => mergeRequest.toSnapshot())
        .toList(growable: false),
    pipelines: pipelines
        .orEmpty()
        .map((pipeline) => pipeline.toSnapshot())
        .toList(growable: false),
    releases: releases
        .orEmpty()
        .map((release) => release.toSnapshot())
        .toList(growable: false),
  );
}

extension LinkedSourceProjectOpenApiMapper
    on openapi.LinkedSourceProjectResponse {
  LinkedSourceProjectSnapshot toSnapshot() => LinkedSourceProjectSnapshot(
    id: _string(id, fallback: 'unknown'),
    displayName: _safeText(displayName),
    providerKey: _safeProviderKey(providerKey),
    visibility: _string(visibility, fallback: 'unknown'),
    repositoryIds: _safeStringList(repositoryIds),
  );
}

extension SourceRepositoryOpenApiMapper on openapi.SourceRepositoryResponse {
  SourceRepositorySnapshot toSnapshot() => SourceRepositorySnapshot(
    id: _string(id, fallback: 'unknown'),
    projectId: _string(projectId, fallback: 'unknown'),
    displayName: _safeText(displayName),
    defaultBranch: _string(defaultBranch, fallback: 'unknown'),
    providerKey: _safeProviderKey(providerKey),
    archived: archived == true,
  );
}

extension DevopsIssueSummaryOpenApiMapper
    on openapi.DevopsIssueSummaryResponse {
  DevopsIssueSummarySnapshot toSnapshot() => DevopsIssueSummarySnapshot(
    id: _string(id, fallback: 'unknown'),
    projectId: _string(projectId, fallback: 'unknown'),
    title: _safeText(title),
    state: _string(state, fallback: 'unknown'),
    providerKey: _safeProviderKey(providerKey),
    updatedAt: _dateTime(updatedAt),
    labels: _safeStringList(labels),
  );
}

extension DevopsMergeRequestSummaryOpenApiMapper
    on openapi.DevopsMergeRequestSummaryResponse {
  DevopsMergeRequestSummarySnapshot toSnapshot() =>
      DevopsMergeRequestSummarySnapshot(
        id: _string(id, fallback: 'unknown'),
        repositoryId: _string(repositoryId, fallback: 'unknown'),
        title: _safeText(title),
        sourceBranch: _string(sourceBranch, fallback: 'unknown'),
        targetBranch: _string(targetBranch, fallback: 'unknown'),
        state: _string(state, fallback: 'unknown'),
        providerKey: _safeProviderKey(providerKey),
        updatedAt: _dateTime(updatedAt),
      );
}

extension DevopsPipelineSummaryOpenApiMapper
    on openapi.DevopsPipelineSummaryResponse {
  DevopsPipelineSummarySnapshot toSnapshot() => DevopsPipelineSummarySnapshot(
    id: _string(id, fallback: 'unknown'),
    repositoryId: _string(repositoryId, fallback: 'unknown'),
    ref: _string(ref, fallback: 'unknown'),
    state: _string(state, fallback: 'unknown'),
    providerKey: _safeProviderKey(providerKey),
    updatedAt: _dateTime(updatedAt),
    jobs: jobs.orEmpty().map((job) => job.toSnapshot()).toList(growable: false),
  );
}

extension DevopsJobSummaryOpenApiMapper on openapi.DevopsJobSummaryResponse {
  DevopsJobSummarySnapshot toSnapshot() => DevopsJobSummarySnapshot(
    id: _string(id, fallback: 'unknown'),
    name: _safeText(name),
    state: _string(state, fallback: 'unknown'),
    startedAt: _dateTime(startedAt),
    finishedAt: _dateTime(finishedAt),
  );
}

extension DevopsReleaseSummaryOpenApiMapper
    on openapi.DevopsReleaseSummaryResponse {
  DevopsReleaseSummarySnapshot toSnapshot() => DevopsReleaseSummarySnapshot(
    id: _string(id, fallback: 'unknown'),
    repositoryId: _string(repositoryId, fallback: 'unknown'),
    name: _safeText(name),
    tagName: _string(tagName, fallback: 'unknown'),
    providerKey: _safeProviderKey(providerKey),
    releasedAt: _dateTime(releasedAt),
  );
}

extension OfficeCapabilitiesOpenApiMapper
    on openapi.OfficeCapabilitiesResponse {
  OfficeCapabilitiesSnapshot toSnapshot() => OfficeCapabilitiesSnapshot(
    releaseStatus: _string(releaseStatus, fallback: 'unknown'),
    enabled: enabled == true,
    configured: configured == true,
    supportSafe: supportSafe == true,
    launchMode: _string(launchMode, fallback: 'disabled'),
    defaultProvider: _safeProviderKey(defaultProvider),
    providerReadiness: providerReadiness
        .orEmpty()
        .map((provider) => provider.toSnapshot())
        .toList(growable: false),
    supportedFileTypes: _safeStringList(supportedFileTypes),
    candidates: candidates
        .orEmpty()
        .map((candidate) => candidate.toSnapshot())
        .toList(growable: false),
    capabilities:
        capabilities?.toSnapshot() ??
        const OfficeCapabilityFlagsSnapshot(
          view: false,
          edit: false,
          comment: false,
          review: false,
          formFill: false,
        ),
    permissions:
        permissions?.toSnapshot() ??
        const OfficePermissionModelSnapshot(
          canView: false,
          canEdit: false,
          canComment: false,
          canReview: false,
          canFillForms: false,
          reason: '',
        ),
    lockSessionReadiness:
        lockSessionReadiness?.toSnapshot() ??
        const OfficeLockSessionReadinessSnapshot(
          documentLocks: 'unavailable',
          sessionTokens: 'unavailable',
          callbackVerification: 'unavailable',
          supportSafe: false,
        ),
  );
}

extension OfficeProviderCandidateOpenApiMapper
    on openapi.OfficeProviderCandidateResponse {
  OfficeProviderCandidateSnapshot toSnapshot() =>
      OfficeProviderCandidateSnapshot(
        providerKey: _safeProviderKey(providerKey),
        displayName: _safeText(displayName),
        defaultCandidate: defaultCandidate == true,
        runtimeFit: _safeText(runtimeFit),
        licensingPosture: _safeText(licensingPosture),
        integrationPath: _safeText(integrationPath),
        notes: _safeStringList(notes),
      );
}

extension OfficeCapabilityFlagsOpenApiMapper
    on openapi.OfficeCapabilityFlagsResponse {
  OfficeCapabilityFlagsSnapshot toSnapshot() => OfficeCapabilityFlagsSnapshot(
    view: view == true,
    edit: edit == true,
    comment: comment == true,
    review: review == true,
    formFill: formFill == true,
  );
}

extension OfficePermissionModelOpenApiMapper
    on openapi.OfficePermissionModelResponse {
  OfficePermissionModelSnapshot toSnapshot() => OfficePermissionModelSnapshot(
    canView: canView == true,
    canEdit: canEdit == true,
    canComment: canComment == true,
    canReview: canReview == true,
    canFillForms: canFillForms == true,
    reason: _safeText(reason),
  );
}

extension OfficeLockSessionReadinessOpenApiMapper
    on openapi.OfficeLockSessionReadinessResponse {
  OfficeLockSessionReadinessSnapshot toSnapshot() =>
      OfficeLockSessionReadinessSnapshot(
        documentLocks: _string(documentLocks, fallback: 'unavailable'),
        sessionTokens: _string(sessionTokens, fallback: 'unavailable'),
        callbackVerification: _string(
          callbackVerification,
          fallback: 'unavailable',
        ),
        supportSafe: supportSafe == true,
      );
}

extension OfficeLaunchOpenApiMapper on openapi.OfficeLaunchResponse {
  OfficeLaunchSnapshot toSnapshot() => OfficeLaunchSnapshot.launched(
    sessionId: _string(sessionId, fallback: ''),
    launchMode: _string(launchMode, fallback: 'disabled'),
    providerKey: _safeProviderKey(providerKey),
    grantedPermissions: _safeStringList(grantedPermissions),
    expiresAt: _dateTime(expiresAt),
  );
}

OfficeLaunchSnapshot officeLaunchFailClosedSnapshot(
  Map<String, dynamic> errorEnvelope,
) {
  final fallbackRequestId = _nullableString(errorEnvelope['requestId']) ?? '';
  final error = openapi.ApiErrorResponse.fromJson({
    'code': _string(
      errorEnvelope['code'],
      fallback: 'office-launch-fail-closed',
    ),
    'details': errorEnvelope['details'] is Map<String, dynamic>
        ? errorEnvelope['details']
        : <String, Object?>{},
    'memberImpact': errorEnvelope['memberImpact'],
    'message': _string(
      errorEnvelope['message'],
      fallback: 'office-launch-fail-closed',
    ),
    'requestId': fallbackRequestId,
    'supportRef': _string(
      errorEnvelope['supportRef'],
      fallback: _supportRef(fallbackRequestId),
    ),
  });
  return OfficeLaunchSnapshot.failClosed(
    errorCode: _string(error.code, fallback: 'office-launch-fail-closed'),
    message: _safeText(error.message),
    requestId: _nullableString(error.requestId),
  );
}

String _supportRef(String requestId) {
  final value = requestId.isEmpty ? 'office-launch-fail-closed' : requestId;
  return ['support', value].join(':');
}

ProviderCategoryReadiness _providerCategoryReadiness(String value) {
  return switch (value) {
    'ready' => ProviderCategoryReadiness.ready,
    'disabled' => ProviderCategoryReadiness.disabled,
    'degraded' => ProviderCategoryReadiness.degraded,
    'policy_blocked' => ProviderCategoryReadiness.policyBlocked,
    'misconfigured' => ProviderCategoryReadiness.misconfigured,
    _ => ProviderCategoryReadiness.unknown,
  };
}

ProviderRealityLevel _providerRealityLevel(String value) {
  switch (value) {
    case 'contract_only':
      return ProviderRealityLevel.contractOnly;
    case 'configured':
      return ProviderRealityLevel.configured;
    case 'live_read':
      return ProviderRealityLevel.liveRead;
    case 'live_write':
      return ProviderRealityLevel.liveWrite;
    case 'migration_dry_run':
      return ProviderRealityLevel.migrationDryRun;
    case 'migration_apply_ready':
      return ProviderRealityLevel.migrationApplyReady;
    case 'rollback_ready':
      return ProviderRealityLevel.rollbackReady;
    case 'release_ready':
      return ProviderRealityLevel.releaseReady;
    default:
      return ProviderRealityLevel.unknown;
  }
}

String _memberCapabilityState(Object? value) {
  final state = _string(value, fallback: 'unavailable');
  const allowed = <String>{
    'available',
    'disabled_by_policy',
    'not_configured',
    'degraded',
    'unavailable',
    'coming_later',
  };
  return allowed.contains(state) ? state : 'unavailable';
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
      lower.contains(
        'bear'
        'er ',
      ) ||
      lower.contains('http://') ||
      lower.contains('https://');
}

List<String> _safeStringList(List<String>? value) {
  return value
          ?.map((item) => item.trim())
          .where((item) => item.isNotEmpty && !_containsSensitiveText(item))
          .toList(growable: false) ??
      const [];
}

Map<String, Object?> _safeDiagnostics(Map<String, Object?>? value) {
  if (value == null) {
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
    Map<String, Object?> value => _safeDiagnostics(value),
    _ => null,
  };
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

extension _NullableListOpenApiMapper<T> on List<T>? {
  List<T> orEmpty() => this ?? const [];
}
