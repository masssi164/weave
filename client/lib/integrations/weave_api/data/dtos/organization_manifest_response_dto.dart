import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/generated/user_api/api.dart' as openapi;
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';

extension OrganizationManifestResponseMapper
    on openapi.OrganizationManifestResponse {
  OrganizationManifestSnapshot toSnapshot() {
    final authUrl = Uri.tryParse(_string(organizationAuthUrl, fallback: ''));
    if (authUrl == null ||
        !authUrl.isAbsolute ||
        authUrl.host.isEmpty ||
        authUrl.userInfo.isNotEmpty ||
        authUrl.hasQuery ||
        authUrl.hasFragment ||
        (authUrl.scheme != 'https' && authUrl.scheme != 'http')) {
      throw const AppFailure.unknown(
        'The backend returned an invalid organization auth URL.',
      );
    }

    final safe = supportSafe == true;
    final exposesProviders = providerConfigurationExposed == true;
    final exposesDiagnostics = diagnosticsExposed == true;
    if (!safe || exposesProviders || exposesDiagnostics) {
      throw const AppFailure.unknown(
        'The backend returned an unsafe organization manifest payload.',
      );
    }

    return OrganizationManifestSnapshot(
      manifestVersion: _string(manifestVersion, fallback: 'unknown'),
      organizationId: _requiredText(organizationId, 'organizationId'),
      displayName: _requiredText(displayName, 'displayName'),
      organizationAuthUrl: authUrl,
      generatedAt: generatedAt,
      supportSafe: safe,
      providerConfigurationExposed: exposesProviders,
      diagnosticsExposed: exposesDiagnostics,
      whitelistingOwner: _string(whitelistingOwner, fallback: 'unknown'),
      clientResponsibilities: _safeStringList(clientResponsibilities),
      adminConsoleResponsibilities: _safeStringList(
        adminConsoleResponsibilities,
      ),
      memberCapabilityStates: _memberCapabilityStates(memberCapabilityStates),
      capabilities: _requiredCapabilities(capabilities).toSnapshot(),
    );
  }
}

openapi.WorkspaceCapabilitiesResponse _requiredCapabilities(
  openapi.WorkspaceCapabilitiesResponse? value,
) {
  if (value != null) return value;
  throw const AppFailure.unknown(
    'The backend returned an invalid organization manifest payload.',
  );
}

Map<String, MemberCapabilityState> _memberCapabilityStates(
  Map<String, openapi.OrganizationManifestResponseMemberCapabilityStatesEnum>
  value,
) {
  return value.map(
    (key, value) => MapEntry(key, _memberCapabilityState(value.value)),
  );
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

List<String> _safeStringList(List<String> value) =>
    value.map(_cleanText).toList(growable: false);

String _safeText(String? value) => value is String ? _cleanText(value) : '';

String _requiredText(String? value, String field) {
  final cleaned = _safeText(value);
  if (cleaned.isEmpty) {
    throw AppFailure.unknown(
      'The backend returned an invalid organization manifest payload.',
      cause: '$field is required',
    );
  }
  return cleaned;
}

String _string(String? value, {required String fallback}) {
  return value != null && value.trim().isNotEmpty ? value.trim() : fallback;
}

String _cleanText(String value) => value.replaceAll(RegExp(r'\s+'), ' ').trim();
