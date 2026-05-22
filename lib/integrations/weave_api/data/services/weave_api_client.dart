import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/data/dtos/platform_status_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/provider_stack_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';

abstract interface class WeaveApiClient {
  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
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
    required String documentId,
  });
}

class HttpWeaveApiClient implements WeaveApiClient {
  HttpWeaveApiClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

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

    return WorkspaceCapabilitiesResponseDto.fromJson(payload).toSnapshot();
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

    return ProviderRegistryResponseDto.fromJson(payload).toSnapshot();
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

    return DevopsSummaryResponseDto.fromJson(payload).toSnapshot();
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

    return OfficeCapabilitiesResponseDto.fromJson(payload).toSnapshot();
  }

  @override
  Future<OfficeLaunchSnapshot> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String documentId,
  }) async {
    final payload = await _postJson(
      requestUri: _officeLaunchUri(baseUrl),
      accessToken: accessToken,
      body: {'documentId': documentId},
      failureMessage: 'The Weave backend refused the Office launch request.',
      invalidPayloadMessage:
          'The Weave backend returned an invalid Office launch payload.',
      decodeFailureMessage:
          'Unable to decode Office launch from the Weave backend.',
    );

    return OfficeLaunchResponseDto.fromJson(payload).toSnapshot();
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

  Future<Map<String, dynamic>> _postJson({
    required Uri requestUri,
    required String accessToken,
    required Map<String, Object?> body,
    required String failureMessage,
    required String invalidPayloadMessage,
    required String decodeFailureMessage,
  }) async {
    late http.Response response;
    try {
      response = await _httpClient
          .post(
            requestUri,
            headers: {
              'Accept': 'application/json',
              'Authorization': 'Bearer $accessToken',
              'Content-Type': 'application/json',
            },
            body: jsonEncode(body),
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
    required Map<String, Object?> body,
    required String failureMessage,
    required String invalidPayloadMessage,
    required String decodeFailureMessage,
    Set<int> failClosedStatusCodes = const {},
  }) async {
    late http.Response response;
    try {
      response = await _httpClient
          .post(
            requestUri,
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json',
              'Authorization': 'Bearer $accessToken',
            },
            body: jsonEncode(body),
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
        throw AppFailure.unknown(invalidPayloadMessage);
      }

      return _HttpJsonPayload(json: payload, failClosed: failClosed);
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(decodeFailureMessage, cause: error);
    }
  }

  Uri _workspaceCapabilitiesUri(Uri baseUrl) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, const [
        'api',
        'v1',
        'workspace',
        'capabilities',
      ]),
    );
  }

  Uri _platformStatusUri(Uri baseUrl) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, const ['api', 'platform', 'status']),
    );
  }

  Uri _providerStatusUri(Uri baseUrl) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, const ['api', 'providers', 'status']),
    );
  }

  Uri _devopsSummaryUri(
    Uri baseUrl, {
    required String workspaceId,
    required String channelId,
  }) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, [
        'api',
        'workspaces',
        workspaceId,
        'channels',
        channelId,
        'devops',
        'summary',
      ]),
    );
  }

  Uri _officeCapabilitiesUri(Uri baseUrl) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, const ['api', 'office', 'capabilities']),
    );
  }

  Uri _officeLaunchUri(Uri baseUrl) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, const ['api', 'office', 'launch']),
    );
  }

  List<String> _apiPath(Uri baseUrl, List<String> pathSegments) {
    final baseSegments = baseUrl.pathSegments
        .where((segment) => segment.isNotEmpty)
        .toList(growable: false);
    if (baseSegments.isNotEmpty &&
        pathSegments.isNotEmpty &&
        baseSegments.last == 'api' &&
        pathSegments.first == 'api') {
      return [...baseSegments, ...pathSegments.skip(1)];
    }

    return [...baseSegments, ...pathSegments];
  }
}

class _HttpJsonPayload {
  const _HttpJsonPayload({required this.json, required this.failClosed});

  final Map<String, dynamic> json;
  final bool failClosed;
}
