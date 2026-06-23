import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/features/onboarding/data/dtos/first_run_status_dto.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_load_failure.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/data/services/weave_api_uri_builder.dart';

class BackendOnboardingStatusClient {
  BackendOnboardingStatusClient({required http.Client httpClient})
    : _httpClient = httpClient;

  final http.Client _httpClient;

  Future<FirstRunStatus> fetchStatus({
    required Uri baseUrl,
    required String accessToken,
  }) async {
    late http.Response response;
    try {
      response = await _httpClient
          .get(
            weaveApiUri(baseUrl, const ['api', 'onboarding', 'status']),
            headers: {
              'Accept': 'application/json',
              'Authorization': 'Bearer $accessToken',
            },
          )
          .timeout(const Duration(seconds: 8));
    } catch (error) {
      throw FirstRunBackendUnavailableFailure(error);
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const FirstRunUnauthorizedFailure();
    }

    if (response.statusCode != 200) {
      throw FirstRunBackendUnavailableFailure(response.statusCode);
    }

    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const FirstRunInvalidPayloadFailure(
          'Expected a JSON object for onboarding status.',
        );
      }
      return openapi.OnboardingStatusResponse.fromJson(decoded).toDomain();
    } on FirstRunInvalidPayloadFailure {
      rethrow;
    } catch (error) {
      throw FirstRunInvalidPayloadFailure(error);
    }
  }
}
