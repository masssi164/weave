import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/data/dtos/organization_manifest_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/platform_status_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/provider_stack_openapi_mappers.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_home_response_dto.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_uri_builder.dart';

enum IdentitySessionReconcileResult { unchanged, accessUpdated }

abstract interface class WeaveApiClient {
  Future<IdentitySessionReconcileResult> reconcileIdentitySession({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<OrganizationManifestSnapshot> fetchOrganizationManifest({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<WorkspaceHomeSnapshot> fetchWorkspaceHome({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<MatrixE2eeDiagnostic> fetchMatrixE2eeDiagnostic({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<ProviderStackSnapshot> fetchProviderStackStatus({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<DevopsProviderSummarySnapshot> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  });

  Future<OfficeCapabilitiesSnapshot> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<OfficeLaunchSnapshot> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String fileId,
    required String requestedMode,
  });
}

class HttpWeaveApiClient implements WeaveApiClient {
  HttpWeaveApiClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  @override
  Future<IdentitySessionReconcileResult> reconcileIdentitySession({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _postJson(
      requestUri: _identitySessionReconcileUri(baseUrl),
      accessToken: accessToken,
      failureMessage:
          'The Weave backend failed to reconcile organization access.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid identity-session reconciliation payload.',
      decodeFailureMessage:
          'Unable to decode identity-session reconciliation from the Weave backend.',
    );
    final response = openapi.IdentitySessionReconcileResponse.fromJson(
      payload.json,
    );
    final result = switch (response.state) {
      'unchanged' => IdentitySessionReconcileResult.unchanged,
      'access_updated' => IdentitySessionReconcileResult.accessUpdated,
      _ => throw const AppFailure.unknown(
        'The Weave backend returned an unknown identity-session reconciliation state.',
      ),
    };
    if (response.sessionRefreshRequired !=
        (result == IdentitySessionReconcileResult.accessUpdated)) {
      throw const AppFailure.unknown(
        'The Weave backend returned an inconsistent identity-session reconciliation state.',
      );
    }
    return result;
  }

  @override
  Future<OrganizationManifestSnapshot> fetchOrganizationManifest({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _organizationManifestUri(baseUrl),
      accessToken: accessToken,
      failureMessage:
          'The Weave backend failed to return the organization manifest.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid organization manifest payload.',
      decodeFailureMessage:
          'Unable to decode the organization manifest from the Weave backend.',
    );

    return openapi.OrganizationManifestResponse.fromJson(payload).toSnapshot();
  }

  @override
  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _workspaceCapabilitiesUri(baseUrl),
      accessToken: accessToken,
      failureMessage:
          'The Weave backend failed to return workspace capabilities.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid workspace capabilities payload.',
      decodeFailureMessage:
          'Unable to decode workspace capabilities from the Weave backend.',
    );

    return openapi.WorkspaceCapabilitiesResponse.fromJson(payload).toSnapshot();
  }

  @override
  Future<WorkspaceHomeSnapshot> fetchWorkspaceHome({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _workspaceHomeUri(baseUrl),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return Weave Home.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid Weave Home payload.',
      decodeFailureMessage: 'Unable to decode Weave Home from the backend.',
    );

    return openapi.WorkspaceHomeResponse.fromJson(payload).toSnapshot();
  }

  @override
  Future<MatrixE2eeDiagnostic> fetchMatrixE2eeDiagnostic({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _platformStatusUri(baseUrl),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return platform status.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid platform status payload.',
      decodeFailureMessage:
          'Unable to decode platform status from the Weave backend.',
    );

    return PlatformStatusResponseDto.fromJson(payload).toMatrixDiagnostic();
  }

  @override
  Future<ProviderStackSnapshot> fetchProviderStackStatus({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _providerStatusUri(baseUrl),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return provider status.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid provider status payload.',
      decodeFailureMessage:
          'Unable to decode provider status from the Weave backend.',
    );

    return openapi.ProviderRegistryResponse.fromJson(payload).toSnapshot();
  }

  @override
  Future<DevopsProviderSummarySnapshot> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  }) async {
    final payload = await _getJson(
      requestUri: _devopsSummaryUri(
        baseUrl,
        workspaceId: workspaceId,
        channelId: channelId,
      ),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return DevOps readiness.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid DevOps readiness payload.',
      decodeFailureMessage:
          'Unable to decode DevOps readiness from the Weave backend.',
    );

    return openapi.DevopsSummaryResponse.fromJson(payload).toSnapshot();
  }

  @override
  Future<OfficeCapabilitiesSnapshot> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _officeCapabilitiesUri(baseUrl),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return Office capabilities.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid Office capabilities payload.',
      decodeFailureMessage:
          'Unable to decode Office capabilities from the Weave backend.',
    );

    return openapi.OfficeCapabilitiesResponse.fromJson(payload).toSnapshot();
  }

  @override
  Future<OfficeLaunchSnapshot> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String fileId,
    required String requestedMode,
  }) async {
    final payload = await _postJson(
      requestUri: _officeLaunchUri(baseUrl),
      accessToken: accessToken,
      body: {'fileId': fileId, 'requestedMode': requestedMode},
      failureMessage: 'The Weave backend refused the Office launch request.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid Office launch payload.',
      decodeFailureMessage:
          'Unable to decode Office launch from the Weave backend.',
      failClosedStatusCodes: const {503},
    );

    if (payload.failClosed) {
      return officeLaunchFailClosedSnapshot(payload.json);
    }

    return openapi.OfficeLaunchResponse.fromJson(payload.json).toSnapshot();
  }

  Future<Map<String, dynamic>> _getJson({
    required Uri requestUri,
    required String accessToken,
    required String failureMessage,
    required String invalidPayloadMessage,
    required String decodeFailureMessage,
  }) async {
    late http.Response response;
    try {
      response = await _httpClient
          .get(
            requestUri,
            headers: {
              'Accept': 'application/json',
              'Authorization': 'Bearer $accessToken',
            },
          )
          .timeout(const Duration(seconds: 5));
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave backend right now.',
        cause: error,
      );
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const AppFailure.unknown(
        'The Weave backend rejected the current session.',
      );
    }

    if (response.statusCode != 200) {
      throw AppFailure.unknown(failureMessage, cause: response.statusCode);
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        throw AppFailure.unknown(invalidPayloadMessage);
      }

      return payload;
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(decodeFailureMessage, cause: error);
    }
  }

  Future<_HttpJsonPayload> _postJson({
    required Uri requestUri,
    required String accessToken,
    Map<String, Object?>? body,
    required String failureMessage,
    required String invalidPayloadMessage,
    required String decodeFailureMessage,
    Set<int> failClosedStatusCodes = const {},
  }) async {
    late http.Response response;
    try {
      final headers = <String, String>{
        'Accept': 'application/json',
        'Authorization': 'Bearer $accessToken',
      };
      if (body != null) {
        headers['Content-Type'] = 'application/json';
      }
      response = await _httpClient
          .post(
            requestUri,
            headers: headers,
            body: body == null ? null : jsonEncode(body),
          )
          .timeout(const Duration(seconds: 5));
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to reach the Weave backend right now.',
        cause: error,
      );
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const AppFailure.unknown(
        'The Weave backend rejected the current session.',
      );
    }

    final failClosed = failClosedStatusCodes.contains(response.statusCode);
    if (response.statusCode != 200 && !failClosed) {
      throw AppFailure.unknown(failureMessage, cause: response.statusCode);
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        if (failClosed) {
          return const _HttpJsonPayload(json: {}, failClosed: true);
        }
        throw AppFailure.unknown(invalidPayloadMessage);
      }

      return _HttpJsonPayload(json: payload, failClosed: failClosed);
    } on AppFailure {
      rethrow;
    } catch (error) {
      if (failClosed) {
        return const _HttpJsonPayload(json: {}, failClosed: true);
      }
      throw AppFailure.unknown(decodeFailureMessage, cause: error);
    }
  }

  Uri _organizationManifestUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['organization', 'manifest']);
  }

  Uri _identitySessionReconcileUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const [
      'v1',
      'identity',
      'session',
      'reconcile',
    ]);
  }

  Uri _workspaceCapabilitiesUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['workspace', 'capabilities']);
  }

  Uri _workspaceHomeUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['workspace', 'home']);
  }

  Uri _platformStatusUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['platform', 'status']);
  }

  Uri _providerStatusUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['providers', 'status']);
  }

  Uri _devopsSummaryUri(
    Uri baseUrl, {
    required String workspaceId,
    required String channelId,
  }) {
    return weaveApiUri(baseUrl, [
      'workspaces',
      workspaceId,
      'channels',
      channelId,
      'devops',
      'summary',
    ]);
  }

  Uri _officeCapabilitiesUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['office', 'capabilities']);
  }

  Uri _officeLaunchUri(Uri baseUrl) {
    return weaveApiUri(baseUrl, const ['office', 'launch']);
  }
}

class _HttpJsonPayload {
  const _HttpJsonPayload({required this.json, required this.failClosed});

  final Map<String, dynamic> json;
  final bool failClosed;
}
