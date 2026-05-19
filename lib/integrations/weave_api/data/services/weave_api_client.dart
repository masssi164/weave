import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/data/dtos/platform_status_response_dto.dart';
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
