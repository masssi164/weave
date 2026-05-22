import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/provider_stack_status.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/data/dtos/platform_status_response_dto.dart';
import 'package:weave/integrations/weave_api/data/dtos/provider_stack_status_response_dto.dart';
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

  Future<ProviderStackStatus> fetchProviderStackStatus({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<DevopsChannelSummary> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  });

  Future<OfficeCapabilities> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  });

  Future<void> launchOfficeSession({
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
  Future<ProviderStackStatus> fetchProviderStackStatus({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _providerStackStatusUri(baseUrl),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return provider readiness.',
      invalidPayloadMessage:
          'The backend returned an invalid provider readiness response.',
      decodeFailureMessage:
          'Unable to decode provider readiness from the Weave backend.',
    );

    return ProviderStackStatusResponseDto.fromJson(payload).toEntity();
  }

  @override
  Future<DevopsChannelSummary> fetchDevopsSummary({
    required Uri baseUrl,
    required String accessToken,
    required String workspaceId,
    required String channelId,
  }) async {
    final payload = await _getJson(
      requestUri: _devopsSummaryUri(baseUrl, workspaceId, channelId),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return DevOps readiness.',
      invalidPayloadMessage:
          'The backend returned an invalid DevOps readiness response.',
      decodeFailureMessage:
          'Unable to decode DevOps readiness from the Weave backend.',
    );

    return DevopsSummaryResponseDto.fromJson(payload).toEntity();
  }

  @override
  Future<OfficeCapabilities> fetchOfficeCapabilities({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    final payload = await _getJson(
      requestUri: _officeCapabilitiesUri(baseUrl),
      accessToken: accessToken,
      failureMessage: 'The Weave backend failed to return Office readiness.',
      invalidPayloadMessage:
          'The backend returned an invalid Office readiness response.',
      decodeFailureMessage:
          'Unable to decode Office readiness from the Weave backend.',
    );

    return OfficeCapabilitiesResponseDto.fromJson(payload).toEntity();
  }

  @override
  Future<void> launchOfficeSession({
    required Uri baseUrl,
    required String accessToken,
    required String fileId,
    required String requestedMode,
  }) async {
    await _postJson(
      requestUri: _officeLaunchUri(baseUrl),
      accessToken: accessToken,
      body: {'fileId': fileId, 'requestedMode': requestedMode},
      failureMessage:
          'The Weave backend refused to launch an Office session fail-closed.',
    );
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

    if (response.statusCode != 200 && response.statusCode != 201) {
      throw AppFailure.unknown(failureMessage, cause: response.statusCode);
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid Office launch response.',
        );
      }
      return payload;
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode Office launch response from the Weave backend.',
        cause: error,
      );
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

  Uri _providerStackStatusUri(Uri baseUrl) {
    return baseUrl.replace(
      pathSegments: _apiPath(baseUrl, const ['api', 'providers', 'status']),
    );
  }

  Uri _devopsSummaryUri(Uri baseUrl, String workspaceId, String channelId) {
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
