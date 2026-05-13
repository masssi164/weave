import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/data/dtos/first_run_status_dto.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
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
      throw AppFailure.unknown(
        'Unable to reach the Weave onboarding backend right now.',
        cause: error,
      );
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const AppFailure.unknown(
        'The Weave backend rejected the current onboarding session.',
      );
    }

    if (response.statusCode != 200) {
      throw AppFailure.unknown(
        'The Weave backend could not load onboarding status.',
        cause: response.statusCode,
      );
    }

    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const AppFailure.unknown(
          'The Weave backend returned an invalid onboarding status payload.',
        );
      }
      return FirstRunStatusDto.fromJson(decoded).toDomain();
    } on AppFailure {
      rethrow;
    } catch (error) {
      throw AppFailure.unknown(
        'Unable to decode onboarding status from the Weave backend.',
        cause: error,
      );
    }
  }
}
