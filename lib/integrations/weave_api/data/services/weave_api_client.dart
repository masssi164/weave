import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart';

abstract interface class WeaveApiClient {
  Future<WorkspaceCapabilitySnapshot> fetchWorkspaceCapabilities({
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
    final requestUri = baseUrl.resolve('/api/v1/workspace/capabilities');

    late http.Response response;
    try {
      response = await _httpClient.get(
        requestUri,
        headers: {
          'accept': 'application/json',
          'authorization': 'Bearer $accessToken',
        },
      );
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
      throw AppFailure.unknown(
        'The Weave backend failed to return workspace capabilities.',
        cause: response.statusCode,
      );
    }

    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid workspace capabilities payload.',
        );
      }

      return WorkspaceCapabilitiesResponseDto.fromJson(payload).toSnapshot();
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode workspace capabilities from the Weave backend.',
        cause: error,
      );
    }
  }
}
